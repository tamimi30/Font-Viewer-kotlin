package com.oneui.fontviewer.data.common.variable

import android.graphics.Typeface
import android.graphics.fonts.Font
import java.io.File

/**
 * Creates Typeface instances for variable fonts by applying axis values
 * (font-variation-settings).
 */
object VariableTypefaceFactory {

    private const val TAG = "VariableTypefaceFactory"

    /**
     * يبني نص إعدادات تنويع الخط (font-variation-settings) من خريطة قيم المحاور،
     * بالصيغة التي يتوقعها Font.Builder.setFontVariationSettings في اندرويد، مثل:
     * "'wght' 700, 'wdth' 100, 'GRAD' 50, 'ROND' 100, 'ital' 1, 'MONO' 1"
     */
    fun buildVariationSettingsString(axisValues: Map<String, Float>?): String {
        if (axisValues == null || axisValues.isEmpty()) {
            return ""
        }

        val builder = StringBuilder()
        for (entry in axisValues.entries) {
            if (builder.length > 0) {
                builder.append(", ")
            }
            builder.append("'").append(entry.key).append("' ").append(entry.value)
        }

        return builder.toString()
    }

    /**
     * ينشئ Typeface بتطبيق عدة محاور دفعة واحدة (وزن، عرض، ميل، تدرج، استدارة، تباعد أحادي...)،
     * ويدعم أي عدد من المحاور معاً.
     */
    fun createTypefaceWithAxes(fontFile: File, axisValues: Map<String, Float>?, ttcIndex: Int): Typeface? {
        try {
            val fontBuilder = Font.Builder(fontFile)

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex)
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex)
            }

            val variationSettings = buildVariationSettingsString(axisValues)
            if (!variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings)
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings)
            }

            val font = fontBuilder.build()

            val fallbackBuilder =
                Typeface.CustomFallbackBuilder(
                    android.graphics.fonts.FontFamily.Builder(font).build()
                )

            val result = fallbackBuilder.build()
            android.util.Log.d(TAG, "Successfully created variable Typeface with axes: " + axisValues +
                                   ", ttcIndex: " + ttcIndex)

            return result

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with axes: " + axisValues +
                                    ", ttcIndex: " + ttcIndex, e)

            try {
                return Typeface.createFromFile(fontFile)
            } catch (ex: Exception) {
                return null
            }
        }
    }
}
