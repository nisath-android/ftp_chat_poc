import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel

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

   /* fun getFileExtension(context: Context, uri: Uri): String? {
        val filePath = getPath(context, uri) ?: return null
        return File(filePath).extension
    }*/

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
}
