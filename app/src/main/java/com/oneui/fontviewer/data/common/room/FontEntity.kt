package com.oneui.fontviewer.data.common.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(
    tableName = "fonts",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["is_system_font"]),
        Index(value = ["last_modified"]),
        Index(value = ["is_favorite"]),
        Index(value = ["is_trashed"]),
        Index(value = ["deleted_at"])
    ]
)
class FontEntity(path: String, fileName: String) {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var id: Long = 0

    @ColumnInfo(name = "path")
    var path: String = path

    @ColumnInfo(name = "file_name")
    var fileName: String = fileName

    @ColumnInfo(name = "real_name")
    var realName: String? = null

    @ColumnInfo(name = "size")
    var size: Long = 0

    @ColumnInfo(name = "last_modified")
    var lastModified: Long = 0

    @ColumnInfo(name = "font_type")
    var fontType: String? = null

    @ColumnInfo(name = "ttc_index")
    var ttcIndex: Int = 0

    @ColumnInfo(name = "is_system_font")
    var isSystemFont: Boolean = false

    @ColumnInfo(name = "is_variable_font")
    var isVariableFont: Boolean = false

    @ColumnInfo(name = "is_cached")
    var isCached: Boolean = false

    @ColumnInfo(name = "last_access_time")
    var lastAccessTime: Long = 0

    @ColumnInfo(name = "access_count")
    var accessCount: Int = 0

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis()

    @ColumnInfo(name = "updated_at")
    var updatedAt: Long = System.currentTimeMillis()

    @ColumnInfo(name = "weight_width_label")
    var weightWidthLabel: String? = null

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    var isFavorite: Boolean = false

    @ColumnInfo(name = "is_trashed", defaultValue = "0")
    var isTrashed: Boolean = false

    @ColumnInfo(name = "deleted_at", defaultValue = "0")
    var deletedAt: Long = 0

    @ColumnInfo(name = "original_path")
    var originalPath: String? = null

    val displayName: String
        get() {
            val realName = this.realName
            if (realName != null && realName.isNotEmpty()) {
                return realName
            }

            val name = fileName
            if (name.lowercase().endsWith(".ttf") ||
                name.lowercase().endsWith(".otf") ||
                name.lowercase().endsWith(".ttc")) {
                return name.substring(0, name.length - 4)
            }
            return name
        }
}
