import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

private const val TAG = "FileUtils"
object FileUtils {
    fun getPath(context: Context, uri: Uri): String? {
        // Check if the Uri is a file Uri
        if (uri.scheme.equals("content", ignoreCase = true)) {
            // Check for API level 29+ to handle scoped storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return copyFileToAppSpecificStorage(context, uri)
            } else {
                // For lower API versions, return the file path directly
                return getRealPathFromURI(context, uri)
            }
        } else if (uri.scheme.equals("file", ignoreCase = true)) {
            return uri.path
        }

        return null
    }

    private fun getFileExtension(context: Context, uri: Uri): String? {
        return context.contentResolver.getType(uri)?.let { mimeType ->
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        }
    }
    private fun copyFileToAppSpecificStorage(context: Context, uri: Uri): String? {
        try {
            val contentResolver: ContentResolver = context.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(uri) ?: return null
            val file = File(context.filesDir, File(uri.path!!).name) // Save the file in app-specific directory
            val outputStream: OutputStream = context.openFileOutput(file.name, Context.MODE_PRIVATE)

            val inputChannel: FileChannel = (inputStream as java.io.FileInputStream).channel
            val outputChannel: FileChannel = (outputStream as java.io.FileOutputStream).channel
            inputChannel.transferTo(0, inputChannel.size(), outputChannel)

            inputStream.close()
            outputStream.close()

            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getRealPathFromURI(context: Context, uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)
        cursor?.moveToFirst()
        val columnIndex: Int = cursor?.getColumnIndexOrThrow(MediaStore.Images.Media.DATA) ?: -1
        val filePath = if (columnIndex != -1) cursor?.getString(columnIndex) else null
        cursor?.close()
        return filePath
    }

    // Function to determine the file type and extension based on filePath or file extension
     fun getFileTypeAndExt(
        context: Context,
        filePath: String,
        fileUri: Uri? = null
    ): Pair<String, String> {
        val fileTypeMapping = mapOf(
            "image" to listOf("png", "jpg", "jpeg", "gif", "bmp"),
            "pdf" to listOf("pdf"),
            "text" to listOf("txt", "csv", "xml"),
            "audio" to listOf("mp3", "wav", "aac", "m4a"),
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
}
