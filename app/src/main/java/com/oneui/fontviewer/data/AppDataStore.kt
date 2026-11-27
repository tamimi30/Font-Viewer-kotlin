package com.oneui.fontviewer.data

import android.content.Context
import android.util.Log

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder
import androidx.datastore.rxjava3.RxDataStore

import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.processors.BehaviorProcessor
import io.reactivex.rxjava3.processors.FlowableProcessor

/**
 * AppDataStore - the single app-wide DataStore.
 * Holds the user settings and the persisted state of each feature
 * (local/system/favorite lists, sorting, font viewer).
 * All SharedPreferences removed and replaced with DataStore.
 * Note: the persisted file name stays "settings" (DATASTORE_NAME) so existing user data is kept.
 */
class AppDataStore private constructor(context: Context) {

    private val dataStore: RxDataStore<Preferences> = RxPreferenceDataStoreBuilder(
        context.applicationContext,
        DATASTORE_NAME
    ).build()

    // ★ نسخة محفوظة في الذاكرة من آخر إعدادات قُرئت من القرص ★
    // السبب: أجزاء كثيرة من التطبيق تستدعي blockingFirst() على الخيط الرئيسي أثناء الإقلاع
    // (Application وSortManager ومدراء التفضيلات والمحوّلات وattachBaseContext ...)، وكل استدعاء منها
    // كان يمر عبر طابور DataStore وخيوط الـ IO ويُبقي الخيط الرئيسي ينتظر. بعد إعادة تشغيل الجهاز يكون
    // القرص والمعالج مشغولين بإقلاع النظام، فتتراكم هذه الانتظارات المتتالية حتى تتجاوز 5 ثوانٍ ويظهر
    // "التطبيق لا يستجيب" (ANR). الآن يُقرأ الملف مرة واحدة فقط في الخلفية عند إنشاء الكائن،
    // وتعود كل القراءات التالية فورياً من الذاكرة، مع بقاء كل Flowable يتلقى التحديثات كما كان.
    private val snapshotSource: BehaviorProcessor<Preferences> = BehaviorProcessor.create()
    private val snapshot: FlowableProcessor<Preferences> = snapshotSource.toSerialized()

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
     * يبدأ اشتراكاً واحداً دائماً على DataStore يُغذّي النسخة المحفوظة في الذاكرة.
     * القراءة الفعلية من القرص تحدث على خيط DataStore الخاص به، فلا تحجب الخيط الذي أنشأ الكائن.
     */
    private fun startSnapshotSync() {
        dataStore.data().subscribe(
            { prefs -> publishSnapshot(prefs) },
            { error ->
                // تعذّرت قراءة الملف (تلف مثلاً): نمرر إعدادات فارغة ليأخذ كل قارئ قيمه الافتراضية
                // بدل أن ينتظر للأبد، وهذا نفس ما كان يحدث سابقاً حين يلتقط المستدعي الاستثناء
                Log.e(TAG, "Failed to read settings, falling back to defaults", error)
                if (!snapshotSource.hasValue()) {
                    // NOTE(kotlin-migration): Java used PreferencesFactory.createEmpty(); Kotlin equivalent is emptyPreferences()
                    snapshot.onNext(emptyPreferences())
                }
            }
        )
    }

    /**
     * ينشر الإعدادات الجديدة للنسخة المحفوظة، ويتجاهل القيمة المطابقة تماماً للحالية
     * (كما كان DataStore يفعل) حتى لا يتلقى المراقبون تحديثاً مكرراً لا تغيير فيه.
     */
    private fun publishSnapshot(prefs: Preferences) {
        val current = snapshotSource.value
        if (current == null || current != prefs) {
            snapshot.onNext(prefs)
        }
    }

    /**
     * مصدر البيانات الذي تقرأ منه كل دوال get...: النسخة المحفوظة في الذاكرة بدل بدء قراءة جديدة من DataStore.
     * أول قارئ ينتظر فقط إلى أن تصل القراءة الأولى من القرص، وكل من بعده يحصل على القيمة فوراً.
     */
    private fun data(): Flowable<Preferences> {
        return snapshot.onBackpressureLatest()
    }

