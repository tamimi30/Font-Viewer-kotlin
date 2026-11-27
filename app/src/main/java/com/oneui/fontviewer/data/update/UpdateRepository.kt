package com.oneui.fontviewer.data.update

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.oneui.fontviewer.BuildConfig
import com.oneui.fontviewer.data.update.model.InstallSource
import com.oneui.fontviewer.data.update.model.UpdateCheckState
import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.data.update.source.GalaxyStoreUpdateSource
import com.oneui.fontviewer.data.update.source.GitHubUpdateSource
import com.oneui.fontviewer.data.update.source.PlayStoreUpdateSource
import com.oneui.fontviewer.data.update.source.UpdateSource

import java.util.Optional

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * المصدر الوحيد لحالة التحديث في التطبيق.
 *
 * ما يعتمد عليه كل جزء من الواجهة:
 *  - مؤشر "N / ج" البرتقالي على أيقونة الدرج وأيقونة الإعدادات، وبطاقة الإعدادات: [availableUpdate]
 *  - صفحة "حول التطبيق" (مؤشر التحميل والنتيجة): [checkState]
 *
 * سلوك صفحة "حول التطبيق" (مطابق لتطبيقات سامسونج):
 *  - أول فتح لها داخل جلسة الإعدادات يتحقق من التحديث ويعرض مؤشر التحميل.
 *  - الفتح التالي في نفس الجلسة يعرض النتيجة المحفوظة فوراً بدون مؤشر تحميل.
 *  - جلسة الإعدادات تبدأ عند فتح الإعدادات وتنتهي بالخروج منها، فإذا دخلت الإعدادات
 *    مرة ثانية يعود التحقق مع مؤشر التحميل. (تغيير اللغة أو الثيم لا ينهي الجلسة.)
 *
 * مصدر معرفة التحديث يتبع مكان تثبيت التطبيق: Play أو Galaxy Store أو GitHub (انظر createUpdateSource).
 *
 * لا يوجد أي انتظار زمني (مثل 24 ساعة) بين عمليات التحقق: يتم التحقق عند كل فتح للتطبيق
 * وعند أول فتح لصفحة "حول التطبيق" في كل جلسة إعدادات.
 *
 * جميع الدوال العامة تُستدعى من الخيط الرئيسي فقط.
 */
