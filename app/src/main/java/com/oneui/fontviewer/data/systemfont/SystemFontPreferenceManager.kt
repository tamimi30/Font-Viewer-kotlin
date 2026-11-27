package com.oneui.fontviewer.data.systemfont

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.data.AppDataStore

import io.reactivex.rxjava3.schedulers.Schedulers

class SystemFontPreferenceManager(context: Context) {

    companion object {
        private const val TAG = "SystemFontPrefManager"
    }

    private val dataStore: AppDataStore = AppDataStore.getInstance(context)

    private var cachedLastOpenedPath: String? = null

    init {
        try {
            cachedLastOpenedPath = dataStore.lastOpenedSystemFontPath.blockingFirst()
        } catch (e: Exception) {
            cachedLastOpenedPath = null
            Log.d(TAG, "No cached last opened system font")
        }
    }

    fun saveLastOpenedFont(fontPath: String?) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path")
            return
        }

        cachedLastOpenedPath = fontPath

        dataStore.setLastOpenedSystemFontPath(fontPath)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { _ -> Log.d(TAG, "Saved last opened system font: $fontPath") },
                { error -> Log.e(TAG, "Error saving last opened system font", error) }
            )
    }

    val lastOpenedFont: String?
        get() {
            try {
                cachedLastOpenedPath = dataStore.lastOpenedSystemFontPath.blockingFirst()
                return cachedLastOpenedPath
            } catch (e: Exception) {
                Log.d(TAG, "No last opened system font found")
                return null
            }
        }

    fun isLastOpenedFont(fontPath: String?): Boolean {
        if (fontPath == null) {
            return false
        }

        return cachedLastOpenedPath != null && cachedLastOpenedPath == fontPath
    }
}
