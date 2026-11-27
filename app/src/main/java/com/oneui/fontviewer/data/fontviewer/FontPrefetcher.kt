package com.oneui.fontviewer.data.fontviewer

import android.content.Context
import android.graphics.Typeface
import android.util.Log

import com.oneui.fontviewer.data.common.variable.VariableFontAxisExtractor
import com.oneui.fontviewer.data.common.variable.VariableTypefaceFactory
import com.oneui.fontviewer.data.localfont.LocalFontCache
import com.oneui.fontviewer.data.systemfont.SystemFontCache

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * يجهّز الخط مسبقاً قبل أن تُرسم شاشة عارض الخط، حتى لا يظهر الخط الافتراضي ولا تتأخر بطاقات المحاور.
 *
 * التجهيز المسبق (Prefetch): يُستدعى [prefetch] من MainActivity لحظة اختيار الخط من القائمة، قبل بدء فتح الشاشة.
 * يحلّل الخط (هل هو متغير، المحاور، القيم الافتراضية) ويبني Typeface في خيط خلفي، أثناء تشغيل أنيميشن فتح الشاشة،
 * فتكون النتيجة جاهزة في ذاكرة مؤقتة صغيرة عند إنشاء FontViewerFragment، فيأخذها عبر [take].
 * اذا لم تكن جاهزة في الوقت المناسب (أو فشل التجهيز لأي سبب) يعمل مسار التحميل الخلفي في الـ Fragment كما هو دون أي تغيير.
 *
 * التطبيق الفوري من الكاش: [getCachedTypeface] يعيد Typeface الخط من LocalFontCache أو SystemFontCache
 * إن كان محمّلاً مسبقاً، لاستخدامه فوراً بدل انتظار الخيط الخلفي.
 */
object FontPrefetcher {

    private const val TAG = "FontPrefetcher"

    private const val CACHE_MAX_ENTRIES = 3

    // القيم الافتراضية الاحتياطية لكل محور، تُستخدم فقط اذا تعذّرت قراءة القيمة الافتراضية الفعلية
    // من جدول fvar الخاص بالخط نفسه (تطابق AXIS_FALLBACK_DEFAULTS في FontViewerFragment)
    private const val DEFAULT_FONT_WEIGHT = 400f
    private val AXIS_FALLBACK_DEFAULTS: MutableMap<String, Float> = HashMap()

