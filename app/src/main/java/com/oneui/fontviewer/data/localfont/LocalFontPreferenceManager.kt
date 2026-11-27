package com.oneui.fontviewer.data.localfont

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.data.firstBlocking

class LocalFontPreferenceManager @JvmOverloads constructor(
    context: Context,
    private val isFavoritesList: Boolean = false
) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(context)

    private var cachedLastOpenedPath: String? = null

    init {
        try {
            cachedLastOpenedPath = if (isFavoritesList)
                dataStore.lastOpenedFavoriteFontPath.firstBlocking()
            else
                dataStore.lastOpenedFontPath.firstBlocking()
        } catch (e: Exception) {
            cachedLastOpenedPath = null
            Log.d(TAG, "No cached last opened font")
        }
    }

    fun saveLastOpenedFont(fontPath: String?) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path")
            return
        }

        cachedLastOpenedPath = fontPath

        dataStore.launchWrite(
            onError = { error -> Log.e(TAG, "Error saving last opened font", error) },
            onSuccess = { Log.d(TAG, "Saved last opened font: $fontPath") }
        ) {
            if (isFavoritesList)
                dataStore.setLastOpenedFavoriteFontPath(fontPath)
            else
                dataStore.setLastOpenedFontPath(fontPath)
        }
    }

    fun isLastOpenedFont(fontPath: String?): Boolean {
        if (fontPath == null) {
            return false
        }

        return cachedLastOpenedPath != null && cachedLastOpenedPath == fontPath
    }

    fun saveFontFolderPath(folderPath: String?) {
        if (folderPath == null) {
            Log.w(TAG, "Attempted to save null folder path")
            return
        }

        dataStore.launchWrite(
            onError = { error -> Log.e(TAG, "Error saving folder path", error) },
            onSuccess = { Log.d(TAG, "Saved font folder path: $folderPath") }
        ) { dataStore.setFolderPath(folderPath) }
    }

    val fontFolderPath: String?
        get() {
            return try {
                dataStore.folderPath.firstBlocking()
            } catch (e: Exception) {
                Log.d(TAG, "No folder path found")
                null
            }
        }

    fun hasFontFolderPath(): Boolean {
        return fontFolderPath != null
    }

    companion object {
        private const val TAG = "LocalFontPreferenceManager"
    }

}
