package com.oneui.fontviewer.data.translation

import android.content.Context
import android.util.Log

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    // نطاق خلفي واحد (Singleton) لا يُلغى: يستخدمه DataStore والكتابات التي لا ننتظر نتيجتها
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // نفس اسم الملف السابق (translation_cache.preferences_pb) كي يبقى الكاش الحالي كما هو
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { this.context.preferencesDataStoreFile(DATASTORE_NAME) }
    )

    /** يعيد الترجمة المحفوظة، أو نصاً فارغاً إذا لم توجد أو تعذّرت القراءة. */
    suspend fun getTranslation(key: String?): String {
        if (key == null || key.isEmpty()) {
            return ""
        }

        val prefKey = stringPreferencesKey(key)
        return try {
            dataStore.data.first()[prefKey] ?: ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
    }

    fun saveTranslation(key: String?, value: String?) {
        if (key == null || key.isEmpty() || value == null || value.isEmpty()) {
            return
        }

        val prefKey = stringPreferencesKey(key)
        scope.launch {
            try {
                dataStore.updateData { prefs ->
                    prefs.toMutablePreferences().also { it[prefKey] = value }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error saving translation: " + e.message)
                return@launch
            }
            Log.d(TAG, "Translation cached successfully")
            checkAndCleanCache()
        }
    }

    private fun checkAndCleanCache() {
        scope.launch {
            try {
                val size = dataStore.data.first().asMap().size
                if (size > MAX_CACHE_SIZE) {
                    Log.i(TAG, "Cache size exceeded $MAX_CACHE_SIZE, clearing cache")
                    clearCache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cache size: " + e.message)
            }
        }
    }

    fun clearCache() {
        scope.launch {
            try {
                dataStore.updateData { prefs ->
                    prefs.toMutablePreferences().also { it.clear() }
                }
                Log.i(TAG, "Translation cache cleared")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing cache: " + e.message)
            }
        }
    }

}
