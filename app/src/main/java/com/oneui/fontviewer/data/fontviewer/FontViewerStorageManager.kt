package com.oneui.fontviewer.data.fontviewer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.provider.DocumentsContract

import androidx.documentfile.provider.DocumentFile

import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FontViewerStorageManager(context: Context) {

    private val context: Context = context.applicationContext

    fun copyFontForViewing(fontUri: Uri?, suggestedFileName: String?): File? {
        if (fontUri == null) {
            Log.w(TAG, "Font URI is null")
            return null
        }

        try {
            var fileName = if (suggestedFileName != null) suggestedFileName else getFileNameFromUri(fontUri)
            if (fileName == null) {
                fileName = "font_" + System.currentTimeMillis() + ".ttf"
            }

            var extension = ".ttf"
            val lastDotIndex = fileName.lastIndexOf('.')
            if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
                extension = fileName.substring(lastDotIndex)
            }

            val fontsDirectory = fontsDirectory
            if (fontsDirectory == null) {
                Log.e(TAG, "Failed to get or create fonts directory")
                return null
            }

            val outputFile = File(fontsDirectory, "viewer_font_" + UUID.randomUUID().toString() + extension)

            var result: File? = null
            try {
                context.contentResolver.openInputStream(fontUri).use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->

                        if (inputStream == null) {
                            Log.e(TAG, "Failed to open input stream for URI: $fontUri")
                        } else {
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                outputStream.write(buffer, 0, bytesRead)
                            }

                            outputStream.flush()
                            Log.d(TAG, "Successfully copied font to: " + outputFile.absolutePath)
                            result = outputFile
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error copying font file", e)
                if (outputFile.exists()) {
                    outputFile.delete()
                }
                result = null
            }
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in copyFontForViewing", e)
            return null
        }
    }

    fun getFileNameFromUri(uri: Uri?): String? {
        if (uri == null) {
            return null
        }

        var fileName: String? = null

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get filename from ContentResolver", e)
        }

        if (fileName == null) {
            try {
                val documentFile = DocumentFile.fromSingleUri(context, uri)
                if (documentFile != null && documentFile.name != null) {
                    fileName = documentFile.name
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get filename from DocumentFile", e)
            }
        }

        if (fileName == null) {
            val path = uri.path
            if (path != null) {
                val lastSlashIndex = path.lastIndexOf('/')
                if (lastSlashIndex >= 0 && lastSlashIndex < path.length - 1) {
                    fileName = path.substring(lastSlashIndex + 1)
                }
            }
        }

        if (fileName == null) {
            fileName = "font_" + System.currentTimeMillis() + ".ttf"
        }

        return fileName
    }

    val fontsDirectory: File?
        get() {
            val fontsDirectory = File(context.filesDir, VIEWER_FONTS_DIR)

            if (!fontsDirectory.exists()) {
                if (!fontsDirectory.mkdirs()) {
                    Log.e(TAG, "Failed to create fonts directory")
                    return null
                }
            }

            return fontsDirectory
        }


    fun getRealPathFromUri(uri: Uri?): String? {
        if (uri == null) return null

        var realPath: String? = null

        if (DocumentsContract.isDocumentUri(context, uri)) {
            val documentId: String? = DocumentsContract.getDocumentId(uri)
            if (documentId != null && documentId.startsWith("primary:")) {
                realPath = "/storage/emulated/0/" + documentId.substring(8)
            }
        }

        if (realPath == null && "com.sec.android.app.myfiles.FileProvider" == uri.authority) {
            realPath = resolveSamsungMyFilesPath(uri)
        }

        if (realPath == null && "content".equals(uri.scheme, ignoreCase = true)) {
            try {
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val columnIndex = cursor.getColumnIndexOrThrow("_data")
                        realPath = cursor.getString(columnIndex)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get real path from content resolver", e)
            }
        }

        if (realPath == null) {
            realPath = uri.path
        }

        val finalPath = realPath
        if (finalPath != null && File(finalPath).exists()) {
            return finalPath
        }

        return null
    }

    private fun resolveSamsungMyFilesPath(uri: Uri): String? {
        val segments = uri.pathSegments
        if (segments.size < 2) return null

        val storageType = segments[0]
        val storageId = segments[1]

        val relativePath = StringBuilder()
        for (i in 2 until segments.size) {
            relativePath.append("/").append(segments[i])
        }

        val basePath: String
        if ("device_storage" == storageType) {
            basePath = "/storage/emulated/$storageId"
        } else if ("external_storage" == storageType) {
            basePath = "/storage/$storageId"
        } else {
            return null
        }

        return basePath + relativePath.toString()
    }

    companion object {
        private const val TAG = "FontViewerStorageManager"
        private const val VIEWER_FONTS_DIR = "fonts"
        private const val BUFFER_SIZE = 100000
    }
}
