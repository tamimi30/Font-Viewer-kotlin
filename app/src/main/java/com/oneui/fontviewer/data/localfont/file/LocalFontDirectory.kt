package com.oneui.fontviewer.data.localfont.file

import android.os.Environment
import android.util.Log

import com.oneui.fontviewer.data.common.list.FontFileInfo

import java.io.File
import java.util.Locale

object LocalFontDirectory {

    private const val TAG = "LocalFontDirectory"

    fun getFontsInDirectory(directoryPath: String?): List<FontFileInfo> {
        val fontFiles: MutableList<FontFileInfo> = ArrayList()

        if (directoryPath == null || directoryPath.isEmpty()) {
            Log.w(TAG, "Directory path is null or empty")
            return fontFiles
        }

        try {
            val directory = File(directoryPath)

            if (!directory.exists()) {
                Log.w(TAG, "Directory does not exist: $directoryPath")
                return fontFiles
            }

            if (!directory.isDirectory) {
                Log.w(TAG, "Path is not a directory: $directoryPath")
                return fontFiles
            }

            val files = directory.listFiles()

            if (files == null) {
                Log.w(TAG, "Cannot list files in directory (permission denied?): $directoryPath")
                return fontFiles
            }

            for (file in files) {
                if (file.isFile && file.canRead()) {
                    val name = file.name
                    val nameLower = name.lowercase(Locale.getDefault())

                    if (nameLower.endsWith(".ttf") || nameLower.endsWith(".otf") || nameLower.endsWith(".ttc")) {
                        fontFiles.add(
                            FontFileInfo(
                                name,
                                file.absolutePath,
                                file.length(),
                                file.lastModified()
                            )
                        )
                    }
                }
            }

            Log.d(TAG, "Found " + fontFiles.size + " font files in: " + directoryPath)

        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception reading directory: $directoryPath", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading directory: $directoryPath", e)
        }

        return fontFiles
    }

    fun isAccessible(folderPath: String): Boolean {
        val directory = File(folderPath)
        return directory.isDirectory && directory.list() != null
    }

    @Suppress("DEPRECATION")
    fun getDownloadsDirectoryPath(): String? {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloadsDir != null && downloadsDir.exists()) {
            return downloadsDir.absolutePath
        }
        return null
    }

    @Suppress("DEPRECATION")
    fun getRealPathFromTreeUri(treeUri: android.net.Uri?): String? {
        if (treeUri == null) {
            return null
        }

        try {
            val uriPath = treeUri.toString()

            if (uriPath.contains("primary:")) {
                val relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8)
                return Environment.getExternalStorageDirectory().absolutePath + "/" + relativePath
            }

            if (uriPath.contains("/storage/")) {
                val storageIndex = uriPath.indexOf("/storage/")
                var potentialPath = uriPath.substring(storageIndex)

                if (potentialPath.contains("%3A")) {
                    potentialPath = potentialPath.replace("%3A", "/")
                }
                if (potentialPath.contains(":")) {
                    potentialPath = potentialPath.replace(":", "/")
                }

                if (potentialPath.startsWith("/storage/emulated/0/")) {
                    return potentialPath
                } else if (potentialPath.contains("/storage/")) {
                    return potentialPath.substring(potentialPath.indexOf("/storage/"))
                }
            }

            val documentId: String? = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            if (documentId != null) {
                if (documentId.startsWith("primary:")) {
                    val path = documentId.substring(8)
                    return Environment.getExternalStorageDirectory().absolutePath + "/" + path
                } else if (documentId.contains(":")) {
                    // Java's String.split drops trailing empty strings; mirror that behaviour
                    val parts = documentId.split(":").dropLastWhile { it.isEmpty() }
                    if (parts.size > 1) {
                        val basePath = "/storage/" + parts[0]
                        val baseDir = File(basePath)
                        if (baseDir.exists()) {
                            return basePath + "/" + parts[1]
                        }
                    }
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to path", e)
        }

        Log.w(TAG, "Could not determine real path, using fallback")
        return null
    }
}
