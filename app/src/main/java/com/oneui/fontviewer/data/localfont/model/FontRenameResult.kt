package com.oneui.fontviewer.data.localfont.model

class FontRenameResult private constructor(
    val isSuccess: Boolean,
    val errorMessage: String?,
    val newPath: String?,
    val newLastModified: Long
) {

    companion object {
        fun success(newPath: String, newLastModified: Long): FontRenameResult {
            return FontRenameResult(true, null, newPath, newLastModified)
        }

        fun failure(errorMessage: String): FontRenameResult {
            return FontRenameResult(false, errorMessage, null, 0L)
        }
    }
}
