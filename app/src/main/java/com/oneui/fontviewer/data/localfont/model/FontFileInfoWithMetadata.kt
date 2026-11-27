package com.oneui.fontviewer.data.localfont.model

import com.oneui.fontviewer.data.common.room.FontEntity

class FontFileInfoWithMetadata(entity: FontEntity) {
    val name: String = entity.fileName
    val path: String = entity.path
    val size: Long = entity.size
    val lastModified: Long = entity.lastModified
    val realName: String? = entity.realName
    val weightWidthLabel: String? = entity.weightWidthLabel
    val isFavorite: Boolean = entity.isFavorite
}
