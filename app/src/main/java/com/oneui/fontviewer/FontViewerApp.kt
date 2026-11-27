package com.oneui.fontviewer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log

import androidx.appcompat.app.AppCompatDelegate

import com.oneui.fontviewer.data.common.room.FontDatabase
import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.data.localfont.LocalFontRepository
import com.oneui.fontviewer.data.systemfont.SystemFontRepository
import com.oneui.fontviewer.data.localfont.LocalFontCache
import com.oneui.fontviewer.data.systemfont.SystemFontCache
import com.oneui.fontviewer.utils.CrashHandler

import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.concurrent.TimeUnit

/**
 * App - النسخة المحسّنة لحل مشكلة وميض الثيم
 * تم إصلاح مشكلة ظهور الثيم الخطأ لأجزاء من الثانية عند بدء التطبيق
 *
 * ═══════════════════════════════════════════════════════════════════
 * ★ الحل الكامل: قراءة إعدادات الثيم بشكل متزامن (Blocking) ★
 * ═══════════════════════════════════════════════════════════════════
 */
class FontViewerApp : Application() {

    private lateinit var appDataStore: AppDataStore

    override fun onCreate() {
        super.onCreate()
        sInstance = this

        // Initialize crash handler first
        CrashHandler.init(this)

        // ★★★ تهيئة DataStore هنا مباشرة (ليس في خيط خلفي) ★★★
        // السبب: نحتاجه فوراً لقراءة إعدادات الثيم
        // ملاحظة: قدّمناه على تهيئة الكاش، لأن إنشاء AppDataStore يبدأ قراءة ملف الإعدادات من القرص
        // في الخلفية فوراً، فيسبق ذلك أي عمل آخر يزاحمه على القرص والمعالج عند الإقلاع بعد إعادة تشغيل الجهاز
        initializeDataStore()

        // ★★★ الحل الرئيسي: تهيئة الكاش مبكراً جداً ★★★
        initializeCaches()

        // ★★★ تطبيق الثيم بشكل متزامن لمنع الوميض ★★★
        applyInitialTheme()

        // Initialize other components in background thread
        Thread {
            try {
                initializeDatabase()
                initializeRepositories()

                Log.d(TAG, "All components initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error during initialization", e)
            }
        }.start()

        setupActivityTracking()
    }

    /**
     * تهيئة الكاش للخطوط
     */
    private fun initializeCaches() {
        Thread({
            try {
                val startTime = System.currentTimeMillis()

                LocalFontCache.getInstance().initialize(this)
                Log.d(TAG, "✓ LocalFontCache initialized")

                SystemFontCache.getInstance().initialize(this)
                Log.d(TAG, "✓ SystemFontCache initialized")

                val duration = System.currentTimeMillis() - startTime
                Log.d(TAG, "★★★ Caches initialized in " + duration + "ms")

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize font caches", e)
            }
        }, "LocalFontCache-Initializer").start()
    }

    private fun initializeDatabase() {
        FontDatabase.getInstance(this)
        Log.d(TAG, "FontDatabase initialized")
    }

    private fun initializeDataStore() {
        if (!::appDataStore.isInitialized) {
            appDataStore = AppDataStore.getInstance(this)
            Log.d(TAG, "AppDataStore initialized")
        }
    }

    /**
     * تهيئة مستودعات البيانات مسبقاً في الخلفية
     * يضمن هذا أن الـ Singleton لكل مستودع جاهز في الذاكرة
     * قبل أن يحتاجه أي ViewModel، مما يُقلل العبء عند فتح الشاشات
     */
    private fun initializeRepositories() {
        LocalFontRepository.getInstance(this)
        Log.d(TAG, "LocalFontRepository initialized")

        SystemFontRepository.getInstance(this)
        Log.d(TAG, "SystemFontRepository initialized")
    }

