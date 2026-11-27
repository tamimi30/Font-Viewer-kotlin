package com.oneui.fontviewer.data

import android.content.Context
import android.util.Log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * AppDataStore - the single app-wide DataStore.
 * Holds the user settings and the persisted state of each feature
 * (local/system/favorite lists, sorting, font viewer).
 * All SharedPreferences removed and replaced with DataStore.
 * Note: the persisted file name stays "settings" (DATASTORE_NAME) so existing user data is kept.
 */
class AppDataStore private constructor(context: Context) {

    /**
     * نطاق خلفي واحد للتطبيق كله خاص بالإعدادات: يعمل على Dispatchers.IO ولا يُلغى أبداً (Singleton).
     * يستخدمه DataStore نفسه، ويستخدمه مدراء التفضيلات للكتابات التي لا ننتظر نتيجتها (fire-and-forget).
     */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // نفس اسم الملف السابق (settings.preferences_pb) كي تبقى بيانات المستخدمين الحالية كما هي
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    // ★ نسخة محفوظة في الذاكرة من آخر إعدادات قُرئت من القرص ★
    // السبب: أجزاء كثيرة من التطبيق تقرأ القيمة بشكل متزامن على الخيط الرئيسي أثناء الإقلاع
    // (Application وSortManager ومدراء التفضيلات والمحوّلات وattachBaseContext ...)، وكل قراءة منها
    // كانت تمر عبر طابور DataStore وخيوط الـ IO ويُبقي الخيط الرئيسي ينتظر. بعد إعادة تشغيل الجهاز يكون
    // القرص والمعالج مشغولين بإقلاع النظام، فتتراكم هذه الانتظارات المتتالية حتى تتجاوز 5 ثوانٍ ويظهر
    // "التطبيق لا يستجيب" (ANR). الآن يُقرأ الملف مرة واحدة فقط في الخلفية عند إنشاء الكائن،
    // وتعود كل القراءات التالية فورياً من الذاكرة، مع بقاء كل Flow يتلقى التحديثات كما كان.
    //
    // MutableStateFlow يتجاهل تلقائياً القيمة المطابقة تماماً للحالية (كما كان publishSnapshot يفعل)،
    // و null تعني "لم تصل القراءة الأولى من القرص بعد".
    private val snapshot = MutableStateFlow<Preferences?>(null)

    // Core keys
    private val KEY_LANGUAGE_MODE: Preferences.Key<Int> = intPreferencesKey("language_mode")
    private val KEY_THEME_MODE: Preferences.Key<Int> = intPreferencesKey("theme_mode")
    private val KEY_THEME_AUTO: Preferences.Key<Boolean> = booleanPreferencesKey("theme_auto")
    private val KEY_FONT_PREVIEW_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("font_preview_enabled")
    private val KEY_TRANSLATION_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("translation_enabled")
    private val KEY_PREVIEW_TEXT: Preferences.Key<String> = stringPreferencesKey("preview_text")
    private val KEY_LAST_OPENED_FONT_PATH: Preferences.Key<String> = stringPreferencesKey("last_opened_font_path")
    private val KEY_FOLDER_PATH: Preferences.Key<String> = stringPreferencesKey("folder_path")



    // Local fonts keys (Local fonts)
    private val KEY_SORT_TYPE: Preferences.Key<String> = stringPreferencesKey("sort_type")
    private val KEY_SORT_ASCENDING: Preferences.Key<Boolean> = booleanPreferencesKey("sort_ascending")
    private val KEY_LAST_OPENED_SYSTEM_FONT_PATH: Preferences.Key<String> = stringPreferencesKey("last_opened_system_font_path")

    // ★ System fonts sort keys — منفصلة تماماً عن مفاتيح المجلد المحلي ★
    private val KEY_SYSTEM_SORT_TYPE: Preferences.Key<String> = stringPreferencesKey("system_sort_type")
    private val KEY_SYSTEM_SORT_ASCENDING: Preferences.Key<Boolean> = booleanPreferencesKey("system_sort_ascending")

    // ★ Favorites sort keys — منفصلة تماماً لتجنب أي تعارض مع القوائم الأخرى ★
    private val KEY_FAVORITES_SORT_TYPE: Preferences.Key<String> = stringPreferencesKey("favorites_sort_type")
    private val KEY_FAVORITES_SORT_ASCENDING: Preferences.Key<Boolean> = booleanPreferencesKey("favorites_sort_ascending")

