package com.naminfo.ftpclient.core

import FileUtils.getFileTypeAndExt
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.naminfo.ftpclient.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SelectFiles"
class SelectFiles(private val context: Context) {

    fun chooseFiles(onIntentReady: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*", "video/*", "audio/*", "application/pdf",
                    "text/plain", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        onIntentReady(intent)
    }

    suspend fun uploadFiles(
        server: String,
        username: String,
        password: String,
        selectedFileUri: Uri,
        getFilePath: (Uri) -> String?,
        ftpUtil: FTPUtil,
        getURL: (String) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (server.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()) {
                    val filePath = getFilePath(selectedFileUri)
                    if (filePath != null) {
                        if (ftpUtil.connect(server, 21, username, password)) {
                            // Determine file type and extension
                            val (fileType, fileExtension) = getFileTypeAndExt(context, filePath, selectedFileUri)

                         //   val (fileType, fileExtension) = getFileTypeAndExtension(context, filePath, selectedFileUri)
                            val remoteFilePath = if (filePath.contains(":")) {
                                "${fileType}_${filePath.split(":")[1]}$fileExtension"
                            } else {
                                "${fileType}_unknown$fileExtension"
                            }

                            val uploaded = ftpUtil.uploadFile(filePath, remoteFilePath)
                            ftpUtil.disconnect()
                            getURL(ftpUtil.makeFtpUrl(
                                username,
                                password,
                                server,
                                filePath = remoteFilePath
                            ))
                            return@withContext uploaded
                        }
                    }
                }
                false
            } catch (e: Exception) {
                Log.e("SelectFiles", "Error uploading files: ${e.message}")
                false
            }
        }
    }

    suspend fun downloadFiles(
        file: FileItem,
        server: String,
        username: String,
        password: String,
        ftpUtil: FTPUtil,
        onDownloadComplete: (Boolean) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val localDirectoryPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.absolutePath
                    ?: context.filesDir.absolutePath
                val localFileName = file.name
                val remoteFilePath = file.name

                if (ftpUtil.connect(server, 21, username, password)) {
                    val downloadSuccess = ftpUtil.downloadFile(remoteFilePath, localDirectoryPath, localFileName)
                    ftpUtil.disconnect()
                    withContext(Dispatchers.Main) {
                        onDownloadComplete(downloadSuccess)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onDownloadComplete(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectFiles", "Error downloading files: ${e.message}")
                withContext(Dispatchers.Main) {
                    onDownloadComplete(false)
                }
            }
        }
    }
}