    /**
     * ★★★ الحل الكامل لمشكلة الوميض ★★★
     *
     * تطبيق الثيم بشكل متزامن (Blocking) قبل رسم أي واجهة
     *
     * ملاحظة مهمة:
     * - استخدام blockingFirst() آمن هنا لأننا نقرأ قيمة واحدة صغيرة فقط
     * - العملية سريعة جداً (أقل من 10ms عادةً) ولن تسبب تجميد
     * - هذا يضمن تطبيق الثيم الصحيح قبل ظهور أي شاشة
     *
     * تحديث: التقدير "أقل من 10ms" لا يصح دائماً، فبعد إعادة تشغيل الجهاز قد تستغرق القراءة الأولى من القرص
     * ثواني، لذلك أُضيفت مهلة THEME_READ_TIMEOUT_MS: إن انتهت نعود لثيم النظام ثم نطبّق الثيم المحفوظ لاحقاً.
     */
    private fun applyInitialTheme() {
        try {
            // قراءة إعداد الثيم التلقائي بشكل متزامن
            val isAuto: Boolean? = appDataStore.themeAuto
                .timeout(THEME_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .blockingFirst()

            if (isAuto != null && isAuto) {
                // الثيم تلقائي - اتبع إعدادات النظام
                AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                Log.d(TAG, "✓ Theme set to FOLLOW_SYSTEM (Blocking Mode)")
            } else {
                // الثيم يدوي - اقرأ الوضع المحدد
                val themeMode: Int? = appDataStore.themeMode
                    .timeout(THEME_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .blockingFirst()

                if (themeMode != null && themeMode == 1) {
                    // وضع داكن
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_YES)
                    Log.d(TAG, "✓ Theme set to DARK (Blocking Mode)")
                } else {
                    // وضع فاتح (القيمة الافتراضية)
                    AppCompatDelegate.setDefaultNightMode(
                        AppCompatDelegate.MODE_NIGHT_NO)
                    Log.d(TAG, "✓ Theme set to LIGHT (Blocking Mode)")
                }
            }
        } catch (e: Exception) {
            // في حالة حدوث خطأ، استخدم إعدادات النظام كقيمة افتراضية آمنة
            Log.e(TAG, "Failed to apply initial theme synchronously", e)
            AppCompatDelegate.setDefaultNightMode(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

            // لم نقرأ الثيم المحفوظ في الوقت المناسب: ننتظره في خيط خلفي (دون حجب الخيط الرئيسي) ثم نطبّقه
            applySavedThemeWhenReady()
        }
    }

    /**
     * يقرأ الثيم المحفوظ في خيط خلفي ثم يطبّقه على الخيط الرئيسي. يُستدعى فقط في الحالة النادرة التي
     * تأخرت فيها القراءة المتزامنة في applyInitialTheme عن المهلة المحددة.
     */
    private fun applySavedThemeWhenReady() {
        Thread({
            try {
                val isAuto: Boolean? = appDataStore.themeAuto.blockingFirst()
                val themeMode: Int? = appDataStore.themeMode.blockingFirst()

                val nightMode: Int
                if (isAuto != null && isAuto) {
                    nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                } else if (themeMode != null && themeMode == 1) {
                    nightMode = AppCompatDelegate.MODE_NIGHT_YES
                } else {
                    nightMode = AppCompatDelegate.MODE_NIGHT_NO
                }

                Handler(Looper.getMainLooper()).post {
                    AppCompatDelegate.setDefaultNightMode(nightMode)
                    Log.d(TAG, "✓ Saved theme applied after delayed read")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply saved theme after delayed read", e)
            }
        }, "Theme-LateApply").start()
    }

    private fun setupActivityTracking() {
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activities.add(WeakReference(activity))
            }

            override fun onActivityDestroyed(activity: Activity) {
                for (i in activities.size - 1 downTo 0) {
                    val a = activities[i].get()
                    if (a == null || a === activity) {
                        activities.removeAt(i)
                    }
                }
            }

            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        })
    }

    /**
     * إعادة بناء جميع الأنشطة الحية
     * تُستخدم عند تغيير الثيم أو الخط أو الإعدادات العامة التي تتطلب
     * إعادة رسم كامل للواجهة
     */
    fun recreateAllActivities() {
        for (ref in ArrayList(activities)) {
            val act = ref.get()
            if (act != null && !act.isFinishing) {
                act.recreate()
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "Low memory warning received")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application terminated")
    }

    companion object {

        private const val TAG = "App"

        // أقصى مدة ننتظرها قراءة الثيم على الخيط الرئيسي أثناء onCreate.
        // بعد إعادة تشغيل الجهاز قد يتأخر القرص والخيوط بسبب إقلاع النظام، ولا يجوز أن يبقى الخيط الرئيسي
        // محجوباً بلا حد (وإلا ظهر ANR). عند انتهاء المهلة نبدأ بثيم النظام ثم نطبّق الثيم المحفوظ فور وصوله.
        private const val THEME_READ_TIMEOUT_MS = 1500L

        private var sInstance: FontViewerApp? = null
        private val activities: MutableList<WeakReference<Activity>> = ArrayList()

        // NOTE(kotlin-migration): Java returned a possibly-null static; exposed as non-null (set in onCreate before any use)
        fun getInstance(): FontViewerApp {
            return sInstance!!
        }
    }
}
