package com.oneui.fontviewer.data.localfont

import android.content.Context
import android.graphics.Typeface
import android.util.Log

import com.oneui.fontviewer.data.common.room.FontDatabase

import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

class LocalFontCache private constructor() {

    private val memoryCache: ConcurrentHashMap<String, Typeface?> = ConcurrentHashMap(150)
    private lateinit var context: Context
    private lateinit var database: FontDatabase

    @Volatile
    private var isInitialized = false

    private val fontLoaderExecutor: java.util.concurrent.ExecutorService =
        java.util.concurrent.Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    fun initialize(context: Context) {
        if (isInitialized) {
            return
        }

        this.context = context.applicationContext
        this.database = FontDatabase.getInstance(this.context)

        Log.d(TAG, "LocalFontCache initializing with Room Database")

        preloadCachedFontsFromDatabase()

        isInitialized = true
    }

    private fun preloadCachedFontsFromDatabase() {
        Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            runPreload()
        }, "LocalFontCache-Preloader").start()
    }

    private fun runPreload() {
        try {
            val startTime = System.currentTimeMillis()

            val cachedFonts = database.fontDao().getCachedFonts()?.toMutableList()

            if (cachedFonts == null || cachedFonts.isEmpty()) {
                Log.d(TAG, "No cached fonts found in database")
                return
            }

            cachedFonts.removeIf { it.isSystemFont }

            if (cachedFonts.isEmpty()) {
                Log.d(TAG, "No cached local fonts found")
                return
            }

            Log.d(TAG, "Found " + cachedFonts.size + " cached local fonts in database")

            var loadedCount = 0

            cachedFonts.sortWith { f1, f2 ->
                val countCompare = Integer.compare(f2.accessCount, f1.accessCount)
                if (countCompare != 0) return@sortWith countCompare

                java.lang.Long.compare(f2.lastAccessTime, f1.lastAccessTime)
            }

            for (font in cachedFonts) {
                val path = font.path

                val typeface = loadTypefaceFromFile(path)

                if (typeface != null) {
                    memoryCache[path] = typeface
                    loadedCount++

                    if (loadedCount % 10 == 0) {
                        Log.d(TAG, "Preloaded $loadedCount fonts...")
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "★ Auto-preloaded " + loadedCount + " fonts in " + duration + "ms")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload cached fonts from database", e)
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

        return memoryCache.computeIfAbsent(fontPath) { path -> loadTypefaceFromFile(path) }
    }

    private fun loadTypefaceFromFile(fontPath: String): Typeface? {
        try {
            val fontFile = File(fontPath)
            if (!fontFile.exists() || !fontFile.canRead()) {
                Log.w(TAG, "Font file does not exist or cannot be read: $fontPath")
                return null
            }

            return loadTypefaceUsingBuilder(fontFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading font from file: $fontPath", e)
            return null
        }
    }

    private fun loadTypefaceUsingBuilder(fontFile: File): Typeface? {
        try {
            val fontBuilder = android.graphics.fonts.Font.Builder(fontFile)

            val font = fontBuilder.build()

            val fallbackBuilder = Typeface.CustomFallbackBuilder(
                android.graphics.fonts.FontFamily.Builder(font).build()
            )

            return fallbackBuilder.build()

        } catch (e: Exception) {
            Log.e(TAG, "Font.Builder failed, falling back to createFromFile", e)
            return Typeface.createFromFile(fontFile)
        }
    }

    fun preloadFonts(fontPaths: List<String>?) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return
        }

        for (fontPath in fontPaths) {
            fontLoaderExecutor.execute {
                if (getIfCached(fontPath) == null) {
                    getTypeface(fontPath)
                }
            }
        }
    }

    /**
     * يحمّل كل الخطوط المُعطاة داخل الذاكرة المؤقتة بشكل متزامن (Blocking)
     * قبل أن يعود للمستدعي. يجب استدعاؤها من خيط خلفي فقط، لأنها تنتظر
     * انتهاء التحميل الفعلي لكل خط. الهدف: ضمان أن القائمة لا تُعرض على
     * المستخدم إلا وكل معاينات خطوطها جاهزة، لتفادي وميض التبديل.
     */
    fun preloadFontsBlocking(fontPaths: List<String>?) {
        if (fontPaths == null || fontPaths.isEmpty()) {
            return
        }

        val futures: MutableList<java.util.concurrent.Future<*>> = ArrayList()
        for (fontPath in fontPaths) {
            if (getIfCached(fontPath) != null) {
                continue
            }
            futures.add(fontLoaderExecutor.submit(Callable { getTypeface(fontPath) }))
        }

        for (future in futures) {
            try {
                future.get()
            } catch (e: Exception) {
                Log.w(TAG, "preloadFontsBlocking: failed while waiting for a font to load", e)
            }
        }
    }

    companion object {
        private const val TAG = "LocalFontCache"
        private var instance: LocalFontCache? = null

        @Synchronized
        fun getInstance(): LocalFontCache {
            if (instance == null) {
                instance = LocalFontCache()
            }
            return instance!!
        }
    }

}
