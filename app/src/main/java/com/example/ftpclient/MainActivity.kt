package com.example.ftpclient


import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ftpclient.databinding.ActivityMainBinding
import com.example.ftpclient.download.FileAdapter
import com.example.ftpclient.download.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "==>>MainActivity"
private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var permissionManager: PermissionManager? = null
    private var selectedFileUri: Uri? = null
    private val ftpUtil = FTPUtil()

    private lateinit var fileAdapter: FileAdapter
    private val fileList: MutableList<FileItem> = mutableListOf()

    var server = ""
    var username = ""
    var password = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        permissionManager = PermissionManager(this@MainActivity)

        if (!permissionManager?.checkPermissions()!!) {
            permissionManager?.requestPermissions(this, PERMISSION_REQUEST_CODE)
        }
        setContentView(binding.root)
        uiInit()
    }



    // Helper function to get the file extension from a URI
    private fun getFileExtension(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }
    }

    // Function to determine the file type and extension based on filePath or file extension
    private fun getFileTypeAndExtension(
        context: Context,
        filePath: String,
        fileUri: Uri? = null
    ): Pair<String, String> {
        val fileTypeMapping = mapOf(
            "image" to listOf("png", "jpg", "jpeg", "gif", "bmp"),
            "pdf" to listOf("pdf"),
            "text" to listOf("txt", "csv", "xml"),
            "audio" to listOf("mp3", "wav", "aac", "m4a","opus","oga"),
            "video" to listOf("mp4", "avi", "mov", "mkv"),
            "document" to listOf("doc", "docx", "xls", "xlsx", "ppt", "pptx")
        )

        // Check for predefined file types based on filePath or URI
        /*for ((key, extensions) in fileTypeMapping) {
            if (filePath.contains(key, ignoreCase = true)) {
                return key to ".${extensions.first()}"
            }
        }*/

        // Fallback: determine type based on the file extension from the MIME type
        val fileExtension = getFileExtension(context, fileUri ?: Uri.EMPTY) ?: ""
        val fileType = fileTypeMapping.entries.firstOrNull {
            it.value.contains(fileExtension)
        }?.key ?: "unknown"
        Log.d(TAG, "getFileTypeAndExtension: $fileExtension")
        return fileType to ".$fileExtension"
    }

    private fun uiInit() {
        binding.btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"

            intent.putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*", // Images
                    "video/*", // Videos
                    "audio/*", // All audio files (mp3, wav, m4a, etc.)
                    "application/pdf", // PDFs
                    "text/plain", // Text files
                    "application/msword", // .doc
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                    "application/vnd.ms-excel", // .xls
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
                    "application/vnd.ms-powerpoint", // .ppt
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
                )
            )
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true) // Allow multiple files
            startActivityForResult(intent, 1)
        }

        binding.btnUpload.setOnClickListener {
             server = binding.edtServer.text.toString().trim()
             username = binding.edtUsername.text.toString().trim()
             password = binding.edtPassword.text.toString().trim()

            if (server.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty() && selectedFileUri != null) {
                val filePath = selectedFileUri?.let { uri -> FileUtils.getPath(this, uri) }

                lifecycleScope.launch(Dispatchers.Main) {
                    if (filePath != null) {
                        val connected = ftpUtil.connect(server, 21, username, password)
                        if (connected) {


                            // Determine file type and extension
                            val (fileType, fileExtension) = getFileTypeAndExtension(this@MainActivity, filePath, selectedFileUri)

                         //   val remoteFilePath = "${fileType}_${filePath?.split(":")?.get(1)}$fileExtension"

                            val remoteFilePath = if (!filePath.isNullOrEmpty() && filePath.contains(":")) {
                                "${fileType}_${filePath.split(":")[1]}$fileExtension"
                            } else {

                                if(filePath.contains("/"))
                                     filePath.substringAfterLast("/").substringBeforeLast(".")
                                else
                                    "${fileType}_unknown$fileExtension"
                            }
                            Log.d(TAG, "Uploading:fileType =$fileType remotePath=$remoteFilePath, localPath=$filePath")

                            val uploaded = ftpUtil.uploadFile(filePath, remoteFilePath)

                            if (uploaded) {
                                Toast.makeText(this@MainActivity, "File uploaded successfully!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@MainActivity, "Upload failed.", Toast.LENGTH_SHORT).show()
                            }
                            ftpUtil.disconnect()
                        } else {
                            Toast.makeText(this@MainActivity, "FTP connection failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(this, "Please fill in all fields and select a file.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownload.setOnClickListener {
         //   ftpUtil.downloadFile()
            loadFilesFromFTP()

        }

        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        // Start by loading the list of files
        loadFilesFromFTP()

        // Set the adapter to RecyclerView
        fileAdapter = FileAdapter(fileList){
            lifecycleScope.launch(Dispatchers.Main) {

               // val remoteFilePath = it.path // The path of the file on the FTP server
                val remoteFilePath = it.name // The path of the file on the FTP server
              //  val localDirectoryPath = this@MainActivity.filesDir.toString() // The directory on your device where you want to save the file
                val localDirectoryPath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                    ?: this@MainActivity.filesDir.absolutePath // Use Downloads directory or app's files directory

                val localFileName = it.name // Name for the local file

                val ftpUtil = FTPUtil()

                val connected = ftpUtil.connect(server, 21, username, password)
                if (connected) {
                    val downloadSuccess = ftpUtil.downloadFile(remoteFilePath, localDirectoryPath, localFileName)
                    if (downloadSuccess) {
                        Toast.makeText(this@MainActivity, "File downloaded successfully!", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "File downloaded successfully!")
                    } else {
                        Toast.makeText(this@MainActivity, "File downloaded failed!", Toast.LENGTH_SHORT).show()

                        Log.e(TAG, "Download failed.")
                    }

                    ftpUtil.disconnect()
                } else {
                    Toast.makeText(this@MainActivity, "FTP connection failed.", Toast.LENGTH_SHORT).show()

                    Log.e(TAG, "FTP connection failed.")
                }
            }
        }
        binding.recyclerView.adapter = fileAdapter
    }

    private fun loadFilesFromFTP() {
        lifecycleScope.launch(Dispatchers.IO) {
            val ftpUtil = FTPUtil()

            val connected = ftpUtil.connect(server, 21, username, password)
            if (connected) {
                // Get the list of files from the FTP server
                val remotePath = "/FTP_SERVER_ROOT" // Specify the directory on the FTP server
                val fileNames = ftpUtil.listFiles(remotePath)

                // Convert file names to FileItem objects
                val files = fileNames.map { FileItem(it, "$remotePath/$it") }

                // Update UI on the main thread
                withContext(Dispatchers.Main) {
                    fileList.clear()
                    fileList.addAll(files)
                    fileAdapter.notifyDataSetChanged()
                }

                ftpUtil.disconnect()
            } else {
                Log.e(TAG, "FTP connection failed.")
            }
        }
    }


    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == 1) {
            selectedFileUri = data?.data
            if (selectedFileUri != null) {
                val fileExtension = getFileExtension(this, selectedFileUri ?: Uri.EMPTY)
                val filePath = FileUtils.getPath(this, selectedFileUri ?: Uri.EMPTY)
                Log.d(
                    "File Info",
                    "URI: $selectedFileUri, ${data?.data?.path} ,Extension: $fileExtension, Path: $filePath"
                )
            }

            binding.btnUpload.isEnabled = true
        }
    }

    fun checkPermissions() {
        val permissionsRequiredList = mutableListOf<String>()

        addPermissionIfNeeded(
            permissionsRequiredList,
            Manifest.permission.RECORD_AUDIO,
            "RECORD_AUDIO"
        )
        addPermissionIfNeeded(permissionsRequiredList, Manifest.permission.CAMERA, "CAMERA")
        addPermissionIfNeeded(
            permissionsRequiredList,
            Manifest.permission.READ_PHONE_STATE,
            "READ_PHONE_STATE"
        )
        addPermissionIfNeeded(
            permissionsRequiredList,
            Manifest.permission.POST_NOTIFICATIONS,
            "POST_NOTIFICATIONS"
        )
        addPermissionIfNeeded(
            permissionsRequiredList,
            Manifest.permission.WRITE_CONTACTS,
            "WRITE_CONTACTS"
        )
        addPermissionIfNeeded(
            permissionsRequiredList,
            Manifest.permission.READ_CONTACTS,
            "READ_CONTACTS"
        )

        if (permissionsRequiredList.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsRequiredList.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun addPermissionIfNeeded(
        permissionsList: MutableList<String>,
        permission: String,
        logTag: String
    ) {

        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "[MainActivity] Asking for $logTag permission")
            permissionsList.add(permission)
        }

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            handlePermissionResults(permissions, grantResults)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun handlePermissionResults(permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { index, permission ->
            when (permission) {
                android.Manifest.permission.RECORD_AUDIO -> handlePermissionResult(
                    grantResults[index],
                    "RECORD_AUDIO"
                )

                android.Manifest.permission.CAMERA -> handleCameraPermissionResult(grantResults[index])
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE -> handlePermissionResult(
                    grantResults[index],
                    "WRITE_EXTERNAL_STORAGE"
                )

                android.Manifest.permission.READ_PHONE_STATE -> handlePhoneStatePermissionResult(
                    grantResults[index]
                )

                android.Manifest.permission.WRITE_CONTACTS -> handleContactPermissionResult(
                    grantResults[index],
                    "WRITE_CONTACTS"
                )

                android.Manifest.permission.READ_CONTACTS -> handleContactPermissionResult(
                    grantResults[index],
                    "READ_CONTACTS"
                )

                android.Manifest.permission.POST_NOTIFICATIONS -> handlePermissionResult(
                    grantResults[index],
                    "POST_NOTIFICATIONS"
                )
            }
        }
    }


    private fun handlePermissionResult(grantResult: Int, permissionName: String) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "[MainActivity] $permissionName permission has been granted")
        }
    }

    private fun handleCameraPermissionResult(grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "[MainActivity] CAMERA permission has been granted")

        }
    }

    private fun handlePhoneStatePermissionResult(grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "[MainActivity] READ_PHONE_STATE permission has been granted")
            initPhoneStateListener()
            checkPermissions()
        }
    }

    private fun handleContactPermissionResult(grantResult: Int, permissionName: String) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "[MainActivity] $permissionName permission has been granted")
            checkPermissions()
        }
    }

    private fun initPhoneStateListener() {
        // Implementation for initializing phone state listener
    }
}