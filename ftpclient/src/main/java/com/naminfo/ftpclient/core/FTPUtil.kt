package com.naminfo.ftpclient.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
private const val TAG = "==>>FTPUtil"
class FTPUtil {
    private val ftpClient = FTPClient()

    // Connect to the FTP server in a background thread
    suspend fun connect(server: String, port: Int, username: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                ftpClient.connect(server, port)
                val login = ftpClient.login(username, password)
                if (login) {
                    // ftpClient.enterLocalPassiveMode() // Set the passive mode
                    Log.d("===>>>FTPUtil", "Login response: " + ftpClient.replyString)
                    return@withContext true
                }
            } catch (e: Exception) {

                Log.d(TAG, "connect: ${e.message}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }

    // Upload file to the FTP server in a background thread
    suspend fun uploadFile(localFilePath: String, remoteFilePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.e(
                    TAG,
                    "Local file does not exist at path: $localFilePath remoteFilePath =$remoteFilePath"
                )
                val localFile = File(localFilePath)
                if (!localFile.exists()) {

                    return@withContext false
                }

                val fileSize = localFile.length()
                Log.d(TAG, "File size: $fileSize bytes")

                val inputStream = FileInputStream(localFile)

                // ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                // ftpClient.setFileType(FTP.LOCAL_FILE_TYPE)

                //      val result = ftpClient.storeUniqueFile(remoteFilePath, inputStream)
                val result = ftpClient.storeFile(remoteFilePath, inputStream)
                val replyCode = ftpClient.replyCode
                Log.d(TAG, "Upload result: $result")
                Log.d(TAG, "FTP Reply Code: $replyCode")
                Log.d(TAG, "FTP Response: " + ftpClient.replyString)

                inputStream.close()
                return@withContext result
            } catch (e: Exception) {
                Log.e(TAG, "Error uploading file: ${e.message}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }


    // Download file from the FTP server to a specific folder
    suspend fun downloadFile(remoteFilePath: String, localDirectoryPath: String, localFileName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val remoteFilePathD = remoteFilePath
                // Ensure the local directory exists
                val localDirectory = File(localDirectoryPath)
                if (!localDirectory.exists()) {
                    localDirectory.mkdirs()
                }

                // Create the local file path
                val localFile = File(localDirectory, localFileName)
                Log.d(
                    TAG,
                    "downloadFile: localFile is exist =${localFile.exists()} localFileName =$localFileName"
                )
                Log.d(
                    TAG,
                    "downloadFile: remoteFilePath =$remoteFilePathD localDirectoryPath =$localDirectoryPath"
                )
                if (localFile.exists()) {

                    localFile.delete() // If file already exists, delete it
                }

                // Create an OutputStream to write the downloaded file
                val outputStream = FileOutputStream(localFile)
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                // Retrieve the file from FTP server
                val result = ftpClient.retrieveFile(remoteFilePathD, outputStream)
                val replyCode = ftpClient.replyCode
                Log.d(TAG, "Download result: $result")
                Log.d(TAG, "FTP Reply Code: $replyCode")
                Log.d(TAG, "FTP Response: " + ftpClient.replyString)
                outputStream.flush()
                outputStream.close()

                return@withContext result
            } catch (e: Exception) {
                // Log.e(TAG, "Error downloading file: ${e.printStackTrace()}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }

    // FTPUtil class
    suspend fun listFiles(remoteDirectory: String): List<String> {
        return withContext(Dispatchers.IO) {
            val fileList = mutableListOf<String>()
            try {
                ftpClient.changeWorkingDirectory(remoteDirectory)
                val files = ftpClient.listFiles()

                for (file in files) {
                    if (file.isFile) {
                        fileList.add(file.name)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error listing files: ${e.message}")
                e.printStackTrace()
            }
            return@withContext fileList
        }
    }
    fun makeFtpUrl(
        username: String,
        password: String,
        server: String,
        port: Int = 21, // Default FTP port
        filePath: String = "" // Path to file or directory
    ): String {
        return if (username.isNotEmpty() && password.isNotEmpty()) {
            "ftp://$username:$password@$server:$port/$filePath".trimEnd('/')
        } else {
            "ftp://$server:$port/$filePath".trimEnd('/')
        }
    }

    // Disconnect from the FTP server
    suspend fun disconnect() {
        withContext(Dispatchers.IO) {
            try {
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}