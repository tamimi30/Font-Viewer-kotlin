package com.oneui.fontviewer.utils.update

import java.util.regex.Pattern

/**
 * مقارنة أرقام الإصدارات النصية مثل "1.0" و "v1.2.3" و "1.4-beta".
 * تُقارن الأجزاء الرقمية فقط، وتتجاهل البادئة v وأي لاحقة بعد الأرقام.
 */
object VersionComparator {

    private val NUMERIC_VERSION: Pattern = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)")

    /** يعيد true إذا كان الإصدار candidate أحدث من الإصدار current. */
    fun isNewer(candidate: String?, current: String?): Boolean {
        return compare(candidate, current) > 0
    }

    /** يعيد true إذا كان النص يحتوي على رقم إصدار يمكن مقارنته. */
    fun isParsable(version: String?): Boolean {
        return parse(version).size > 0
    }

    fun compare(a: String?, b: String?): Int {
        val partsA = parse(a)
        val partsB = parse(b)
        val length = Math.max(partsA.size, partsB.size)
        for (i in 0 until length) {
            val valueA = if (i < partsA.size) partsA[i] else 0
            val valueB = if (i < partsB.size) partsB[i] else 0
            if (valueA != valueB) {
                return Integer.compare(valueA, valueB)
            }
        }
        return 0
    }

    private fun parse(version: String?): IntArray {
        if (version == null) {
            return IntArray(0)
        }
        val matcher = NUMERIC_VERSION.matcher(version.trim())
        if (!matcher.find()) {
            return IntArray(0)
        }
        val segments = matcher.group(1)!!.split("\\.".toRegex()).toTypedArray()
        val parts = IntArray(segments.size)
        for (i in segments.indices) {
            try {
                parts[i] = Integer.parseInt(segments[i])
            } catch (e: NumberFormatException) {
                // رقم أكبر من حدود int: نعتبره أكبر قيمة ممكنة
                parts[i] = Int.MAX_VALUE
            }
        }
        return parts
    }
}
