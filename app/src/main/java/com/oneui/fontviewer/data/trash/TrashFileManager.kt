package com.oneui.fontviewer.data.trash

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.utils.FileUtils

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object TrashFileManager {

    private const val TAG = "TrashFileManager"

    private const val TRASH_DIR_NAME = ".Trash"

    private const val FALLBACK_RESTORE_DIR_NAME = "RestoredFonts"


    fun getTrashDirectory(context: Context): File? {
        val trashDir = File(context.filesDir, TRASH_DIR_NAME)

        if (!trashDir.exists()) {
            val created = trashDir.mkdirs()
            if (!created) {
                Log.e(TAG, "Failed to create .Trash directory at: " + trashDir.absolutePath)
                return null
            }
            Log.d(TAG, ".Trash directory created at: " + trashDir.absolutePath)
        }

        return trashDir
    }


    fun moveToTrash(context: Context, originalPath: String): String? {
        val sourceFile = File(originalPath)

        if (!sourceFile.exists()) {
            Log.w(TAG, "Source file does not exist: $originalPath")
            return null
        }
        if (!sourceFile.canRead()) {
            Log.w(TAG, "Source file is not readable: $originalPath")
            return null
        }

        val trashDir = getTrashDirectory(context)
        if (trashDir == null) {
            Log.e(TAG, "Cannot access or create .Trash directory")
            return null
        }

        val destinationFile = resolveNamingConflict(File(trashDir, sourceFile.name))

        val copied = copyFile(sourceFile, destinationFile)
        if (!copied) {
            Log.e(TAG, "Failed to copy file to .Trash: $originalPath")
            return null
        }

        val deleted = sourceFile.delete()
        if (!deleted) {
            destinationFile.delete()
            Log.e(TAG, "Failed to delete original file after copy, rolled back: $originalPath")
            return null
        }

        Log.d(TAG, "Moved to trash: " + originalPath + " → " + destinationFile.absolutePath)
        return destinationFile.absolutePath
    }


    fun restoreFromTrash(context: Context, trashedPath: String, originalPath: String): String? {
        val trashedFile = File(trashedPath)

        if (!trashedFile.exists()) {
            Log.w(TAG, "Trashed file does not exist: $trashedPath")
            return null
        }

        val destinationFile = File(originalPath)
        val destinationDir = destinationFile.parentFile

        if (destinationDir != null && !destinationDir.exists()) {
            val dirCreated = destinationDir.mkdirs()
            if (!dirCreated) {
                Log.w(TAG, "Cannot create destination directory: "
                        + destinationDir.absolutePath
                        + " — restoring to fallback location instead")
                return restoreToFallbackLocation(context, trashedFile)
            }
        }

        val resolvedDestination = resolveNamingConflict(destinationFile)

        val copied = copyFile(trashedFile, resolvedDestination)
        if (!copied) {
            Log.e(TAG, "Failed to restore file: "
                    + trashedPath + " → " + resolvedDestination.absolutePath)
            return null
        }

        val deleted = trashedFile.delete()
        if (!deleted) {
            Log.w(TAG, "Restored successfully but failed to delete from .Trash: $trashedPath")
        }

        Log.d(TAG, "Restored from trash: " + trashedPath + " → " + resolvedDestination.absolutePath)
        return resolvedDestination.absolutePath
    }


    fun deletePermanently(trashedPath: String): Boolean {
        val file = File(trashedPath)

        if (!file.exists()) {
            Log.w(TAG, "File to permanently delete does not exist (already gone?): $trashedPath")
            return true
        }

        val deleted = file.delete()

        if (deleted) {
            Log.d(TAG, "Permanently deleted: $trashedPath")
        } else {
            Log.e(TAG, "Failed to permanently delete: $trashedPath")
        }

        return deleted
    }


    private fun resolveNamingConflict(targetFile: File): File {
        if (!targetFile.exists()) {
            return targetFile
        }

        val parentDir: File? = targetFile.parentFile
        val fileName: String = targetFile.name
        val nameWithoutExt: String = FileUtils.removeExtension(fileName)
        val extension: String = FileUtils.getExtension(fileName)
        val dotExtension: String = if (extension.isEmpty()) "" else ".$extension"

        var counter = 1
        var candidate: File
        do {
            val newName = nameWithoutExt + "(" + counter + ")" + dotExtension
            candidate = File(parentDir, newName)
            counter++
        } while (candidate.exists())

        Log.d(TAG, "Naming conflict resolved: " + fileName + " → " + candidate.name)
        return candidate
    }

    private fun copyFile(source: File, destination: File): Boolean {
        return try {
            FileInputStream(source).use { fis ->
                FileOutputStream(destination).use { fos ->
                    fis.channel.use { inputChannel ->
                        fos.channel.use { outputChannel ->
                            outputChannel.transferFrom(inputChannel, 0, inputChannel.size())
                        }
                    }
                }
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy file: "
                    + source.absolutePath + " → " + destination.absolutePath, e)
            if (destination.exists()) {
                destination.delete()
            }
            false
        }
    }

    private fun restoreToFallbackLocation(context: Context, trashedFile: File): String? {
        val fallbackDir = File(context.filesDir, FALLBACK_RESTORE_DIR_NAME)
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs()
        }

        val destinationFile = resolveNamingConflict(File(fallbackDir, trashedFile.name))
        val copied = copyFile(trashedFile, destinationFile)

        if (copied) {
            trashedFile.delete()
            Log.w(TAG, "Restored to fallback location: " + destinationFile.absolutePath)
            return destinationFile.absolutePath
        }

        Log.e(TAG, "Failed to restore even to fallback location for: " + trashedFile.absolutePath)
        return null
    }
}