    init {
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_WGHT] = DEFAULT_FONT_WEIGHT
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_WDTH] = 100f
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_ITAL] = 0f
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_GRAD] = 0f
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_ROND] = 0f
        AXIS_FALLBACK_DEFAULTS[VariableFontAxisExtractor.AXIS_MONO] = 0f
    }

    private val sCache: MutableMap<String, PrefetchedFont> = LinkedHashMap()
    private val sExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        val thread = Thread(runnable, "FontViewer-Prefetch")
        thread.isDaemon = true
        thread
    }

    /** نتيجة تجهيز مسبق لخط واحد: كل ما يحتاجه الـ Fragment لعرضه فوراً. */
    class PrefetchedFont(
        @JvmField val isVariable: Boolean,
        @JvmField val axisInstancesMap: Map<String, List<VariableFontAxisExtractor.VariableInstance>>,
        @JvmField val axisDefaults: Map<String, Float>,
        @JvmField val typeface: Typeface
    )

    /**
     * تُستدعى من MainActivity قبل startActivity. لا تحجب الخيط الرئيسي: كل العمل في خيط خلفي مستقل.
     * تتجاهل الخطوط القادمة من Uri (تُنسخ أولاً ولها مسارها الخاص).
     */
    fun prefetch(context: Context?, path: String?, ttcIndex: Int, systemFont: Boolean) {
        if (context == null || path == null || path.isEmpty() || path.startsWith("content://")) {
            return
        }

        val taskContext: Context = context
        val localeTags = localeTagsOf(context)

        sExecutor.execute {
            try {
                val fontFile = File(path)
                if (!fontFile.exists()) {
                    return@execute
                }

                // هذا المنطق يطابق loadFontFromPathWithAxes عند resetToDefaults = true (فتح خط جديد من القائمة)
                val isVar = VariableFontAxisExtractor.isVariableFont(fontFile, ttcIndex)

                val axisInstancesMap: MutableMap<String, List<VariableFontAxisExtractor.VariableInstance>> = LinkedHashMap()
                val axisDefaults: MutableMap<String, Float> = LinkedHashMap()

                if (isVar) {
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_WGHT] = VariableFontAxisExtractor.extractVariableInstances(taskContext, fontFile, ttcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_WDTH] = VariableFontAxisExtractor.extractWidthInstances(taskContext, fontFile, ttcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_ITAL] = VariableFontAxisExtractor.extractItalicInstances(taskContext, fontFile, ttcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_GRAD] = VariableFontAxisExtractor.extractGradeInstances(taskContext, fontFile, ttcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_ROND] = VariableFontAxisExtractor.extractRoundnessInstances(taskContext, fontFile, ttcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_MONO] = VariableFontAxisExtractor.extractMonoInstances(taskContext, fontFile, ttcIndex)

                    for ((axisTag, instances) in axisInstancesMap) {
                        if (instances.isEmpty()) {
                            continue
                        }

                        val fallbackBoxed = AXIS_FALLBACK_DEFAULTS[axisTag]
                        val fallback = fallbackBoxed ?: 0f
                        axisDefaults[axisTag] = VariableFontAxisExtractor.readAxisDefaultValue(fontFile, ttcIndex, axisTag, fallback)
                    }
                }

                val typeface: Typeface? = if (systemFont) {
                    SystemFontCache.getInstance().getTypefaceWithAxes(path, axisDefaults, ttcIndex)
                } else {
                    VariableTypefaceFactory.createTypefaceWithAxes(fontFile, axisDefaults, ttcIndex)
                }

                // أي فشل: لا نخزّن شيئاً، فيتولى مسار التحميل الخلفي القديم معالجة الخطأ وعرض رسالته كما كان
                if (typeface == null) {
                    return@execute
                }

                val key = buildKey(path, ttcIndex, systemFont, localeTags)
                synchronized(sCache) {
                    while (sCache.size >= CACHE_MAX_ENTRIES) {
                        val eldestKey = sCache.keys.iterator().next()
                        sCache.remove(eldestKey)
                    }
                    sCache[key] = PrefetchedFont(isVar, axisInstancesMap, axisDefaults, typeface)
                }
                Log.d(TAG, "Prefetch ready for: $path")
            } catch (e: Exception) {
                Log.w(TAG, "Prefetch failed for: $path", e)
            }
        }
    }

    /**
     * يأخذ نتيجة التجهيز المسبق لهذا الخط (ويزيلها من الذاكرة المؤقتة)، أو null اذا لم تكن جاهزة.
     */
    fun take(context: Context, path: String, ttcIndex: Int, systemFont: Boolean): PrefetchedFont? {
        val key = buildKey(path, ttcIndex, systemFont, localeTagsOf(context))
        synchronized(sCache) {
            return sCache.remove(key)
        }
    }

    /**
     * يعيد Typeface الخط من الذاكرة المؤقتة (LocalFontCache أو SystemFontCache) اذا كان محمّلاً مسبقاً، وإلا null،
     * دون أي قراءة ملفات أو تحميل. الكاشان يخزّنان الخط بقيم محاوره الافتراضية وبفهرس TTC = 0 فقط، وهذا مطابق لما يبنيه
     * التحميل الخلفي عند اختيار خط جديد (لأن كل محاوره تُعاد الى قيمها الافتراضية). لا نستخدم الكاش مع ttcIndex > 0
     * لأنه لا يميّز بين خطوط الملف الواحد.
     */
    fun getCachedTypeface(path: String, ttcIndex: Int, systemFont: Boolean): Typeface? {
        if (ttcIndex > 0) {
            return null
        }
        return if (systemFont)
            SystemFontCache.getInstance().getIfCached(path)
        else
            LocalFontCache.getInstance().getIfCached(path)
    }

    /**
     * مفتاح التجهيز المسبق: المسار + فهرس TTC + نوع الخط + تاريخ تعديل الملف وحجمه (حتى لا نستخدم نتيجة قديمة اذا تغيّر الملف)
     * + لغة التطبيق (لأن أسماء قيم المحاور تُقرأ من strings.xml حسب اللغة).
     */
    private fun buildKey(path: String, ttcIndex: Int, systemFont: Boolean, localeTags: String): String {
        val file = File(path)
        return path + "|" + ttcIndex + "|" + systemFont + "|" + file.lastModified() + "|" + file.length() + "|" + localeTags
    }

    private fun localeTagsOf(context: Context): String {
        return context.resources.configuration.locales.toLanguageTags()
    }
}