    /**
     * كل الكتابات تمر من هنا: بعد نجاح الحفظ نحدّث النسخة المحفوظة فوراً قبل أن يستلم المستدعي نتيجة النجاح،
     * فإذا قرأ المستدعي القيمة مباشرة بعد الحفظ (مثل applyTheme بعد setThemeMode) وجد القيمة الجديدة لا القديمة.
     */
    private fun update(transform: Function<Preferences, Single<Preferences>>): Single<Preferences> {
        return dataStore.updateDataAsync(transform).doOnSuccess { prefs -> publishSnapshot(prefs) }
    }

    // ════════════════════════════════════════════════════════════
    // Language Mode
    // ════════════════════════════════════════════════════════════

    val languageMode: Flowable<Int>
        get() = data().map { prefs ->
            prefs[KEY_LANGUAGE_MODE] ?: DEFAULT_LANGUAGE_MODE
        }

    fun setLanguageMode(mode: Int): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_LANGUAGE_MODE] = mode
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Theme Mode
    // ════════════════════════════════════════════════════════════

    val themeMode: Flowable<Int>
        get() = data().map { prefs ->
            prefs[KEY_THEME_MODE] ?: DEFAULT_THEME_MODE
        }

    fun setThemeMode(mode: Int): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_THEME_MODE] = mode
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val themeAuto: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_THEME_AUTO] ?: DEFAULT_THEME_AUTO
        }

    fun setThemeAuto(auto: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_THEME_AUTO] = auto
            Single.just<Preferences>(mutablePrefs)
        }
    }



    // ════════════════════════════════════════════════════════════
    // Font Preview
    // ════════════════════════════════════════════════════════════

    val fontPreviewEnabled: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_FONT_PREVIEW_ENABLED] ?: DEFAULT_FONT_PREVIEW_ENABLED
        }

    fun setFontPreviewEnabled(enabled: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_FONT_PREVIEW_ENABLED] = enabled
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Translation
    // ════════════════════════════════════════════════════════════

    val translationEnabled: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_TRANSLATION_ENABLED] ?: DEFAULT_TRANSLATION_ENABLED
        }

    fun setTranslationEnabled(enabled: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_TRANSLATION_ENABLED] = enabled
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Preview Text
    // ════════════════════════════════════════════════════════════

    val previewText: Flowable<String>
        get() = data().map { prefs ->
            prefs[KEY_PREVIEW_TEXT] ?: DEFAULT_PREVIEW_TEXT
        }

    fun setPreviewText(text: String): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_PREVIEW_TEXT] = text
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened Font (Local)
    // ════════════════════════════════════════════════════════════

    val lastOpenedFontPath: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_LAST_OPENED_FONT_PATH]!!
        })

    fun setLastOpenedFontPath(path: String?): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_FONT_PATH)
            }
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened Font (Favorites)
    // ════════════════════════════════════════════════════════════

    val lastOpenedFavoriteFontPath: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_LAST_OPENED_FAVORITE_FONT_PATH]!!
        })

    fun setLastOpenedFavoriteFontPath(path: String?): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_FAVORITE_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_FAVORITE_FONT_PATH)
            }
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Folder Path
    // ════════════════════════════════════════════════════════════

    val folderPath: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_FOLDER_PATH]!!
        })

    fun setFolderPath(path: String?): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            if (path != null) {
                mutablePrefs[KEY_FOLDER_PATH] = path
            } else {
                mutablePrefs.remove(KEY_FOLDER_PATH)
            }
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (Local Folder Fonts)
    // ════════════════════════════════════════════════════════════

    val sortType: Flowable<String>
        get() = data().map { prefs ->
            prefs[KEY_SORT_TYPE] ?: "NAME"
        }

    fun setSortType(type: String): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_SORT_TYPE] = type
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val sortAscending: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_SORT_ASCENDING] ?: true
        }

    fun setSortAscending(ascending: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_SORT_ASCENDING] = ascending
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (System Fonts) ★ منفصلة تماماً لتجنب التجمد ★
    // ════════════════════════════════════════════════════════════

    val systemSortType: Flowable<String>
        get() = data().map { prefs ->
            prefs[KEY_SYSTEM_SORT_TYPE] ?: "NAME"
        }

    fun setSystemSortType(type: String): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_SYSTEM_SORT_TYPE] = type
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val systemSortAscending: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_SYSTEM_SORT_ASCENDING] ?: true
        }

    fun setSystemSortAscending(ascending: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_SYSTEM_SORT_ASCENDING] = ascending
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Sort Options (Favorites) ★ منفصلة تماماً لتجنب أي تعارض ★
    // ════════════════════════════════════════════════════════════

    val favoritesSortType: Flowable<String>
        get() = data().map { prefs ->
            prefs[KEY_FAVORITES_SORT_TYPE] ?: "NAME"
        }

    fun setFavoritesSortType(type: String): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_FAVORITES_SORT_TYPE] = type
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val favoritesSortAscending: Flowable<Boolean>
        get() = data().map { prefs ->
            prefs[KEY_FAVORITES_SORT_ASCENDING] ?: true
        }

    fun setFavoritesSortAscending(ascending: Boolean): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_FAVORITES_SORT_ASCENDING] = ascending
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Last Opened System Font
    // ════════════════════════════════════════════════════════════

    val lastOpenedSystemFontPath: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_LAST_OPENED_SYSTEM_FONT_PATH]!!
        })

    fun setLastOpenedSystemFontPath(path: String?): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            if (path != null) {
                mutablePrefs[KEY_LAST_OPENED_SYSTEM_FONT_PATH] = path
            } else {
                mutablePrefs.remove(KEY_LAST_OPENED_SYSTEM_FONT_PATH)
            }
            Single.just<Preferences>(mutablePrefs)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Last Viewed Font
    // ════════════════════════════════════════════════════════════

    fun setLastViewedFont(path: String?, fileName: String?, realName: String?): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            if (path != null) mutablePrefs[KEY_VIEWER_FONT_PATH] = path
            if (fileName != null) mutablePrefs[KEY_VIEWER_FILE_NAME] = fileName
            if (realName != null) {
                mutablePrefs[KEY_VIEWER_REAL_NAME] = realName
            } else {
                mutablePrefs.remove(KEY_VIEWER_REAL_NAME)
            }
            Single.just<Preferences>(mutablePrefs)
        }
    }

    fun clearLastViewedFont(): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs.remove(KEY_VIEWER_FONT_PATH)
            mutablePrefs.remove(KEY_VIEWER_FILE_NAME)
            mutablePrefs.remove(KEY_VIEWER_REAL_NAME)
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontPath: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_VIEWER_FONT_PATH]!!
        })

    val viewerFileName: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_VIEWER_FILE_NAME]!!
        })

    val viewerRealName: Flowable<String>
        get() = data().map(Function<Preferences, String> { prefs ->
            prefs[KEY_VIEWER_REAL_NAME]!!
        })


    // ════════════════════════════════════════════════════════════
    // Font Viewer - Font Weight
    // ════════════════════════════════════════════════════════════

    fun setViewerFontWeight(weight: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_WEIGHT] = weight
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontWeight: Flowable<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_WEIGHT] ?: DEFAULT_VIEWER_FONT_WEIGHT
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Width Axis (wdth)
    // ════════════════════════════════════════════════════════════

    fun setViewerFontWidth(width: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_WIDTH] = width
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontWidth: Flowable<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_WIDTH] ?: DEFAULT_VIEWER_FONT_WIDTH
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Grade Axis (GRAD)
    // ════════════════════════════════════════════════════════════

    fun setViewerFontGrade(grade: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_GRADE] = grade
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontGrade: Flowable<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_GRADE] ?: DEFAULT_VIEWER_FONT_GRADE
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Roundness Axis (ROND)
    // ════════════════════════════════════════════════════════════

    fun setViewerFontRoundness(roundness: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_ROUNDNESS] = roundness
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontRoundness: Flowable<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_ROUNDNESS] ?: DEFAULT_VIEWER_FONT_ROUNDNESS
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Italic Axis (ital)
    // ════════════════════════════════════════════════════════════

    fun setViewerFontItalicAxis(italValue: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_ITALIC_AXIS] = italValue
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontItalicAxis: Flowable<Float>
        get() = data().map { prefs ->
            prefs[KEY_VIEWER_FONT_ITALIC_AXIS] ?: DEFAULT_VIEWER_FONT_ITALIC_AXIS
        }

    // ════════════════════════════════════════════════════════════
    // Font Viewer - Mono Axis (MONO)
    // ════════════════════════════════════════════════════════════

    fun setViewerFontMono(monoValue: Float): Single<Preferences> {
        return update { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[KEY_VIEWER_FONT_MONO] = monoValue
            Single.just<Preferences>(mutablePrefs)
        }
    }

    val viewerFontMono: Flowable<Float>
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
