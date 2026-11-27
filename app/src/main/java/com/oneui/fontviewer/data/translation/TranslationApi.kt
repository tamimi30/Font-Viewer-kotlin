package com.oneui.fontviewer.data.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.util.Log

import org.json.JSONArray

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class TranslationApi(private val context: Context) {

    companion object {
        private const val TAG = "TranslationApi"
    }

    // Translate text using Google Translate API
    fun translateText(text: String, sourceLang: String, targetLang: String): String {
        var connection: HttpURLConnection? = null
        try {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val urlString = String.format(
                "https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=%s&tl=%s&dt=t&q=%s",
                sourceLang, targetLang, encodedText
            )

            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val br = BufferedReader(
                    InputStreamReader(connection.inputStream, "UTF-8"))
                val response = StringBuilder()
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    response.append(line)
                }
                br.close()

                val jsonArray = JSONArray(response.toString())
                val translationsArray = jsonArray.getJSONArray(0)

                val translatedText = StringBuilder()
                for (i in 0 until translationsArray.length()) {
                    val translation = translationsArray.getJSONArray(i)
                    translatedText.append(translation.getString(0))
                }

                return translatedText.toString()
            } else {
                Log.e(TAG, "Translation API returned error code: $responseCode")
                return "ERROR_API"
            }

        } catch (e: java.io.IOException) {
            // هذه تلتقط الحالة 2: متصل بالواي فاي ولكن لا يوجد انترنت فعلي
            Log.e(TAG, "Network error during translation: " + e.message, e)
            return "ERROR_NETWORK"
        } catch (e: Exception) {
            // هذه تلتقط الحالة 3: أخطاء برمجية أو تغير في هيكل بيانات جوجل
            Log.e(TAG, "Failed to translate text: " + e.message, e)
            return "ERROR_API"
        } finally {
            if (connection != null) {
                connection.disconnect()
            }
        }
    }

    val isInternetAvailable: Boolean
        get() {
            try {
                val connectivityManager =
                    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
                if (connectivityManager == null) {
                    return false
                }
                val activeNetwork: NetworkInfo? = connectivityManager.activeNetworkInfo
                return activeNetwork != null && activeNetwork.isConnected
            } catch (e: Exception) {
                Log.e(TAG, "Error checking internet connection: " + e.message)
                return false
            }
        }
}
