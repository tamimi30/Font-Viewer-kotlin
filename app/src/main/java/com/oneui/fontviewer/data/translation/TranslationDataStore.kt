package com.oneui.fontviewer.data.translation

import android.content.Context
import android.util.Log

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.rxjava3.RxPreferenceDataStoreBuilder
import androidx.datastore.rxjava3.RxDataStore

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class TranslationDataStore private constructor(context: Context) {

    companion object {
        private const val TAG = "TranslationDataStore"
        private const val DATASTORE_NAME = "translation_cache"
        private const val MAX_CACHE_SIZE = 500

        @Volatile
        private var INSTANCE: TranslationDataStore? = null

        fun getInstance(context: Context): TranslationDataStore {
            if (INSTANCE == null) {
                synchronized(TranslationDataStore::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = TranslationDataStore(context)
                    }
                }
            }
            return INSTANCE!!
        }
    }

    private val context: Context = context.applicationContext
    private val dataStore: RxDataStore<Preferences> = RxPreferenceDataStoreBuilder(
        this.context,
        DATASTORE_NAME
    ).build()

    fun getTranslation(key: String?): Single<String> {
        if (key == null || key.isEmpty()) {
            return Single.just("")
        }

        val prefKey = stringPreferencesKey(key)
        return dataStore.data()
            .firstOrError()
            .map { prefs ->
                val value = prefs.get(prefKey)
                value ?: ""
            }
            .onErrorReturnItem("")
    }

    fun saveTranslation(key: String?, value: String?) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
            return
        }

        val prefKey = stringPreferencesKey(key)
        dataStore.updateDataAsync { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs[prefKey] = value
            Single.just<Preferences>(mutablePrefs)
        }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { _ ->
                    Log.d(TAG, "Translation cached successfully")
                    checkAndCleanCache()
                },
                { error -> Log.e(TAG, "Error saving translation: " + error.message) }
            )
    }

    private fun checkAndCleanCache() {
        dataStore.data()
            .firstOrError()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs ->
                    val size = prefs.asMap().size
                    if (size > MAX_CACHE_SIZE) {
                        Log.i(TAG, "Cache size exceeded $MAX_CACHE_SIZE, clearing cache")
                        clearCache()
                    }
                },
                { error -> Log.e(TAG, "Error checking cache size: " + error.message) }
            )
    }

    fun clearCache() {
        dataStore.updateDataAsync { prefs ->
            val mutablePrefs = prefs.toMutablePreferences()
            mutablePrefs.clear()
            Single.just<Preferences>(mutablePrefs)
        }
            .subscribeOn(Schedulers.io())
            .subscribe(
                { _ -> Log.i(TAG, "Translation cache cleared") },
                { error -> Log.e(TAG, "Error clearing cache: " + error.message) }
            )
    }

}
