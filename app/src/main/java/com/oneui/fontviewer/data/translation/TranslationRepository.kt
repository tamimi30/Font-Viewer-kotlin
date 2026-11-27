package com.oneui.fontviewer.data.translation

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.data.firstBlocking
import com.oneui.fontviewer.data.settings.SettingsRepository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TranslationRepository(private val context: Context) {

    private val translationCache: TranslationDataStore = TranslationDataStore.getInstance(context)
    private val appDataStore: AppDataStore = AppDataStore.getInstance(context)
    private val translationApi: TranslationApi = TranslationApi(context)

    // كان كل طلب ترجمة يبدأ Thread جديداً؛ الآن يعمل داخل coroutine على Dispatchers.IO
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    interface TranslationCallback {
        fun onTranslationComplete(translatedData: Map<String, String>)
        // NOTE(kotlin-migration): error is nullable because the Java code passes e.getMessage(), which may be null.
        fun onTranslationFailed(error: String?)
    }

    fun translateMetadata(metadata: Map<String, String>, callback: TranslationCallback) {
        if (!isTranslationEnabled) {
            callback.onTranslationComplete(metadata)
            return
        }

        val targetLanguage = getCurrentLanguage()

        if (targetLanguage == "en") {
            callback.onTranslationComplete(metadata)
            return
        }

        scope.launch {
            try {
                val translatedData: MutableMap<String, String> = HashMap(metadata)
                var translationErrorType: String? = null

                val fieldsToTranslate = arrayOf(
                    "Copyright",
                    "Trademark",
                    "Description",
                    "LicenseDescription"
                )

                for (field in fieldsToTranslate) {
                    if (metadata.containsKey(field)) {
                        val originalText: String? = metadata[field]

                        if (originalText != null && originalText.isNotEmpty() && originalText.length < 5000) {
                            val cacheKey = generateCacheKey(originalText, targetLanguage)

                            if (cacheKey == null) {
                                continue
                            }

                            var cachedTranslation: String? = ""
                            try {
                                cachedTranslation = translationCache.getTranslation(cacheKey)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Error reading from cache: " + e.message)
                            }

                            if (cachedTranslation != null && cachedTranslation.isNotEmpty()) {
                                translatedData[field] = cachedTranslation
                                Log.d(TAG, "Using cached translation for field: $field")
                            } else if (!translationApi.isInternetAvailable) {
                                translationErrorType = "NO_INTERNET"
                                break
                            } else {
                                val translatedText: String? = translationApi.translateText(originalText, "en", targetLanguage)

                                if ("ERROR_NETWORK" == translatedText) {
                                    translationErrorType = "NO_INTERNET"
                                    break
                                } else if ("ERROR_API" == translatedText || translatedText == null || translatedText.isEmpty()) {
                                    translationErrorType = "API_ERROR"
                                    break
                                } else {
                                    translationCache.saveTranslation(cacheKey, translatedText)
                                    translatedData[field] = translatedText
                                    Log.d(TAG, "Translated and cached field: $field")
                                }

                                delay(100)
                            }
                        }
                    }
                }

                if ("NO_INTERNET" == translationErrorType) {
                    callback.onTranslationFailed("NO_INTERNET")
                } else if ("API_ERROR" == translationErrorType) {
                    callback.onTranslationFailed("API_ERROR")
                } else {
                    callback.onTranslationComplete(translatedData)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Translation failed: " + e.message, e)
                callback.onTranslationFailed(e.message)
            }
        }
    }

    private fun getCurrentLanguage(): String {
        return try {
            val currentLocale = SettingsRepository.getLocale(context)
            val language = currentLocale.language

            if (language == "ar") {
                "ar"
            } else {
                "en"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current language: " + e.message)
            "en"
        }
    }

    private fun generateCacheKey(text: String, targetLang: String): String? {
        return try {
            val combined = text.substring(0, Math.min(text.length, 100)) + "_" + targetLang
            combined.hashCode().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating cache key: " + e.message)
            null
        }
    }

    val isTranslationEnabled: Boolean
        get() {
            return try {
                appDataStore.translationEnabled.firstBlocking()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking translation enabled: " + e.message)
                false
            }
        }

    companion object {
        private const val TAG = "TranslationRepository"
    }
}
