package com.oneui.fontviewer.data.common.metadata

import android.content.Context
import android.util.Log

import com.oneui.fontviewer.R

import java.io.File
import java.io.RandomAccessFile

object FontWeightWidthExtractor {

    private const val TAG = "FontWeightWidthExtractor"


    private const val VF_PREFIX = "VF"

    // الصيغة المخزّنة: "وزن|عرض|متغير" (مثل 400|5|1)، ولا تتغير بتغير لغة التطبيق
    private const val RAW_SEPARATOR = "|"


    fun extract(fontFile: File?, ttcIndex: Int): String? {
        if (fontFile == null || !fontFile.exists() || !fontFile.canRead()) {
            return null
        }
        try {
            val ww = readWeightWidth(fontFile, ttcIndex)
            if (ww == null) return null

            val hasWeight = ww[0] > 0
            val hasWidth = hasWidthName(ww[1])
            if (!hasWeight && !hasWidth) return null

            val isVF = isVariableFont(fontFile, ttcIndex)

            return ww[0].toString() + RAW_SEPARATOR + ww[1] + RAW_SEPARATOR + (if (isVF) "1" else "0")

        } catch (e: Exception) {
            Log.w(TAG, "extract() failed for " + fontFile.name + ": " + e.message)
            return null
        }
    }

    // تحويل الصيغة المخزّنة لنص يُعرض حسب لغة التطبيق. أي نص لا يطابق الصيغة
    // (مثل النصوص القديمة المخزّنة سابقاً) يُعاد كما هو
    fun formatLabel(context: Context, raw: String?): String? {
        if (raw == null || raw.isEmpty()) return raw

        val parts = raw.split("|")
        if (parts.size != 3) return raw

        val weightClass: Int
        val widthClass: Int
        try {
            weightClass = Integer.parseInt(parts[0])
            widthClass = Integer.parseInt(parts[1])
        } catch (e: NumberFormatException) {
            return raw
        }

        val label = buildLabel(getWeightName(context, weightClass), getWidthName(context, widthClass))
        if (label == null) return null

        return if ("1" == parts[2]) "$VF_PREFIX • $label" else label
    }

    private fun readWeightWidth(fontFile: File, ttcIndex: Int): IntArray? {
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(fontFile, "r")
            val fontOffset = resolveFontOffset(raf, ttcIndex)
            if (fontOffset < 0) return null

            raf.seek(fontOffset + 4)
            val numTables = raf.readUnsignedShort()
            raf.skipBytes(6)

            var os2Offset: Long = -1
            for (i in 0 until numTables) {
                val tag = ByteArray(4)
                raf.read(tag)
                val t = String(tag, Charsets.ISO_8859_1)
                raf.skipBytes(4)
                val off = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.skipBytes(4)

                if ("OS/2" == t) {
                    os2Offset = off
                    break
                }
            }

            if (os2Offset < 0) return null

            raf.seek(os2Offset + 2)
            raf.skipBytes(2)
            val weightClass = raf.readUnsignedShort()
            val widthClass = raf.readUnsignedShort()

            return intArrayOf(weightClass, widthClass)

        } catch (e: Exception) {
            Log.w(TAG, "readWeightWidth failed: " + e.message)
            return null
        } finally {
            if (raf != null) try {
                raf.close()
            } catch (ignored: Exception) {
            }
        }
    }


    private fun isVariableFont(fontFile: File, ttcIndex: Int): Boolean {
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(fontFile, "r")
            val fontOffset = resolveFontOffset(raf, ttcIndex)
            if (fontOffset < 0) return false

            raf.seek(fontOffset + 4)
            val numTables = raf.readUnsignedShort()
            raf.skipBytes(6)

            for (i in 0 until numTables) {
                val tag = ByteArray(4)
                raf.read(tag)
                val t = String(tag, Charsets.ISO_8859_1)
                raf.skipBytes(12)

                if ("fvar" == t) return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "isVariableFont check failed: " + e.message)
        } finally {
            if (raf != null) try {
                raf.close()
            } catch (ignored: Exception) {
            }
        }
        return false
    }


    @Throws(Exception::class)
    private fun resolveFontOffset(raf: RandomAccessFile, ttcIndex: Int): Long {
        raf.seek(0)
        val hdr = ByteArray(4)
        raf.read(hdr)
        val tag = String(hdr, Charsets.US_ASCII)

        if ("ttcf" == tag) {
            raf.skipBytes(4)
            val numFonts = readUInt32(raf)
            if (ttcIndex < 0 || ttcIndex >= numFonts) return -1
            raf.seek(12 + ttcIndex.toLong() * 4)
            return readUInt32(raf)
        }
        return 0L
    }

    @Throws(Exception::class)
    private fun readUInt32(raf: RandomAccessFile): Long {
        val b = ByteArray(4)
        raf.read(b)
        return ((b[0].toInt() and 0xFF).toLong() shl 24) or ((b[1].toInt() and 0xFF).toLong() shl 16) or
                ((b[2].toInt() and 0xFF).toLong() shl 8) or (b[3].toInt() and 0xFF).toLong()
    }

    private fun buildLabel(weight: String?, width: String?): String? {
        val hasWeight = weight != null && !weight.isEmpty()
        val hasWidth = width != null && !width.isEmpty()
        if (!hasWeight && !hasWidth) return null
        if (!hasWeight) return width
        if (!hasWidth) return weight
        return "$weight • $width"
    }


    fun getWeightName(context: Context, w: Int): String? {
        if (w <= 0) return null

        return when (w) {
            100 -> context.getString(R.string.font_info_weight_thin)
            200 -> context.getString(R.string.font_info_weight_extra_light)
            300 -> context.getString(R.string.font_info_weight_light)
            400 -> context.getString(R.string.font_info_weight_regular)
            500 -> context.getString(R.string.font_info_weight_medium)
            600 -> context.getString(R.string.font_info_weight_semi_bold)
            700 -> context.getString(R.string.font_info_weight_bold)
            800 -> context.getString(R.string.font_info_weight_extra_bold)
            900 -> context.getString(R.string.font_info_weight_black)
            else -> context.getString(R.string.font_info_weight_other, w)
        }
    }

    fun getWidthName(context: Context, d: Int): String? {
        return when (d) {
            1 -> context.getString(R.string.font_info_width_ultra_condensed)
            2 -> context.getString(R.string.font_info_width_extra_condensed)
            3 -> context.getString(R.string.font_info_width_condensed)
            4 -> context.getString(R.string.font_info_width_semi_condensed)
            5 -> null
            6 -> context.getString(R.string.font_info_width_semi_expanded)
            7 -> context.getString(R.string.font_info_width_expanded)
            8 -> context.getString(R.string.font_info_width_extra_expanded)
            9 -> context.getString(R.string.font_info_width_ultra_expanded)
            else -> null
        }
    }

    private fun hasWidthName(d: Int): Boolean {
        return d >= 1 && d <= 9 && d != 5
    }
}
