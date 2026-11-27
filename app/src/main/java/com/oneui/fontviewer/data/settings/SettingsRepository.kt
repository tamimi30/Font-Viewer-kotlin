package com.oneui.fontviewer.data.settings

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.Log
import android.app.LocaleManager

import java.util.Locale

import com.oneui.fontviewer.data.AppDataStore

object SettingsRepository {

    private const val TAG = "SettingsRepository"

    const val LANGUAGE_SYSTEM = 0
    const val LANGUAGE_ARABIC = 1
    const val LANGUAGE_ENGLISH = 2

    const val THEME_LIGHT = 0
    const val THEME_DARK = 1


    fun getLanguageMode(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getSystemAssignedLanguage(context)
        }
        return try {
            AppDataStore.getInstance(context)
                .languageMode
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading language mode", e)
            LANGUAGE_SYSTEM
        }
    }

    fun getSystemAssignedLanguage(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                if (localeManager != null) {
                    val locales = localeManager.applicationLocales
                    if (!locales.isEmpty) {
                        val lang = locales.get(0).language
                        if (lang == "ar") {
                            return LANGUAGE_ARABIC
                        } else if (lang == "en") {
                            return LANGUAGE_ENGLISH
                        }
                    }
                }
                return LANGUAGE_SYSTEM
            } catch (e: Exception) {
                Log.e(TAG, "Error reading locale from LocaleManager", e)
                return LANGUAGE_SYSTEM
            }
        }
        return try {
            AppDataStore.getInstance(context)
                .languageMode
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading language mode from DataStore", e)
            LANGUAGE_SYSTEM
        }
    }

    fun getThemeMode(context: Context): Int {
        return try {
            AppDataStore.getInstance(context)
                .themeMode
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading theme mode", e)
            THEME_LIGHT
        }
    }

    fun isThemeAuto(context: Context): Boolean {
        return try {
            AppDataStore.getInstance(context)
                .themeAuto
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading theme auto", e)
            true
        }
    }


    fun isFontPreviewEnabled(context: Context): Boolean {
        return try {
            AppDataStore.getInstance(context)
                .fontPreviewEnabled
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading font preview setting", e)
            true
        }
    }

    fun isTranslationEnabled(context: Context): Boolean {
        return try {
            AppDataStore.getInstance(context)
                .translationEnabled
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading translation setting", e)
            false
        }
    }


    fun getPreviewText(context: Context): String {
        return try {
            AppDataStore.getInstance(context)
                .previewText
                .blockingFirst()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading preview text", e)
            AppDataStore.DEFAULT_PREVIEW_TEXT
        }
    }


    @Suppress("DEPRECATION")
    fun getLocale(context: Context): Locale {
        val mode = getLanguageMode(context)

        return when (mode) {
            LANGUAGE_ARABIC -> Locale("ar")
            LANGUAGE_ENGLISH -> Locale("en")
            else -> getSystemLocale()
        }
    }

    private fun getSystemLocale(): Locale {
        return Resources.getSystem().configuration.locales[0]
    }
}
