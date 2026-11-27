package com.oneui.fontviewer.data.common.list

import java.util.Locale

class FontFileInfo(
    val name: String?,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val realName: String? = null
) {

    val displayName: String?
        get() {
            if (realName != null && realName.isNotEmpty()) {
                return realName
            }
            val n = name
            if (n != null) {
                val lower = n.lowercase(Locale.getDefault())
                if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")) {
                    return n.substring(0, n.length - 4)
                }
            }
            return n
        }
}