    // ★ Favorites last opened font key — منفصل تماماً عن مفتاح الخطوط المحلية ★
    private val KEY_LAST_OPENED_FAVORITE_FONT_PATH: Preferences.Key<String> = stringPreferencesKey("last_opened_favorite_font_path")

    // Font viewer keys
    private val KEY_VIEWER_FONT_PATH: Preferences.Key<String> = stringPreferencesKey("viewer_font_path")
    private val KEY_VIEWER_FILE_NAME: Preferences.Key<String> = stringPreferencesKey("viewer_file_name")
    private val KEY_VIEWER_REAL_NAME: Preferences.Key<String> = stringPreferencesKey("viewer_real_name")
    private val KEY_VIEWER_FONT_WEIGHT: Preferences.Key<Float> = floatPreferencesKey("viewer_font_weight")

    // ★ مفاتيح محاور الخط المتغير الإضافية: العرض، التدرج، الاستدارة، الميل، والتباعد الأحادي ★
    private val KEY_VIEWER_FONT_WIDTH: Preferences.Key<Float> = floatPreferencesKey("viewer_font_width")
    private val KEY_VIEWER_FONT_GRADE: Preferences.Key<Float> = floatPreferencesKey("viewer_font_grade")
    private val KEY_VIEWER_FONT_ROUNDNESS: Preferences.Key<Float> = floatPreferencesKey("viewer_font_roundness")
    private val KEY_VIEWER_FONT_ITALIC_AXIS: Preferences.Key<Float> = floatPreferencesKey("viewer_font_italic_axis")
    private val KEY_VIEWER_FONT_MONO: Preferences.Key<Float> = floatPreferencesKey("viewer_font_mono")

    init {
        startSnapshotSync()
    }

    /**
     * يبدأ تجميعاً واحداً دائماً على DataStore يُغذّي النسخة المحفوظة في الذاكرة.
     * القراءة الفعلية من القرص تحدث على نطاق DataStore الخاص به، فلا تحجب الخيط الذي أنشأ الكائن.
     */
    private fun startSnapshotSync() {
        scope.launch {
            dataStore.data
                .catch { error ->
                    // تعذّرت قراءة الملف (تلف مثلاً): نمرر إعدادات فارغة ليأخذ كل قارئ قيمه الافتراضية
                    // بدل أن ينتظر للأبد، وهذا نفس ما كان يحدث سابقاً حين يلتقط المستدعي الاستثناء
                    Log.e(TAG, "Failed to read settings, falling back to defaults", error)
                    if (snapshot.value == null) {
                        snapshot.value = emptyPreferences()
                    }
                }
                .collect { prefs -> snapshot.value = prefs }
        }
    }

    /**
     * مصدر البيانات الذي تقرأ منه كل خصائص القراءة: النسخة المحفوظة في الذاكرة بدل بدء قراءة جديدة من DataStore.
     * أول قارئ ينتظر فقط إلى أن تصل القراءة الأولى من القرص، وكل من بعده يحصل على القيمة فوراً.
     */
    private fun data(): Flow<Preferences> = snapshot.filterNotNull()

    /**
     * كل الكتابات تمر من هنا: بعد نجاح الحفظ نحدّث النسخة المحفوظة فوراً قبل أن يستلم المستدعي نتيجة النجاح،
     * فإذا قرأ المستدعي القيمة مباشرة بعد الحفظ (مثل applyTheme بعد setThemeMode) وجد القيمة الجديدة لا القديمة.
     */
    private suspend fun update(transform: (MutablePreferences) -> Unit): Preferences {
        val updated = dataStore.updateData { prefs ->
            prefs.toMutablePreferences().also(transform)
        }
        snapshot.value = updated
        return updated
    }

