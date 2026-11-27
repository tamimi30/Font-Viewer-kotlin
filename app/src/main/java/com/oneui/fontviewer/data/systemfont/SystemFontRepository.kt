package com.oneui.fontviewer.data.systemfont

import android.content.Context
import android.util.Log

import java.io.File
import java.util.concurrent.ExecutorService

import androidx.lifecycle.LiveData

import com.oneui.fontviewer.data.common.room.FontDao
import com.oneui.fontviewer.data.common.room.FontDatabase
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.common.metadata.FontMetadataExtractor
import com.oneui.fontviewer.data.common.metadata.FontWeightWidthExtractor

class SystemFontRepository private constructor(context: Context) {

    private val context: Context = context.applicationContext
    private val fontDao: FontDao
    private val executorService: ExecutorService

    init {
        val database = FontDatabase.getInstance(this.context)
        fontDao = database.fontDao()
        executorService = FontDatabase.databaseWriteExecutor
    }

    val systemFonts: LiveData<List<FontEntity>>
        get() = fontDao.getSystemFonts()

    val systemFontsCount: LiveData<Int>
        get() = fontDao.getSystemFontsCount()

    fun loadAndSyncSystemFonts(listener: OnSyncCompleteListener?) {
        executorService.execute {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    listener?.onSyncComplete(0, 0, 0)
                    return@execute
                }

                val manager = SystemFontManager.getInstance()
                val systemFonts: List<SystemFontInfo> = manager.systemFonts

                if (systemFonts.isEmpty()) {
                    listener?.onSyncComplete(0, 0, 0)
                    return@execute
                }

                val existingFonts = fontDao.getSystemFontsSync()
                val fontsToAdd = ArrayList<FontEntity>()
                val currentPaths = HashSet<String>()
                var updated = 0
                var deleted = 0

                for (fontInfo in systemFonts) {
                    currentPaths.add(fontInfo.path)
                    val existing = findByPath(existingFonts, fontInfo.path)

                    if (existing == null) {
                        val newFont = createFontEntityFromSystemFont(fontInfo)
                        fontsToAdd.add(newFont)

                        val newRealName = newFont.realName
                        if (newRealName != null && !newRealName.isEmpty()) {
                            Log.d(TAG, "✓ System font with ready name: $newRealName")
                        }
                    } else {
                        var needsUpdate = false

                        if (existing.lastModified != fontInfo.lastModified) {
                            existing.lastModified = fontInfo.lastModified
                            needsUpdate = true
                        }

                        if (existing.size != fontInfo.size) {
                            existing.size = fontInfo.size
                            needsUpdate = true
                        }

                        val infoRealName = fontInfo.realName
                        if (infoRealName != null && !infoRealName.isEmpty()) {
                            val currentRealName = existing.realName
                            if (currentRealName == null || currentRealName.isEmpty() ||
                                currentRealName != infoRealName
                            ) {
                                existing.realName = infoRealName
                                needsUpdate = true
                            }
                        }

                        if (needsUpdate) {
                            existing.updatedAt = System.currentTimeMillis()
                            fontDao.update(existing)
                            updated++
                        }
                    }
                }

                // حذف الخطوط التي كانت محفوظة في قاعدة البيانات ولم تعد موجودة في النظام
                // (مثلاً خط حُذف بتحديث للنظام)، وإلا تبقى ظاهرة في القائمة بملفات غير موجودة.
                // نصل الى هنا فقط اذا أعاد النظام قائمة غير فارغة (انظر الفحص أعلاه)، فلا نمسح القائمة كلها بسبب خطأ عابر.
                val staleFonts = ArrayList<FontEntity>()
                val stalePaths = ArrayList<String>()
                for (existing in existingFonts) {
                    if (!currentPaths.contains(existing.path)) {
                        staleFonts.add(existing)
                        stalePaths.add(existing.path)
                    }
                }

                if (!staleFonts.isEmpty()) {
                    deleted = fontDao.deleteAll(staleFonts)
                    SystemFontCache.getInstance().evict(stalePaths)
                    Log.d(TAG, "Removed $deleted system fonts that no longer exist")
                }

                if (!fontsToAdd.isEmpty()) {
                    fontDao.insertAll(fontsToAdd)
                }

                // === كود اختبار مؤقت: احذفه بعد الاختبار ===
                val testPrefs =
                    context.getSharedPreferences("sysfont_delete_test", Context.MODE_PRIVATE)
                if (!testPrefs.getBoolean("fake_inserted", false)) {
                    val fake = FontEntity("/system/fonts/FakeGoogleSans.ttf", "FakeGoogleSans.ttf")
                    fake.isSystemFont = true
                    fontDao.insert(fake)
                    testPrefs.edit().putBoolean("fake_inserted", true).apply()
                }
                // === نهاية كود الاختبار ===

                listener?.onSyncComplete(fontsToAdd.size, updated, deleted)

                extractSystemFontsRealNamesInBackgroundPrioritized()

                extractSystemFontsWeightWidthInBackground()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync system fonts", e)
                listener?.onSyncComplete(0, 0, 0)
            }
        }
    }

    private fun createFontEntityFromSystemFont(fontInfo: SystemFontInfo): FontEntity {
        val entity = FontEntity(fontInfo.path, fontInfo.name)
        entity.size = fontInfo.size
        entity.lastModified = fontInfo.lastModified
        entity.isSystemFont = true
        entity.ttcIndex = fontInfo.ttcIndex
        entity.isVariableFont = fontInfo.isVariableFont

        val realName = fontInfo.realName
        if (realName != null && !realName.isEmpty()) {
            entity.realName = realName
        }

        if (realName == null || realName.isEmpty()) {
            try {
                val fontFile = File(fontInfo.path)
                if (fontFile.exists()) {
                    val extractedName = FontMetadataExtractor.extractFontName(
                        fontFile, fontInfo.ttcIndex
                    )

                    if (extractedName != null && !extractedName.isEmpty() &&
                        extractedName != "Unknown Font"
                    ) {
                        entity.realName = extractedName
                        Log.d(TAG, "✓ Instantly extracted system font name: $extractedName")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Quick extraction failed for system font: " + e.message)
            }
        }

        try {
            val fontFile = File(fontInfo.path)
            if (fontFile.exists()) {
                val weightWidthLabel = FontWeightWidthExtractor.extract(
                    fontFile, fontInfo.ttcIndex
                )
                entity.weightWidthLabel = weightWidthLabel
                Log.d(TAG, "✓ Instantly extracted weight/width: $weightWidthLabel")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Quick weight/width extraction failed for system font: " + e.message)
        }

        val fileName = fontInfo.name.lowercase()
        if (fileName.endsWith(".ttc")) {
            entity.fontType = "TTC"
        } else if (fileName.endsWith(".otf")) {
            entity.fontType = "OTF"
        } else {
            entity.fontType = "TTF"
        }

        if (fontInfo.isVariableFont) {
            entity.fontType = "Variable"
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

    private fun extractSystemFontsRealNamesInBackgroundPrioritized() {
        executorService.execute {
            try {
                val fontsWithoutNames = fontDao.getFontsWithoutRealName(500)
                    .sortedWith { f1, f2 -> f1.size.compareTo(f2.size) }

                var extracted = 0
                for (font in fontsWithoutNames) {
                    if (font.isSystemFont) {
                        try {
                            val fontFile = File(font.path)
                            if (fontFile.exists()) {
                                val realName = FontMetadataExtractor.extractFontName(
                                    fontFile, font.ttcIndex
                                )

                                if (realName != null && !realName.isEmpty() &&
                                    realName != "Unknown Font"
                                ) {
                                    fontDao.updateRealName(
                                        font.path, realName,
                                        System.currentTimeMillis()
                                    )
                                    extracted++

                                    if (extracted % 10 == 0) {
                                        Log.d(TAG, "Background extraction: $extracted system font names")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to extract name in background: " + e.message)
                        }
                    }
                }

                Log.d(TAG, "★ Background extraction complete: $extracted system font names extracted")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract system fonts real names", e)
            }
        }
    }

    private fun extractSystemFontsWeightWidthInBackground() {
        executorService.execute {
            try {
                fontDao.resetLegacyWeightWidthLabels()
                val fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500)
                    .sortedWith { f1, f2 -> f1.size.compareTo(f2.size) }

                var extracted = 0
                for (font in fontsWithoutLabel) {
                    if (font.isSystemFont) {
                        try {
                            val fontFile = File(font.path)
                            if (fontFile.exists()) {
                                val label = FontWeightWidthExtractor.extract(
                                    fontFile, font.ttcIndex
                                )
                                fontDao.updateWeightWidthLabel(
                                    font.path, label, System.currentTimeMillis()
                                )
                                extracted++

                                if (extracted % 10 == 0) {
                                    Log.d(TAG, "System font weight/width extraction: $extracted labels")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to extract weight/width for system font: " + e.message)
                        }
                    }
                }

                Log.d(TAG, "★ System font weight/width extraction complete: $extracted labels extracted")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract weight/width for system fonts in background", e)
            }
        }
    }

    fun updateRealName(path: String, realName: String?) {
        executorService.execute {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update real name", e)
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

    fun updateCacheStatus(path: String, isCached: Boolean) {
        executorService.execute {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update cache status", e)
            }
        }
    }

    fun getFontByPath(path: String): LiveData<FontEntity> {
        return fontDao.getFontByPath(path)
    }

    fun searchSystemFonts(query: String): LiveData<List<FontEntity>> {
        return fontDao.searchFonts(true, query)
    }

    fun getSystemFontsSorted(sortType: SortType, ascending: Boolean): LiveData<List<FontEntity>> {
        return when (sortType) {
            SortType.DATE ->
                if (ascending) fontDao.getFontsSortedByDate(true)
                else fontDao.getFontsSortedByDateDesc(true)
            SortType.SIZE ->
                if (ascending) fontDao.getFontsSortedBySize(true)
                else fontDao.getFontsSortedBySizeDesc(true)
            SortType.NAME ->
                if (ascending) fontDao.getFontsSortedByName(true)
                else fontDao.getFontsSortedByNameDesc(true)
        }
    }

    fun deleteAllSystemFonts(listener: OnCompleteListener?) {
        executorService.execute {
            try {
                val rows = fontDao.deleteAllSystemFonts()
                listener?.onComplete(rows > 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete system fonts", e)
                listener?.onComplete(false)
            }
        }
    }

    fun getVariableFontsCount(listener: OnCountListener?) {
        executorService.execute {
            try {
                val count = fontDao.getVariableFontsCount()
                listener?.onCount(count)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get variable fonts count", e)
                listener?.onCount(0)
            }
        }
    }

    enum class SortType {
        NAME, DATE, SIZE
    }

    fun interface OnSyncCompleteListener {
        fun onSyncComplete(added: Int, updated: Int, deleted: Int)
    }

    fun interface OnCompleteListener {
        fun onComplete(success: Boolean)
    }

    fun interface OnCountListener {
        fun onCount(count: Int)
    }

    companion object {
        private const val TAG = "SystemFontRepository"

        @Volatile
        private var INSTANCE: SystemFontRepository? = null

        @JvmStatic
        fun getInstance(context: Context): SystemFontRepository {
            var instance = INSTANCE
            if (instance == null) {
                synchronized(SystemFontRepository::class.java) {
                    instance = INSTANCE
                    if (instance == null) {
                        instance = SystemFontRepository(context.applicationContext)
                        INSTANCE = instance
                    }
                }
            }
            return instance!!
        }
    }
}
