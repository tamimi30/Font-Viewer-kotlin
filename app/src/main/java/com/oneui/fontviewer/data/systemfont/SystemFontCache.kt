package com.oneui.fontviewer.data.systemfont

import android.content.Context
import android.graphics.Typeface
import android.util.Log

import com.oneui.fontviewer.data.common.room.FontDatabase
import com.oneui.fontviewer.data.common.variable.VariableTypefaceFactory

import java.io.File
import java.util.concurrent.ConcurrentHashMap

class SystemFontCache private constructor() {

    private val memoryCache: ConcurrentHashMap<String, Typeface?> = ConcurrentHashMap(150)
    private val weightedCache: ConcurrentHashMap<String, Typeface?> = ConcurrentHashMap(300)
    private var context: Context? = null
    private var database: FontDatabase? = null

    @Volatile
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }

        val appContext = context.applicationContext
        this.context = appContext
        this.database = FontDatabase.getInstance(appContext)

        Log.d(TAG, "SystemFontCache initializing with Room Database")

        preloadCachedFontsFromDatabase()

        isInitialized = true
    }

    private fun preloadCachedFontsFromDatabase() {
        Thread({ runPreload() }, "SystemFontCache-Preloader").start()
    }

    private fun runPreload() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        try {
            val startTime = System.currentTimeMillis()

            val allCachedFonts = database!!.fontDao().getCachedFonts()

            if (allCachedFonts.isEmpty()) {
                Log.d(TAG, "No cached system fonts found in database")
                return
            }

            val cachedSystemFonts = allCachedFonts.filter { font -> font.isSystemFont }

            if (cachedSystemFonts.isEmpty()) {
                Log.d(TAG, "No cached system fonts after filtering")
                return
            }

            Log.d(TAG, "Found " + cachedSystemFonts.size + " cached system fonts")

            var loadedCount = 0

            val sortedFonts = cachedSystemFonts.sortedWith { f1, f2 ->
                val countCompare = f2.accessCount.compareTo(f1.accessCount)
                if (countCompare != 0) {
                    countCompare
                } else {
                    f2.lastAccessTime.compareTo(f1.lastAccessTime)
                }
            }

            for (font in sortedFonts) {
                val path = font.path

                val typeface = loadTypefaceInternal(path, 0f, 0)

                if (typeface != null) {
                    memoryCache[path] = typeface
                    loadedCount++

                    if (loadedCount % 10 == 0) {
                        Log.d(TAG, "Preloaded $loadedCount system fonts...")
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "★ Auto-preloaded $loadedCount system fonts in ${duration}ms")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload cached system fonts from database", e)
        }
    }

    /**
     * يزيل من الذاكرة المؤقتة الخطوط التي لم تعد موجودة في النظام، حتى لا يبقى Typeface لملف محذوف.
     */
    fun evict(fontPaths: Collection<String?>?) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return
        }

        for (path in fontPaths) {
            if (path != null) {
                memoryCache.remove(path)
                weightedCache.remove(path)
            }
        }
    }

    fun getIfCached(fontPath: String?): Typeface? {
        if (fontPath == null) return null
        return memoryCache[fontPath]
    }

    fun getTypeface(fontPath: String?): Typeface? {
        if (fontPath == null || fontPath.isEmpty()) {
            return null
        }

        return memoryCache.computeIfAbsent(fontPath) { path -> loadTypefaceInternal(path, 0f, 0) }
    }

    /**
     * نسخة عامة تدعم تطبيق عدة محاور دفعة واحدة (وزن، عرض، ميل، تدرج، استدارة، تباعد أحادي...)
     * على خط النظام.
     */
    fun getTypefaceWithAxes(fontPath: String?, axisValues: Map<String, Float>?, ttcIndex: Int): Typeface? {
        if (fontPath == null || fontPath.isEmpty()) {
            return null
        }

        Log.d(TAG, "Loading typeface with axes: $axisValues, ttcIndex: $ttcIndex")

        val typeface = loadTypefaceWithAxesInternal(fontPath, axisValues, ttcIndex)

        if (typeface != null) {
            Log.d(TAG, "Successfully created typeface with axes: $axisValues")
        } else {
            Log.e(TAG, "Failed to create typeface with axes: $axisValues")
        }

        return typeface
    }


    private fun loadTypefaceInternal(fontPath: String, weight: Float, ttcIndex: Int): Typeface? {
        val fontFile = File(fontPath)
        if (!fontFile.exists() || !fontFile.canRead()) {
            Log.w(TAG, "Font file does not exist or cannot be read: $fontPath")
            return null
        }

        return loadTypefaceUsingBuilder(fontFile, weight, ttcIndex)
    }

    private fun loadTypefaceUsingBuilder(fontFile: File, weight: Float, ttcIndex: Int): Typeface? {
        try {
            val fontBuilder = android.graphics.fonts.Font.Builder(fontFile)

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex)
                Log.d(TAG, "Set TTC index: $ttcIndex")
            }

            if (weight > 0 && weight != 400f) {
                val variationSettings = "'wght' $weight"
                fontBuilder.setFontVariationSettings(variationSettings)
                Log.d(TAG, "Set font variation settings: $variationSettings")
            }

            val font = fontBuilder.build()

            val fallbackBuilder = Typeface.CustomFallbackBuilder(
                android.graphics.fonts.FontFamily.Builder(font).build()
            )

            return fallbackBuilder.build()

        } catch (e: Exception) {
            Log.e(TAG, "Font.Builder failed", e)

            if (weight <= 0 || weight == 400f) {
                return loadTypefaceFromFile(fontFile.absolutePath)
            }

            return null
        }
    }

    private fun loadTypefaceWithAxesInternal(fontPath: String, axisValues: Map<String, Float>?, ttcIndex: Int): Typeface? {
        val fontFile = File(fontPath)
        if (!fontFile.exists() || !fontFile.canRead()) {
            Log.w(TAG, "Font file does not exist or cannot be read: $fontPath")
            return null
        }

        return loadTypefaceUsingBuilderWithAxes(fontFile, axisValues, ttcIndex)
    }

    private fun loadTypefaceUsingBuilderWithAxes(fontFile: File, axisValues: Map<String, Float>?, ttcIndex: Int): Typeface? {
        try {
            val fontBuilder = android.graphics.fonts.Font.Builder(fontFile)

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex)
                Log.d(TAG, "Set TTC index: $ttcIndex")
            }

            val variationSettings = VariableTypefaceFactory.buildVariationSettingsString(axisValues)
            if (!variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings)
                Log.d(TAG, "Set font variation settings: $variationSettings")
            }

            val font = fontBuilder.build()

            val fallbackBuilder = Typeface.CustomFallbackBuilder(
                android.graphics.fonts.FontFamily.Builder(font).build()
            )

            return fallbackBuilder.build()

        } catch (e: Exception) {
            Log.e(TAG, "Font.Builder failed for axes", e)

            if (axisValues == null || axisValues.isEmpty()) {
                return loadTypefaceFromFile(fontFile.absolutePath)
            }

            return null
        }
    }

    private fun loadTypefaceFromFile(fontPath: String): Typeface? {
        return try {
            Typeface.createFromFile(fontPath)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading font from file: $fontPath", e)
            null
        }
    }

    fun preloadFonts(fontPaths: List<String?>?) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return
        }

        Thread({
            var loadedCount = 0
            for (fontPath in fontPaths) {
                if (getIfCached(fontPath) == null) {
                    val typeface = getTypeface(fontPath)
                    if (typeface != null) {
                        loadedCount++
                    }
                }
            }

            Log.d(TAG, "Preloaded $loadedCount system fonts into memory")
        }, "SystemFontCache-BackgroundPreload").start()
    }

    companion object {
        private const val TAG = "SystemFontCache"

        @Volatile
        private var instance: SystemFontCache? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): SystemFontCache {
            var current = instance
            if (current == null) {
                current = SystemFontCache()
                instance = current
            }
            return current
        }
    }
}