    /**
     * يشغّل كتابة في الخلفية دون انتظار نتيجتها (بديل subscribeOn(io).subscribe(onSuccess, onError)).
     * تُستدعى [onSuccess] و[onError] على خيط الـ IO.
     */
    fun launchWrite(
        onError: (Throwable) -> Unit,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                onError(e)
                return@launch
            }
            onSuccess()
        }
    }

    // ════════════════════════════════════════════════════════════
    // Language Mode
    // ════════════════════════════════════════════════════════════

    val languageMode: Flow<Int>
        get() = data().map { prefs ->
            prefs[KEY_LANGUAGE_MODE] ?: DEFAULT_LANGUAGE_MODE
        }

    suspend fun setLanguageMode(mode: Int): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_LANGUAGE_MODE] = mode
        }
    }

    // ════════════════════════════════════════════════════════════
    // Theme Mode
    // ════════════════════════════════════════════════════════════

    val themeMode: Flow<Int>
        get() = data().map { prefs ->
            prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
        }

    suspend fun setThemeMode(mode: Int): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_THEME_MODE] = mode
        }
    }

    val themeAuto: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_THEME_AUTO] ?: DEFAULT_THEME_AUTO
        }

    suspend fun setThemeAuto(auto: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_THEME_AUTO] = auto
        }
    }



    // ════════════════════════════════════════════════════════════
    // Font Preview
    // ════════════════════════════════════════════════════════════

    val fontPreviewEnabled: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_FONT_PREVIEW_ENABLED] ?: DEFAULT_FONT_PREVIEW_ENABLED
        }

    suspend fun setFontPreviewEnabled(enabled: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_FONT_PREVIEW_ENABLED] = enabled
        }
    }

    // ════════════════════════════════════════════════════════════
    // Translation
    // ════════════════════════════════════════════════════════════

    val translationEnabled: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_TRANSLATION_ENABLED] ?: DEFAULT_TRANSLATION_ENABLED
        }

    suspend fun setTranslationEnabled(enabled: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_TRANSLATION_ENABLED] = enabled
        }
    }

    // ════════════════════════════════════════════════════════════
    // Preview Text
    // ════════════════════════════════════════════════════════════

    val previewText: Flow<String>
        get() = data().map { prefs ->
            prefs[KEY_PREVIEW_TEXT] ?: DEFAULT_PREVIEW_TEXT
        }

    suspend fun setPreviewText(text: String): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_PREVIEW_TEXT] = text
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened Font (Local)
    // ════════════════════════════════════════════════════════════

    val lastOpenedFontPath: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_LAST_OPENED_FONT_PATH] }

    suspend fun setLastOpenedFontPath(path: String?): Preferences {
        return update { mutablePrefs ->
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_FONT_PATH)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened Font (Favorites)
    // ════════════════════════════════════════════════════════════

    val lastOpenedFavoriteFontPath: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_LAST_OPENED_FAVORITE_FONT_PATH] }

    suspend fun setLastOpenedFavoriteFontPath(path: String?): Preferences {
        return update { mutablePrefs ->
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_FAVORITE_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_FAVORITE_FONT_PATH)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Folder Path
    // ════════════════════════════════════════════════════════════

    val folderPath: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_FOLDER_PATH] }

    suspend fun setFolderPath(path: String?): Preferences {
        return update { mutablePrefs ->
            if (path != null) {
                mutablePrefs[KEY_FOLDER_PATH] = path
            } else {
                mutablePrefs.remove(KEY_FOLDER_PATH)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (Local Folder Fonts)
    // ════════════════════════════════════════════════════════════

    val sortType: Flow<String>
        get() = data().map { prefs ->
            prefs[KEY_SORT_TYPE] ?: "NAME"
        }

    suspend fun setSortType(type: String): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_SORT_TYPE] = type
        }
    }

    val sortAscending: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_SORT_ASCENDING] ?: true
        }

    suspend fun setSortAscending(ascending: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_SORT_ASCENDING] = ascending
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (System Fonts) ★ منفصلة تماماً لتجنب التجمد ★
    // ════════════════════════════════════════════════════════════

    val systemSortType: Flow<String>
        get() = data().map { prefs ->
            prefs[KEY_SYSTEM_SORT_TYPE] ?: "NAME"
        }

    suspend fun setSystemSortType(type: String): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_SYSTEM_SORT_TYPE] = type
        }
    }

    val systemSortAscending: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_SYSTEM_SORT_ASCENDING] ?: true
        }

    suspend fun setSystemSortAscending(ascending: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_SYSTEM_SORT_ASCENDING] = ascending
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (Favorites) ★ منفصلة تماماً لتجنب أي تعارض ★
    // ════════════════════════════════════════════════════════════

    val favoritesSortType: Flow<String>
        get() = data().map { prefs ->
            prefs[KEY_FAVORITES_SORT_TYPE] ?: "NAME"
        }

    suspend fun setFavoritesSortType(type: String): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_FAVORITES_SORT_TYPE] = type
        }
    }

    val favoritesSortAscending: Flow<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_FAVORITES_SORT_ASCENDING] ?: true
        }

    suspend fun setFavoritesSortAscending(ascending: Boolean): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_FAVORITES_SORT_ASCENDING] = ascending
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened System Font
    // ════════════════════════════════════════════════════════════

    val lastOpenedSystemFontPath: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_LAST_OPENED_SYSTEM_FONT_PATH] }

    suspend fun setLastOpenedSystemFontPath(path: String?): Preferences {
        return update { mutablePrefs ->
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_SYSTEM_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_SYSTEM_FONT_PATH)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Last Viewed Font
    // ════════════════════════════════════════════════════════════

    suspend fun setLastViewedFont(path: String?, fileName: String?, realName: String?): Preferences {
        return update { mutablePrefs ->
            if (path != null) mutablePrefs[KEY_VIEWER_FONT_PATH] = path
            if (fileName != null) mutablePrefs[KEY_VIEWER_FILE_NAME] = fileName
            if (realName != null) {
                mutablePrefs[KEY_VIEWER_REAL_NAME] = realName
            } else {
                mutablePrefs.remove(KEY_VIEWER_REAL_NAME)
            }
        }
    }

    suspend fun clearLastViewedFont(): Preferences {
        return update { mutablePrefs ->
            mutablePrefs.remove(KEY_VIEWER_FONT_PATH)
            mutablePrefs.remove(KEY_VIEWER_FILE_NAME)
            mutablePrefs.remove(KEY_VIEWER_REAL_NAME)
        }
    }

    val viewerFontPath: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_VIEWER_FONT_PATH] }

    val viewerFileName: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_VIEWER_FILE_NAME] }

    val viewerRealName: Flow<String?>
        get() = data().map { prefs -> prefs[KEY_VIEWER_REAL_NAME] }


    // ════════════════════════════════════════════════════════════
    // Font Viewer - Font Weight
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontWeight(weight: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_WEIGHT] = weight
        }
    }

    val viewerFontWeight: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_WEIGHT] ?: DEFAULT_VIEWER_FONT_WEIGHT
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Width Axis (wdth)
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontWidth(width: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_WIDTH] = width
        }
    }

    val viewerFontWidth: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_WIDTH] ?: DEFAULT_VIEWER_FONT_WIDTH
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Grade Axis (GRAD)
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontGrade(grade: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_GRADE] = grade
        }
    }

    val viewerFontGrade: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_GRADE] ?: DEFAULT_VIEWER_FONT_GRADE
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Roundness Axis (ROND)
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontRoundness(roundness: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_ROUNDNESS] = roundness
        }
    }

    val viewerFontRoundness: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_ROUNDNESS] ?: DEFAULT_VIEWER_FONT_ROUNDNESS
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Italic Axis (ital)
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontItalicAxis(italValue: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_ITALIC_AXIS] = italValue
        }
    }

    val viewerFontItalicAxis: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_ITALIC_AXIS] ?: DEFAULT_VIEWER_FONT_ITALIC_AXIS
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Mono Axis (MONO)
    // ════════════════════════════════════════════════════════════

    suspend fun setViewerFontMono(monoValue: Float): Preferences {
        return update { mutablePrefs ->
            mutablePrefs[KEY_VIEWER_FONT_MONO] = monoValue
        }
    }

    val viewerFontMono: Flow<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_MONO] ?: DEFAULT_VIEWER_FONT_MONO
        }

    companion object {
        private const val TAG = "AppDataStore"
        private const val DATASTORE_NAME = "settings"

        @Volatile
        private var INSTANCE: AppDataStore? = null

        // Default values
        const val DEFAULT_LANGUAGE_MODE = 0
        const val DEFAULT_THEME_MODE = 0
        const val DEFAULT_THEME_AUTO = true
        const val DEFAULT_FONT_PREVIEW_ENABLED = true
        const val DEFAULT_TRANSLATION_ENABLED = false
        const val DEFAULT_PREVIEW_TEXT =
            "The quick brown fox jumps over the lazy dog.\n\n" +
            "0123456789"
        const val DEFAULT_VIEWER_FONT_WEIGHT = 400f

        // ★ القيم الافتراضية لمحاور الخط المتغير الإضافية ★
        const val DEFAULT_VIEWER_FONT_WIDTH = 100f
        const val DEFAULT_VIEWER_FONT_GRADE = 0f
        const val DEFAULT_VIEWER_FONT_ROUNDNESS = 0f
        const val DEFAULT_VIEWER_FONT_ITALIC_AXIS = 0f
        const val DEFAULT_VIEWER_FONT_MONO = 0f

        fun getInstance(context: Context): AppDataStore {
            var instance = INSTANCE
            if (instance == null) {
                synchronized(AppDataStore::class.java) {
                    instance = INSTANCE
                    if (instance == null) {
                        instance = AppDataStore(context)
                        INSTANCE = instance
                    }
                }
            }
            return instance!!
        }
    }
}
