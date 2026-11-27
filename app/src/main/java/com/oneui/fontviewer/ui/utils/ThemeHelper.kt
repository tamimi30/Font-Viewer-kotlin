package com.oneui.fontviewer.ui.utils

import android.content.Context
import android.util.Log

import androidx.appcompat.app.AppCompatDelegate
import com.oneui.fontviewer.data.settings.SettingsRepository

object ThemeHelper {

    private const val TAG = "ThemeHelper"

    @JvmStatic
    fun applyTheme(context: Context) {
        val isAuto = SettingsRepository.isThemeAuto(context)

        Log.d(TAG, "Applying theme - Auto mode: $isAuto")

        if (isAuto) {
            Log.d(TAG, "Applying FOLLOW_SYSTEM mode")
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } else {
            val mode = SettingsRepository.getThemeMode(context)
            if (mode == SettingsRepository.THEME_DARK) {
                Log.d(TAG, "Applying DARK mode")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                Log.d(TAG, "Applying LIGHT mode")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
    }
}
