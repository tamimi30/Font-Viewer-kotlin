package com.oneui.fontviewer.ui.settings

import android.app.Application
import android.os.Build
import android.util.Log

import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.ui.utils.ThemeHelper
import com.oneui.fontviewer.data.settings.SettingsRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(application)
    private val disposables = CompositeDisposable()

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
        disposables.add(
            dataStore.languageMode
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _languageMode.setValue(value) },
                    { error -> Log.e(TAG, "Error observing language mode", error) }
                )
        )

        disposables.add(
            dataStore.themeMode
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _themeMode.setValue(value) },
                    { error -> Log.e(TAG, "Error observing theme mode", error) }
                )
        )

        disposables.add(
            dataStore.themeAuto
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _themeAuto.setValue(value) },
                    { error -> Log.e(TAG, "Error observing theme auto", error) }
                )
        )


        disposables.add(
            dataStore.fontPreviewEnabled
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _fontPreviewEnabled.setValue(value) },
                    { error -> Log.e(TAG, "Error observing font preview", error) }
                )
        )

        disposables.add(
            dataStore.translationEnabled
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _translationEnabled.setValue(value) },
                    { error -> Log.e(TAG, "Error observing translation", error) }
                )
        )

        disposables.add(
            dataStore.previewText
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { value -> _previewText.setValue(value) },
                    { error -> Log.e(TAG, "Error observing preview text", error) }
                )
        )
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

        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ ->
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
                    },
                    { error -> Log.e(TAG, "Error setting language mode", error) }
                )
        )
    }

    fun syncLanguageModeFromSystem(mode: Int) {
        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ -> Log.d(TAG, "Language mode synced from system to DataStore: $mode") },
                    { error -> Log.e(TAG, "Error syncing language mode from system", error) }
                )
        )
    }


    fun setThemeMode(mode: Int) {
        if (_themeMode.value != null && _themeMode.value == mode) {
            return
        }

        Log.d(TAG, "Setting theme mode to: $mode")

        disposables.add(
            dataStore.setThemeAuto(false)
                .flatMap { _ -> dataStore.setThemeMode(mode) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ ->
                        Log.d(TAG, "Theme mode updated and auto disabled")
                        ThemeHelper.applyTheme(getApplication())
                    },
                    { error -> Log.e(TAG, "Error setting theme mode", error) }
                )
        )
    }

    fun setThemeAuto(enabled: Boolean) {
        if (_themeAuto.value != null && _themeAuto.value == enabled) {
            return
        }

        Log.d(TAG, "Setting theme auto to: $enabled")

        disposables.add(
            dataStore.setThemeAuto(enabled)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ ->
                        Log.d(TAG, "Theme auto updated")
                        ThemeHelper.applyTheme(getApplication())
                    },
                    { error -> Log.e(TAG, "Error setting theme auto", error) }
                )
        )
    }


    fun setFontPreviewEnabled(enabled: Boolean) {
        if (_fontPreviewEnabled.value != null && _fontPreviewEnabled.value == enabled) {
            return
        }

        disposables.add(
            dataStore.setFontPreviewEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { _ -> Log.d(TAG, "Font preview updated to: $enabled") },
                    { error -> Log.e(TAG, "Error setting font preview", error) }
                )
        )
    }

    fun setTranslationEnabled(enabled: Boolean) {
        if (_translationEnabled.value != null && _translationEnabled.value == enabled) {
            return
        }

        disposables.add(
            dataStore.setTranslationEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    { _ -> Log.d(TAG, "Translation updated to: $enabled") },
                    { error -> Log.e(TAG, "Error setting translation", error) }
                )
        )
    }

    fun setPreviewText(text: String?) {
        if (_previewText.value != null && _previewText.value == text) {
            return
        }

        disposables.add(
            dataStore.setPreviewText(text ?: "")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { _ ->
                        Log.d(TAG, "Preview text updated")
                        _settingsEvent.setValue(
                            SettingsEvent(
                                SettingsEventType.SHOW_TOAST,
                                R.string.toast_settings_preview_text_updated
                            )
                        )
                    },
                    { error -> Log.e(TAG, "Error setting preview text", error) }
                )
        )
    }

    override fun onCleared() {
        super.onCleared()
        disposables.clear()
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
