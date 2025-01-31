package com.example.ftpclient


import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ftpclient.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import com.naminfo.ftpclient.core.FTPUtil
import com.naminfo.ftpclient.core.SelectFiles
import com.naminfo.ftpclient.core.model.FileItem
import com.naminfo.ftpclient.permission.PermissionManager
import kotlinx.coroutines.launch


private const val TAG = "==>>MainActivity"
private const val PERMISSION_REQUEST_CODE = 1001

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var permissionManager: PermissionManager? = null
    private val selectFiles: SelectFiles = SelectFiles(this)
    private lateinit var selectFilesLauncher: ActivityResultLauncher<Intent>
    private var selectedFileUri: Uri? = null
    private var selectedFileUris: MutableList<Uri> = mutableListOf()
    private val ftpUtil = FTPUtil()
    private var ftpURL = ""
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


    private fun uiInit() {
        binding.btnSelectFile.setOnClickListener {
            loginFTP()
        }
        loginFTP()
    }

    private fun loginFTP() {
        server = binding.edtServer.text.toString().trim()
        username = binding.edtUsername.text.toString().trim()
        password = binding.edtPassword.text.toString().trim()
        if (server.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
            lifecycleScope.launch {
                if (ftpUtil.connect(
                        server = server,
                        port = 21,
                        username = username,
                        password = password
                    )
                ) {
                    Snackbar.make(binding.root, "Connected", Snackbar.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, BasicChatActivity::class.java).apply {
                        putExtra("server", server)
                        putExtra("username", username)
                        putExtra("password", password)
                    })
                } else {
                    Snackbar.make(binding.root, "Connection failed", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }


}