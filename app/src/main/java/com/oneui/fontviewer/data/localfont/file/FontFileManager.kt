package com.oneui.fontviewer.data.localfont.file

import java.io.File

import com.oneui.fontviewer.data.localfont.model.FontRenameResult

object FontFileManager {

    fun renameFile(oldPath: String, newFileName: String): FontRenameResult {
        val oldFile = File(oldPath)

        if (!oldFile.exists()) {
            return FontRenameResult.failure("الملف غير موجود")
        }

        val parentDir = oldFile.parentFile
        if (parentDir == null) {
            return FontRenameResult.failure("خطأ في المسار")
        }

        val newFile = File(parentDir, newFileName)

        if (newFile.exists()) {
            return FontRenameResult.failure("الاسم موجود بالفعل")
        }

        if (!oldFile.renameTo(newFile)) {
            return FontRenameResult.failure("فشلت إعادة التسمية")
        }

        return FontRenameResult.success(newFile.absolutePath, newFile.lastModified())
    }
}
