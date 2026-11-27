package com.oneui.fontviewer.utils

import java.io.File

object FileUtils {

    fun removeExtension(fileName: String?): String {
        if (fileName == null || fileName.isEmpty()) {
            return ""
        }

        val lastDotIndex = fileName.lastIndexOf('.')

        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex)
        }

        return fileName
    }

    fun getExtension(fileName: String?): String {
        if (fileName == null || fileName.isEmpty()) {
            return ""
        }

        val lastDotIndex = fileName.lastIndexOf('.')

        if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            return fileName.substring(lastDotIndex + 1)
        }

        return ""
    }

    fun getFileName(path: String): String {
        return File(path).name
    }

    fun getParentPath(path: String): String? {
        return File(path).parent
    }

    fun exists(parentPath: String?, fileName: String): Boolean {
        return File(parentPath, fileName).exists()
    }

    fun isValidFileName(name: String?): Boolean {
        if (name == null || name.trim { it <= ' ' }.isEmpty()) return false
        val forbiddenChars = arrayOf("/", "\\", ":", "*", "?", "\"", "<", ">", "|")
        for (forbidden in forbiddenChars) {
            if (name.contains(forbidden)) return false
        }
        return true
    }

}
