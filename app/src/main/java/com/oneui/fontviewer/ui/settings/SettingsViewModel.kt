package com.oneui.fontviewer.ui.settings

import android.app.Application
import android.os.Build
import android.util.Log

import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.ui.utils.ThemeHelper
import com.oneui.fontviewer.data.settings.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(application)

    private val _languageMode = MutableLiveData<Int>()
    private val _themeMode = MutableLiveData<Int>()
    private val _themeAuto = MutableLiveData<Boolean>()

    private val _fontPreviewEnabled = MutableLiveData<Boolean>()
    private val _translationEnabled = MutableLiveData<Boolean>()

    private val _previewText = MutableLiveData<String>()

    private val _settingsEvent = MutableLiveData<SettingsEvent>()

    init {
        observeDataStore()
    }

    private fun observeDataStore() {
        observe(dataStore.languageMode, _languageMode, "language mode")
        observe(dataStore.themeMode, _themeMode, "theme mode")
        observe(dataStore.themeAuto, _themeAuto, "theme auto")
        observe(dataStore.fontPreviewEnabled, _fontPreviewEnabled, "font preview")
        observe(dataStore.translationEnabled, _translationEnabled, "translation")
        observe(dataStore.previewText, _previewText, "preview text")
    }

    /** يجمع قيم الـ Flow ويمررها إلى LiveData على الخيط الرئيسي (viewModelScope). */
    private fun <T> observe(source: Flow<T>, target: MutableLiveData<T>, name: String) {
        viewModelScope.launch {
            source
                .catch { error -> Log.e(TAG, "Error observing $name", error) }
                .collect { value -> target.value = value }
        }
    }


    val languageMode: LiveData<Int>
        get() = _languageMode

    val themeMode: LiveData<Int>
        get() = _themeMode

    val themeAuto: LiveData<Boolean>
        get() = _themeAuto

    val fontPreviewEnabled: LiveData<Boolean>
        get() = _fontPreviewEnabled

    val translationEnabled: LiveData<Boolean>
        get() = _translationEnabled

    val previewText: LiveData<String>
        get() = _previewText

    val settingsEvent: LiveData<SettingsEvent>
        get() = _settingsEvent


    @Suppress("DEPRECATION")
    fun setLanguageMode(mode: Int) {
        if (_languageMode.value != null && _languageMode.value == mode) {
            return
        }

        viewModelScope.launch {
            try {
                dataStore.setLanguageMode(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting language mode", e)
                return@launch
            }

            Log.d(TAG, "Language mode saved to DataStore: $mode")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = getApplication<Application>().getSystemService(android.app.LocaleManager::class.java)
                if (localeManager != null) {
                    if (mode == SettingsRepository.LANGUAGE_ARABIC) {
                        localeManager.applicationLocales = android.os.LocaleList.forLanguageTags("ar")
                    } else if (mode == SettingsRepository.LANGUAGE_ENGLISH) {
                        localeManager.applicationLocales = android.os.LocaleList.forLanguageTags("en")
                    } else {
                        localeManager.applicationLocales = android.os.LocaleList.getEmptyLocaleList()
                    }
                }
                // ★ من أندرويد 13 فما فوق، النظام نفسه يعيد إنشاء كل الأنشطة
                // تلقائياً بمجرد استدعاء setApplicationLocales، لذلك لا نستدعي
                // recreateAllActivities() هنا. استدعاؤها يدوياً هو ما كان يسبب
                // الوميض المزدوج (إعادة إنشاء من النظام + إعادة إنشاء يدوية).
            } else {
                val locale: java.util.Locale
                if (mode == SettingsRepository.LANGUAGE_ARABIC) {
                    locale = java.util.Locale("ar")
                } else if (mode == SettingsRepository.LANGUAGE_ENGLISH) {
                    locale = java.util.Locale("en")
                } else {
                    locale = android.content.res.Resources.getSystem().configuration.locales.get(0)
                }
                java.util.Locale.setDefault(locale)

                // ★ في هذا المسار فقط (ما قبل أندرويد 13) النظام لا يعيد الإنشاء
                // تلقائياً، فنحتاج فعلياً لطلبه يدوياً هنا.
                _settingsEvent.setValue(SettingsEvent(SettingsEventType.RECREATE_ALL_ACTIVITIES))
            }
        }
    }

    fun syncLanguageModeFromSystem(mode: Int) {
        viewModelScope.launch {
            try {
                dataStore.setLanguageMode(mode)
                Log.d(TAG, "Language mode synced from system to DataStore: $mode")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing language mode from system", e)
            }
        }
    }


    fun setThemeMode(mode: Int) {
        if (_themeMode.value != null && _themeMode.value == mode) {
            return
        }

        Log.d(TAG, "Setting theme mode to: $mode")

        viewModelScope.launch {
            try {
                // بالترتيب: تعطيل الوضع التلقائي أولاً ثم حفظ الوضع المختار (كان flatMap)
                dataStore.setThemeAuto(false)
                dataStore.setThemeMode(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting theme mode", e)
                return@launch
            }

            Log.d(TAG, "Theme mode updated and auto disabled")
            ThemeHelper.applyTheme(getApplication())
        }
    }

    fun setThemeAuto(enabled: Boolean) {
        if (_themeAuto.value != null && _themeAuto.value == enabled) {
            return
        }

        Log.d(TAG, "Setting theme auto to: $enabled")

        viewModelScope.launch {
            try {
                dataStore.setThemeAuto(enabled)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting theme auto", e)
                return@launch
            }

            Log.d(TAG, "Theme auto updated")
            ThemeHelper.applyTheme(getApplication())
        }
    }


    fun setFontPreviewEnabled(enabled: Boolean) {
        if (_fontPreviewEnabled.value != null && _fontPreviewEnabled.value == enabled) {
            return
        }

        viewModelScope.launch {
            try {
                dataStore.setFontPreviewEnabled(enabled)
                Log.d(TAG, "Font preview updated to: $enabled")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting font preview", e)
            }
        }
    }

    fun setTranslationEnabled(enabled: Boolean) {
        if (_translationEnabled.value != null && _translationEnabled.value == enabled) {
            return
        }

        viewModelScope.launch {
            try {
                dataStore.setTranslationEnabled(enabled)
                Log.d(TAG, "Translation updated to: $enabled")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting translation", e)
            }
        }
    }

    fun setPreviewText(text: String?) {
        if (_previewText.value != null && _previewText.value == text) {
            return
        }

        viewModelScope.launch {
            try {
                dataStore.setPreviewText(text ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error setting preview text", e)
                return@launch
            }

            Log.d(TAG, "Preview text updated")
            _settingsEvent.setValue(
                SettingsEvent(
                    SettingsEventType.SHOW_TOAST,
                    R.string.toast_settings_preview_text_updated
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope يُلغى تلقائياً هنا، فلا حاجة لتنظيف يدوي (كان disposables.clear())
        Log.d(TAG, "ViewModel cleared")
    }


    class SettingsEvent private constructor(
        val type: SettingsEventType,
        val message: String?,
        val messageResId: Int
    ) {
        private var handled = false

        constructor(type: SettingsEventType) : this(type, null, 0)

        constructor(type: SettingsEventType, message: String?) : this(type, message, 0)

        constructor(type: SettingsEventType, @StringRes messageResId: Int) : this(type, null, messageResId)

        // يعيد true مرة واحدة فقط (له أثر جانبي: يعلّم الحدث كمعالَج)
        val contentIfNotHandled: Boolean
            get() {
                if (handled) {
                    return false
                }
                handled = true
                return true
            }
    }

    enum class SettingsEventType {
        SHOW_TOAST,
        RECREATE_ACTIVITY,
        RECREATE_ALL_ACTIVITIES
    }

    companion object {
        private const val TAG = "SettingsViewModel"
    }
}