class UpdateRepository private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val preferences: UpdatePreferenceManager = UpdatePreferenceManager(appContext)
    private val updateSource: UpdateSource = createUpdateSource()

    // يعمل على الخيط الرئيسي (جميع الدوال العامة تُستدعى منه)، والشبكة تُنفَّذ داخل withContext(Dispatchers.IO).
    // المستودع Singleton على مستوى التطبيق فالنطاق لا يُلغى أبداً.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // null = لا يتوجد تحديث متوفر
    private val _availableUpdate: MutableLiveData<UpdateInfo?> = MutableLiveData(loadStoredUpdate())
    private val _checkState: MutableLiveData<UpdateCheckState> =
        MutableLiveData(UpdateCheckState.IDLE)

    private var checkInFlight = false

    // طُلب التحقق من صفحة "حول التطبيق" وننتظر نتيجته
    private var aboutCheckRequested = false

    // تم التحقق من صفحة "حول التطبيق" مرة داخل جلسة الإعدادات الحالية
    private var settingsSessionChecked = false

    // ضغط المستخدم "ليس الآن" على بطاقة الإعدادات داخل الجلسة الحالية
    private var updateCardDismissed = false

    /**
     * آخر تحديث محفوظ. إذا كانت النسخة المثبتة الآن مساوية له أو أحدث (أي تم التحديث)
     * نمسحه، كي لا يظهر المؤشر لتحديث مثبّت أصلاً.
     */
    private fun loadStoredUpdate(): UpdateInfo? {
        val stored = preferences.loadAvailableUpdate()
        if (stored == null) {
            return null
        }
        if (!stored.isNewerThan(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())) {
            preferences.clearAvailableUpdate()
            return null
        }
        return stored
    }

    /**
     * مصدر معرفة التحديث يتبع مكان تثبيت التطبيق (مثل وجهة زر التحديث):
     * Play ← Play In-App Updates، و Galaxy Store ← خادم Galaxy Store، وغير ذلك ← GitHub.
     */
    private fun createUpdateSource(): UpdateSource {
        return when (InstallSourceResolver.resolve(appContext)) {
            InstallSource.GOOGLE_PLAY -> PlayStoreUpdateSource(appContext)
            InstallSource.GALAXY_STORE -> GalaxyStoreUpdateSource(appContext)
            else -> GitHubUpdateSource(BuildConfig.UPDATE_INCLUDE_PRERELEASES)
        }
    }

    // ───────────────────────── الحالة المراقَبة ─────────────────────────

    /** التحديث المتوفر حالياً، أو null. يعتمد عليه المؤشر البرتقالي وبطاقة الإعدادات. */
    val availableUpdate: LiveData<UpdateInfo?>
        get() = _availableUpdate

    /** حالة التحقق التي تعرضها صفحة "حول التطبيق". */
    val checkState: LiveData<UpdateCheckState>
        get() = _checkState

    val availableUpdateNow: UpdateInfo?
        get() = _availableUpdate.value

    val isUpdateAvailable: Boolean
        get() = _availableUpdate.value != null

    // ───────────────────────── التحقق ─────────────────────────

    /**
     * تحقق صامت عند فتح التطبيق: يحدّث المؤشر والبطاقة فقط، ولا يؤثر على مؤشر التحميل
     * في صفحة "حول التطبيق".
     */
    fun checkForUpdateOnLaunch() {
        startCheck()
    }

    /**
     * تطلبه صفحة "حول التطبيق" عند فتحها. يبدأ التحقق مع مؤشر تحميل فقط إذا لم يتم
     * التحقق بعد في جلسة الإعدادات الحالية، وإلا تبقى النتيجة المحفوظة معروضة كما هي.
     */
    fun requestAboutPageCheck() {
        if (settingsSessionChecked) {
            return
        }
        beginAboutCheck()
    }

    /** زر "إعادة المحاولة" في صفحة "حول التطبيق": تحقق إجباري جديد. */
    fun retryCheck() {
        beginAboutCheck()
    }

    private fun beginAboutCheck() {
        aboutCheckRequested = true
        _checkState.value = UpdateCheckState.CHECKING
        startCheck()
    }

    private fun startCheck() {
        if (checkInFlight) {
            // يوجد تحقق جارٍ بالفعل، ستصل نتيجته إلى جميع المهتمين
            return
        }

        if (!isNetworkAvailable()) {
            finishCheck(UpdateCheckState.NO_CONNECTION)
            return
        }

        checkInFlight = true
        scope.launch {
            val result: Optional<UpdateInfo> = try {
                withContext(Dispatchers.IO) { updateSource.checkForUpdate() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onCheckFailed(e)
                return@launch
            }
            onCheckSucceeded(result)
        }
    }

    private fun onCheckSucceeded(availableUpdate: Optional<UpdateInfo>) {
        checkInFlight = false

        // المصدر يرجع التحديث فقط إذا كان أحدث من النسخة المثبتة
        val update: UpdateInfo? = availableUpdate.orElse(null)

        applyAvailableUpdate(update)
        finishCheck(
            if (update != null)
                UpdateCheckState.UPDATE_AVAILABLE
            else
                UpdateCheckState.UP_TO_DATE
        )
    }

    private fun onCheckFailed(error: Throwable) {
        checkInFlight = false
        Log.w(TAG, "Update check failed: " + error.message)

        // عند فشل التحقق لا نمس المؤشر الحالي: التحديث الذي عرفناه سابقاً يبقى ظاهراً
        finishCheck(UpdateCheckState.NO_CONNECTION)
    }

    private fun applyAvailableUpdate(update: UpdateInfo?) {
        val current = _availableUpdate.value
        val unchanged = if (current == null)
            update == null
        else
            update != null
                    && current.versionName == update.versionName
                    && current.versionCode == update.versionCode

        if (update != null) {
            preferences.saveAvailableUpdate(update)
        } else {
            preferences.clearAvailableUpdate()
        }

        if (!unchanged) {
            _availableUpdate.value = update
        }
    }

    private fun finishCheck(state: UpdateCheckState) {
        _checkState.value = state
        if (aboutCheckRequested) {
            aboutCheckRequested = false
            settingsSessionChecked = true
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val connectivityManager =
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            if (connectivityManager == null) {
                return false
            }
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error checking network availability: " + e.message)
            return false
        }
    }

    // ───────────────────────── جلسة الإعدادات ─────────────────────────

    /**
     * تُستدعى عند فتح شاشة الإعدادات من جديد (وليس عند إعادة بنائها بسبب تغيير اللغة أو الثيم).
     * تعيد البطاقة، وتجعل أول فتح لصفحة "حول التطبيق" يتحقق من التحديث مع مؤشر تحميل.
     */
    fun beginSettingsSession() {
        settingsSessionChecked = false
        aboutCheckRequested = false
        updateCardDismissed = false
    }

    val isUpdateCardDismissed: Boolean
        get() = updateCardDismissed

    /** زر "ليس الآن": تختفي البطاقة حتى يخرج المستخدم من الإعدادات ويدخلها مرة أخرى. */
    fun dismissUpdateCard() {
        updateCardDismissed = true
    }

    companion object {
        private const val TAG = "UpdateRepository"

        @Volatile
        private var sInstance: UpdateRepository? = null

        @JvmStatic
        fun getInstance(context: Context): UpdateRepository {
            var instance = sInstance
            if (instance == null) {
                synchronized(UpdateRepository::class.java) {
                    instance = sInstance
                    if (instance == null) {
                        instance = UpdateRepository(context)
                        sInstance = instance
                    }
                }
            }
            return instance!!
        }
    }
}
