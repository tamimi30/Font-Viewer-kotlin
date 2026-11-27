package com.oneui.fontviewer.data.update.api

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import android.util.Xml

import com.oneui.fontviewer.BuildConfig

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * يسأل خادم Galaxy Store عن وجود تحديث للتطبيق، بنفس الطلب الذي يستخدمه Samsung Video
 * (stubUpdateCheck.as) والاستجابة XML فيها resultCode:
 *  0 أو 1 = لا يوجد تحديث، 2 = يتوفر تحديث.
 *
 * ملاحظة: هذه الواجهة تعمل للتطبيقات المنشورة في Galaxy Store فقط، وسامسونج لا توثقها رسمياً.
 */
class GalaxyStoreUpdateApi {

    class Result(
        val resultCode: Int,
        val versionName: String?,
        val versionCode: Long
    )

    @Throws(IOException::class)
    fun check(context: Context): Result {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(buildUrl(context)).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.doInput = true

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Galaxy Store returned HTTP $responseCode")
                throw IOException("HTTP $responseCode")
            }
            return parse(connection.inputStream)
        } catch (e: XmlPullParserException) {
            throw IOException("Malformed Galaxy Store response", e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val TAG = "GalaxyStoreUpdateApi"
        private const val BASE_URL = "https://vas.samsungapps.com/stub/stubUpdateCheck.as"
        private const val TIMEOUT_MS = 15000

        const val RESULT_NO_UPDATE = 0
        const val RESULT_NO_UPDATE_ALT = 1
        const val RESULT_CAN_UPDATE = 2

        @Throws(IOException::class, XmlPullParserException::class)
        private fun parse(inputStream: InputStream): Result {
            var resultCode = -1
            var versionName: String? = null
            var versionCode = 0L

            inputStream.use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, null)
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        val name = parser.name
                        if ("resultCode".equals(name, ignoreCase = true)) {
                            resultCode = parseInt(parser.nextText(), -1)
                        } else if ("versionName".equals(name, ignoreCase = true)) {
                            versionName = parser.nextText()
                        } else if ("versionCode".equals(name, ignoreCase = true)) {
                            versionCode = parseInt(parser.nextText(), 0).toLong()
                        }
                    }
                    eventType = parser.next()
                }
            }
            return Result(resultCode, versionName, versionCode)
        }

        private fun parseInt(value: String?, fallback: Int): Int {
            return try {
                if (value == null) fallback else value.trim().toInt()
            } catch (e: NumberFormatException) {
                fallback
            }
        }

        private fun buildUrl(context: Context): String {
            val packageName = context.packageName
            val operator = getSimOperator(context)

            val url = StringBuilder(BASE_URL).append('?')
            append(url, "appId", packageName, true)
            append(url, "callerId", packageName, false)
            append(url, "versionCode", BuildConfig.VERSION_CODE.toString(), false)
            append(url, "deviceId", Build.MODEL.replaceFirst("SAMSUNG-", ""), false)
            append(url, "mcc", operator[0], false)
            append(url, "mnc", operator[1], false)
            append(url, "cc", "NONE", false)
            append(url, "csc", "NONE", false)
            append(url, "sdkVer", Build.VERSION.SDK_INT.toString(), false)
            append(url, "systemId",
                (System.currentTimeMillis() - SystemClock.elapsedRealtime()).toString(), false)
            append(url, "abiType", getAbiType(), false)
            append(url, "extuk", "", false)
            append(url, "updateType", "self", false)
            append(url, "pd", "0", false)
            return url.toString()
        }

        private fun append(url: StringBuilder, key: String, value: String, first: Boolean) {
            if (!first) {
                url.append('&')
            }
            try {
                url.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()))
            } catch (e: java.io.UnsupportedEncodingException) {
                // UTF-8 مدعوم دائماً على أندرويد
                url.append(key).append('=')
            }
        }

        private fun getSimOperator(context: Context): Array<String> {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager?
            val simOperator: String? = telephonyManager?.simOperator
            if (simOperator == null || simOperator.length <= 3) {
                return arrayOf("", "")
            }
            return arrayOf(simOperator.substring(0, 3), simOperator.substring(3))
        }

        private fun getAbiType(): String {
            if (Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()) {
                return "64"
            }
            return if (Build.SUPPORTED_32_BIT_ABIS.isNotEmpty()) "32" else "ex"
        }
    }
}
