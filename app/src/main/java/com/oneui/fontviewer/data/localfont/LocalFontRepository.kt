package com.oneui.fontviewer.data.localfont

import android.content.Context
import android.util.Log

import java.io.File
import java.util.concurrent.ExecutorService

import androidx.lifecycle.LiveData

import com.oneui.fontviewer.data.common.room.FontDao
import com.oneui.fontviewer.data.common.room.FontDatabase
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.common.list.FontFileInfo
import com.oneui.fontviewer.data.localfont.file.LocalFontDirectory
import com.oneui.fontviewer.data.common.metadata.FontMetadataExtractor
import com.oneui.fontviewer.data.common.metadata.FontWeightWidthExtractor

class LocalFontRepository private constructor(context: Context) {

    companion object {
        private const val TAG = "LocalFontRepository"

        @Volatile
        private var INSTANCE: LocalFontRepository? = null

        fun getInstance(context: Context): LocalFontRepository {
            if (INSTANCE == null) {
                synchronized(LocalFontRepository::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = LocalFontRepository(context.applicationContext)
                    }
                }
            }
            return INSTANCE!!
        }
    }

    private val fontDao: FontDao
    private val executorService: ExecutorService
    private val database: FontDatabase

    enum class SortType {
        NAME,
        DATE,
        SIZE
    }

    init {
        database = FontDatabase.getInstance(context)
        fontDao = database.fontDao()
        executorService = FontDatabase.databaseWriteExecutor
    }

    val allFonts: LiveData<List<FontEntity>>
        get() = fontDao.getAllFonts()

    val localFonts: LiveData<List<FontEntity>>
        get() = fontDao.getLocalFonts()

    val localFontsSync: List<FontEntity>
        get() = fontDao.getLocalFontsSync()

    fun getFontByPath(path: String): LiveData<FontEntity> {
        return fontDao.getFontByPath(path)
    }

    val totalFontsCount: LiveData<Int>
        get() = fontDao.getTotalFontsCount()

    val localFontsCount: LiveData<Int>
        get() = fontDao.getLocalFontsCount()

    fun getFontsSortedByName(isSystem: Boolean, ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFontsSortedByName(isSystem)
        } else {
            fontDao.getFontsSortedByNameDesc(isSystem)
        }
    }

    fun getFontsSortedByDate(isSystem: Boolean, ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFontsSortedByDate(isSystem)
        } else {
            fontDao.getFontsSortedByDateDesc(isSystem)
        }
    }

    fun getFontsSortedBySize(isSystem: Boolean, ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFontsSortedBySize(isSystem)
        } else {
            fontDao.getFontsSortedBySizeDesc(isSystem)
        }
    }

    fun searchFonts(isSystem: Boolean, query: String): LiveData<List<FontEntity>> {
        return fontDao.searchFonts(isSystem, query)
    }


    val favoriteFonts: LiveData<List<FontEntity>>
        get() = fontDao.getFavoriteFonts()

    val favoriteFontsCount: LiveData<Int>
        get() = fontDao.getFavoriteFontsCount()

    fun getFavoritesSortedByName(ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFavoriteFontsSortedByName()
        } else {
            fontDao.getFavoriteFontsSortedByNameDesc()
        }
    }

    fun getFavoritesSortedByDate(ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFavoriteFontsSortedByDate()
        } else {
            fontDao.getFavoriteFontsSortedByDateDesc()
        }
    }

    fun getFavoritesSortedBySize(ascending: Boolean): LiveData<List<FontEntity>> {
        return if (ascending) {
            fontDao.getFavoriteFontsSortedBySize()
        } else {
            fontDao.getFavoriteFontsSortedBySizeDesc()
        }
    }

    fun searchFavoriteFonts(query: String): LiveData<List<FontEntity>> {
        return fontDao.searchFavoriteFonts(query)
    }

    fun updateFavoriteStatus(path: String, isFavorite: Boolean, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val rows = fontDao.updateFavoriteStatus(path, isFavorite, System.currentTimeMillis())
                Log.d(TAG, "★ Favorite status updated: $path → $isFavorite")
                listener?.onComplete(rows > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update favorite status", e)
                listener?.onComplete(false)
            }
        }
    }

    fun updateFavoriteStatusBatch(
        paths: List<String>, isFavorite: Boolean,
        listener: OnCompleteListener?
    ) {
        executorService.execute {
            try {
                val now = System.currentTimeMillis()
                var totalRows = 0

                database.runInTransaction(Runnable {
                    for (path in paths) {
                        totalRows += fontDao.updateFavoriteStatus(path, isFavorite, now)
                    }
                })

                Log.d(
                    TAG, "★ Batch favorite status updated: " + paths.size
                        + " fonts → " + isFavorite
                )
                listener?.onComplete(totalRows > 0)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to batch update favorite status", e)
                listener?.onComplete(false)
            }
        }
    }


    fun insert(font: FontEntity, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val id = fontDao.insert(font)
                listener?.onComplete(id > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert font", e)
                listener?.onComplete(false)
            }
        }
    }

    fun insertAll(fonts: List<FontEntity>, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val ids = fontDao.insertAll(fonts)
                listener?.onComplete(ids.size == fonts.size)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert fonts", e)
                listener?.onComplete(false)
            }
        }
    }

    fun update(font: FontEntity, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val rows = fontDao.update(font)
                listener?.onComplete(rows > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update font", e)
                listener?.onComplete(false)
            }
        }
    }

    fun delete(font: FontEntity, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val rows = fontDao.delete(font)
                listener?.onComplete(rows > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete font", e)
                listener?.onComplete(false)
            }
        }
    }

    fun deleteByPath(path: String, listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val rows = fontDao.deleteByPath(path)
                listener?.onComplete(rows > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete font by path", e)
                listener?.onComplete(false)
            }
        }
    }

    fun updateCacheStatus(path: String, isCached: Boolean) {
        executorService.execute {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update cache status", e)
            }
        }
    }

    fun recordAccess(path: String) {
        executorService.execute {
            try {
                val now = System.currentTimeMillis()
                fontDao.recordAccess(path, now, now)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record access", e)
            }
        }
    }

    fun updateRealName(path: String, realName: String) {
        executorService.execute {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis())
                Log.d(TAG, "Updated real name for: $path = $realName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update real name", e)
            }
        }
    }

    fun updatePath(oldPath: String, newPath: String, newFileName: String) {
        executorService.execute {
            try {
                fontDao.updatePath(oldPath, newPath, newFileName, System.currentTimeMillis())
                Log.d(TAG, "Updated font path: $oldPath -> $newPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update path", e)
            }
        }
    }

    fun loadAndSyncLocalFonts(folderPath: String?, listener: OnSyncCompleteListener?) {
        executorService.execute {
            try {
                val filesInFolder: List<FontFileInfo> =
                    LocalFontDirectory.getFontsInDirectory(folderPath)

                if (filesInFolder.isEmpty()) {
                    val existingFonts = fontDao.getLocalFontsSync()
                    var deletedCount = 0
                    if (existingFonts.isNotEmpty()) {
                        for (existing in existingFonts) {
                            fontDao.delete(existing)
                            deletedCount++
                        }
                    }

                    if (listener != null) {
                        listener.onSyncComplete(0, 0, deletedCount)
                        listener.onFullExtractionComplete()
                    }
                    return@execute
                }

                val existingFonts = fontDao.getLocalFontsSync()
                val fontsToAdd = ArrayList<FontEntity>()
                var updated = 0
                var deleted = 0

                for (fileInfo in filesInFolder) {
                    val existing = findByPath(existingFonts, fileInfo.path)

                    if (existing == null) {
                        val newFont = createFontEntityFromFile(fileInfo, false)

                        try {
                            val fontFile = File(fileInfo.path)
                            val realName = FontMetadataExtractor.extractFontName(fontFile, 0)
                            if (realName != null && realName.isNotEmpty() &&
                                realName != "Unknown Font"
                            ) {
                                newFont.realName = realName
                                Log.d(TAG, "★ Instantly extracted name: $realName")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Quick extraction failed: " + e.message)
                        }

                        try {
                            val fontFile = File(fileInfo.path)
                            val weightWidthLabel = FontWeightWidthExtractor.extract(fontFile, 0)
                            newFont.weightWidthLabel = weightWidthLabel
                            Log.d(TAG, "★ Instantly extracted weight/width: $weightWidthLabel")
                        } catch (e: Exception) {
                            Log.w(TAG, "Quick weight/width extraction failed: " + e.message)
                        }

                        fontsToAdd.add(newFont)
                    } else if (existing.lastModified != fileInfo.lastModified) {
                        existing.lastModified = fileInfo.lastModified
                        existing.size = fileInfo.size
                        existing.updatedAt = System.currentTimeMillis()
                        fontDao.update(existing)
                        updated++
                    }
                }

                for (existing in existingFonts) {
                    if (!fileExistsInList(filesInFolder, existing.path)) {
                        fontDao.delete(existing)
                        deleted++
                    }
                }

                if (fontsToAdd.isNotEmpty()) {
                    fontDao.insertAll(fontsToAdd)
                }

                listener?.onSyncComplete(fontsToAdd.size, updated, deleted)

                executorService.execute {
                    extractRealNamesSync()
                    extractWeightWidthSync()

                    listener?.onFullExtractionComplete()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync local fonts", e)
                if (listener != null) {
                    listener.onSyncComplete(0, 0, 0)
                    listener.onFullExtractionComplete()
                }
            }
        }
    }

    private fun createFontEntityFromFile(fileInfo: FontFileInfo, isSystem: Boolean): FontEntity {
        val entity = FontEntity(fileInfo.path, fileInfo.name)
        entity.size = fileInfo.size
        entity.lastModified = fileInfo.lastModified
        entity.isSystemFont = isSystem
        entity.ttcIndex = 0

        val fileName = fileInfo.name.lowercase()
        if (fileName.endsWith(".ttc")) {
            entity.fontType = "TTC"
        } else if (fileName.endsWith(".otf")) {
            entity.fontType = "OTF"
        } else {
            entity.fontType = "TTF"
        }

        return entity
    }

    private fun findByPath(fonts: List<FontEntity>, path: String): FontEntity? {
        for (font in fonts) {
            if (font.path == path) {
                return font
            }
        }
        return null
    }

    private fun fileExistsInList(files: List<FontFileInfo>, path: String): Boolean {
        for (file in files) {
            if (file.path == path) {
                return true
            }
        }
        return false
    }



    private fun extractRealNamesSync() {
        try {
            val fontsWithoutNames = fontDao.getFontsWithoutRealName(500)
                .sortedBy { it.size }

            class NameUpdate(val path: String, val realName: String)
            val updates = ArrayList<NameUpdate>()

            for (font in fontsWithoutNames) {
                if (!font.isSystemFont) {
                    try {
                        val fontFile = File(font.path)
                        if (fontFile.exists()) {
                            val realName = FontMetadataExtractor.extractFontName(
                                fontFile, font.ttcIndex
                            )
                            if (realName != null && realName.isNotEmpty() &&
                                realName != "Unknown Font"
                            ) {
                                updates.add(NameUpdate(font.path, realName))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract name: " + e.message)
                    }
                }
            }

            if (updates.isNotEmpty()) {
                database.runInTransaction(Runnable {
                    val now = System.currentTimeMillis()
                    for (update in updates) {
                        fontDao.updateRealName(update.path, update.realName, now)
                    }
                })
            }

            Log.d(TAG, "★ extractRealNamesSync complete: " + updates.size + " names extracted")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract real names", e)
        }
    }

    private fun extractWeightWidthSync() {
        try {
            fontDao.resetLegacyWeightWidthLabels()
            val fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500)
                .sortedBy { it.size }

            class LabelUpdate(val path: String, val label: String)
            val updates = ArrayList<LabelUpdate>()

            for (font in fontsWithoutLabel) {
                if (!font.isSystemFont) {
                    try {
                        val fontFile = File(font.path)
                        if (fontFile.exists()) {
                            val label = FontWeightWidthExtractor.extract(
                                fontFile, font.ttcIndex
                            )
                            // NOTE(kotlin-migration): the Java code also queued null labels and passed null to
                            // updateWeightWidthLabel (a no-op that only bumped updated_at); FontDao.updateWeightWidthLabel
                            // takes a non-null String in Kotlin, so null labels are skipped.
                            if (label != null) {
                                updates.add(LabelUpdate(font.path, label))
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract weight/width: " + e.message)
                    }
                }
            }

            if (updates.isNotEmpty()) {
                database.runInTransaction(Runnable {
                    val now = System.currentTimeMillis()
                    for (update in updates) {
                        fontDao.updateWeightWidthLabel(update.path, update.label, now)
                    }
                })
            }

            Log.d(TAG, "★ extractWeightWidthSync complete: " + updates.size + " labels extracted")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract weight/width labels", e)
        }
    }

    fun interface OnCompleteListener {
        fun onComplete(success: Boolean)
    }

    interface OnSyncCompleteListener {
        fun onSyncComplete(added: Int, updated: Int, deleted: Int)
        fun onFullExtractionComplete()
    }
}
