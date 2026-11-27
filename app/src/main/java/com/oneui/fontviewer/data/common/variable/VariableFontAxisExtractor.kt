package com.oneui.fontviewer.data.common.variable

import android.content.Context
import android.graphics.fonts.Font
import android.graphics.fonts.FontVariationAxis


import com.oneui.fontviewer.R

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads variable-font data from font files: fvar axes, named instances,
 * axis ranges and default values.
 */
object VariableFontAxisExtractor {

    private const val TAG = "VariableFontAxisExtractor"

    // ذاكرة مؤقتة لمحاور fvar: كان جدول fvar يُفتح ويُقرأ نحو 12 مرة لكل خط متغير، فصار يُقرأ مرة واحدة ثم تُخدم بقية الطلبات منها.
    // المفتاح يتضمن المسار وفهرس TTC وتاريخ التعديل والحجم، فاذا تغيّر الملف تُقرأ بياناته من جديد
    private val FVAR_CACHE: MutableMap<String, Map<String, FloatArray>> =
        java.util.concurrent.ConcurrentHashMap()

    // ذاكرة مؤقتة لنتيجة isVariableFont: كانت تبني Font.Builder وتقرأ الملف مرتين لكل خط متغير (مرة مباشرة ومرة داخل
    // extractVariableInstances). نخزّن النتيجة الموجبة (الخط متغير) فقط، حتى لا يعلق false ناتج عن خطأ قراءة عابر.
    // المفتاح يتضمن تاريخ التعديل والحجم، فاذا تغيّر الملف يُفحص من جديد
    private val VARIABLE_FONT_CACHE: MutableMap<String, Boolean> =
        java.util.concurrent.ConcurrentHashMap()

    // وسوم محاور الخط المتغير المدعومة.
    // ملاحظة تقنية مهمة: wght, wdth, ital محاور مسجّلة رسمياً في OpenType لذلك وسومها بأحرف صغيرة،
    // بينما GRAD, ROND, MONO محاور غير رسمية (foundry-defined) ويجب أن تكون وسومها بأحرف كبيرة بالكامل،
    // هذا جزء من بنية الخط نفسه (كما هو مخزن فعلياً داخل جدول fvar) وليس مجرد اختيار تسمية.
    const val AXIS_WGHT = "wght"
    const val AXIS_WDTH = "wdth"
    const val AXIS_ITAL = "ital"
    const val AXIS_GRAD = "GRAD"
    const val AXIS_ROND = "ROND"
    const val AXIS_MONO = "MONO"

    class VariableInstance(
        var name: String,
        var tag: String,
        var value: Float
    ) {

        override fun toString(): String {
            return name
        }
    }

    // NOTE(kotlin-migration): fontFile is nullable because the Java code explicitly handled null.
    fun isVariableFont(fontFile: File?, ttcIndex: Int): Boolean {


        if (fontFile == null || !fontFile.exists()) {
            return false
        }

        val cacheKey = fontFile.absolutePath + '|' + ttcIndex + '|' +
                fontFile.lastModified() + '|' + fontFile.length()
        if (VARIABLE_FONT_CACHE.containsKey(cacheKey)) {
            return true
        }

        val result = detectVariableFont(fontFile, ttcIndex)
        if (result) {
            if (VARIABLE_FONT_CACHE.size >= 64) VARIABLE_FONT_CACHE.clear()
            VARIABLE_FONT_CACHE[cacheKey] = true
        }
        return result
    }

    private fun detectVariableFont(fontFile: File, ttcIndex: Int): Boolean {
        try {
            val axes = getVariationAxes(fontFile, ttcIndex)
            if (axes != null && axes.isNotEmpty()) {
                return true
            }
        } catch (e: Exception) {
        }

        return fvarTableExists(fontFile, ttcIndex)
    }



    private fun getVariationAxes(fontFile: File, ttcIndex: Int): Array<FontVariationAxis>? {
        return try {
            val fontBuilder = Font.Builder(fontFile)
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex)
            }
            val font = fontBuilder.build()
            font.axes
        } catch (e: Exception) {
            null
        }
    }

    private fun fvarTableExists(fontFile: File, ttcIndex: Int): Boolean {
        try {
            return RandomAccessFile(fontFile, "r").use { raf ->
                val header = ByteArray(4)
                raf.read(header)
                val tag = String(header, Charsets.US_ASCII)

                if ("ttcf" == tag) {
                    raf.seek(8)
                    val numFonts = readUInt32(raf)
                    if (ttcIndex >= numFonts) {
                        return@use false
                    }

                    raf.seek((12 + (ttcIndex * 4)).toLong())
                    val fontOffset = readUInt32(raf)
                    return@use checkFvarAtOffset(raf, fontOffset)
                } else {
                    raf.seek(4)
                    val numTables = readUInt16(raf)

                    for (i in 0 until numTables) {
                        raf.seek((12 + i * 16).toLong())
                        val tableTag = ByteArray(4)
                        raf.read(tableTag)
                        val tagStr = String(tableTag, Charsets.US_ASCII)

                        if ("fvar" == tagStr) {
                            return@use true
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
        }
        return false
    }

    private fun checkFvarAtOffset(raf: RandomAccessFile, fontOffset: Long): Boolean {
        try {
            raf.seek(fontOffset + 4)
            val numTables = readUInt16(raf)

            for (i in 0 until numTables) {
                raf.seek(fontOffset + 12 + i * 16)
                val tableTag = ByteArray(4)
                raf.read(tableTag)
                val tagStr = String(tableTag, Charsets.US_ASCII)

                if ("fvar" == tagStr) {
                    return true
                }
            }
        } catch (e: Exception) {
        }
        return false
    }

    fun extractVariableInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()



        if (!isVariableFont(fontFile, ttcIndex)) {
            return instances
        }

        try {
            val range = readWeightRangeFromFvar(fontFile, ttcIndex)
            val minWeight = range[0]
            val maxWeight = range[1]

            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_thin), 100f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_extra_light), 200f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_light), 300f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_regular), 400f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_medium), 500f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_semi_bold), 600f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_bold), 700f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_extra_bold), 800f, minWeight, maxWeight)
            addWeightIfInRange(instances, context.getString(R.string.font_info_weight_black), 900f, minWeight, maxWeight)

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to extract instances", e)
        }

        return instances
    }

    // ════════════════════════════════════════════════════════════
    // محاور إضافية: العرض (wdth)، الميل (ital)، التدرج (GRAD)،
    // الاستدارة (ROND)، والتباعد الأحادي (MONO)
    // ════════════════════════════════════════════════════════════

    /**
     * محور العرض (wdth). يستخدم القيم الشائعة القياسية (نفس نسب usWidthClass في OS/2):
     * Condensed = 75%, Normal = 100%, Expanded = 125% — تماماً كما يحدث مع الوزن (Bold = 700 مثلاً)،
     * وليس الحد الأدنى/الأقصى الفعلي للخط.
     */
    fun extractWidthInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()

        val range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_WDTH)
        if (range == null) {
            return instances
        }

        val minWidth = range[0]
        val maxWidth = range[2]

        addNamedInstanceIfInRange(instances, context.getString(R.string.font_info_width_condensed), AXIS_WDTH, 75f, minWidth, maxWidth)
        addNamedInstanceIfInRange(instances, context.getString(R.string.font_info_width_normal), AXIS_WDTH, 100f, minWidth, maxWidth)
        addNamedInstanceIfInRange(instances, context.getString(R.string.font_info_width_expanded), AXIS_WDTH, 125f, minWidth, maxWidth)

        return instances
    }

    /**
     * محور الميل (ital). محور ثنائي عادة: 0 = Upright، 1 = Italic.
     */
    fun extractItalicInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()

        val range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ITAL)
        if (range == null) {
            return instances
        }

        val minItal = range[0]
        val maxItal = range[2]

        addNamedInstanceIfInRange(instances, context.getString(R.string.font_info_italic_upright), AXIS_ITAL, 0f, minItal, maxItal)
        addNamedInstanceIfInRange(instances, context.getString(R.string.font_info_axis_italic), AXIS_ITAL, 1f, minItal, maxItal)

        return instances
    }

    /**
     * محور التدرج (GRAD). هنا نستخدم القيم الفعلية للخط: Low = الحد الأدنى الذي يدعمه الخط،
     * High = الحد الأقصى الذي يدعمه الخط، Normal = القيمة الافتراضية الفعلية المسجّلة في fvar
     * (وليست 0 دائماً، فبعض الخطوط تجعل قيمتها الافتراضية مختلفة).
     */
    fun extractGradeInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()

        val range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_GRAD)
        if (range == null) {
            return instances
        }

        val minGrade     = range[0]
        val defaultGrade = range[1]
        val maxGrade     = range[2]

        if (minGrade < defaultGrade) {
            instances.add(VariableInstance(context.getString(R.string.font_info_grade_low), AXIS_GRAD, minGrade))
        }
        instances.add(VariableInstance(context.getString(R.string.font_info_width_normal), AXIS_GRAD, defaultGrade))
        if (maxGrade > defaultGrade) {
            instances.add(VariableInstance(context.getString(R.string.font_info_grade_high), AXIS_GRAD, maxGrade))
        }

        return instances
    }

    /**
     * محور الاستدارة (ROND). Sharp = الحد الأدنى الفعلي، Rounded = الحد الأقصى الفعلي للخط.
     */
    fun extractRoundnessInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()

        val range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ROND)
        if (range == null) {
            return instances
        }

        val minRound = range[0]
        val maxRound = range[2]

        if (maxRound > minRound) {
            instances.add(VariableInstance(context.getString(R.string.font_info_roundness_sharp), AXIS_ROND, minRound))
            instances.add(VariableInstance(context.getString(R.string.font_info_roundness_rounded), AXIS_ROND, maxRound))
        }

        return instances
    }

    /**
     * محور التباعد الأحادي (MONO). Proportional = الحد الأدنى (القيمة الأصغر)،
     * Monospaced = الحد الأقصى (القيمة الأكبر) — مرتبة تصاعدياً حسب القيمة كما هو مطلوب.
     */
    fun extractMonoInstances(context: Context, fontFile: File, ttcIndex: Int): MutableList<VariableInstance> {
        val instances = ArrayList<VariableInstance>()

        val range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_MONO)
        if (range == null) {
            return instances
        }

        val minMono = range[0]
        val maxMono = range[2]

        if (maxMono > minMono) {
            instances.add(VariableInstance(context.getString(R.string.font_info_mono_proportional), AXIS_MONO, minMono))
            instances.add(VariableInstance(context.getString(R.string.font_info_mono_monospaced), AXIS_MONO, maxMono))
        }

        return instances
    }

    private fun addNamedInstanceIfInRange(
        instances: MutableList<VariableInstance>, name: String,
        axisTag: String, value: Float,
        min: Float, max: Float
    ) {
        if (value >= min && value <= max) {
            instances.add(VariableInstance(name, axisTag, value))
        }
    }



    private fun readWeightRangeFromFvar(fontFile: File, ttcIndex: Int): FloatArray {
        val defaultRange = floatArrayOf(100f, 900f)

        val axisRange = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_WGHT)
        if (axisRange == null) {
            return defaultRange
        }

        return floatArrayOf(axisRange[0], axisRange[2])
    }

    /**
     * يقرأ الحد الأدنى والقيمة الافتراضية والحد الأقصى لمحور معيّن (بالبحث عن وسمه) من جدول fvar.
     * يعيد null اذا كان هذا المحور غير موجود في الخط (اي أن الخط لا يدعمه).
     */
    private fun readAxisRangeFromFvar(fontFile: File, ttcIndex: Int, targetTag: String): FloatArray? {
        val key = fontFile.absolutePath + '|' + ttcIndex + '|' +
                fontFile.lastModified() + '|' + fontFile.length()
        var axes = FVAR_CACHE[key]
        if (axes == null) {
            axes = parseAllAxesFromFvar(fontFile, ttcIndex)
            if (axes == null) return null          // فشلت القراءة: لا نخزّن
            if (FVAR_CACHE.size >= 64) FVAR_CACHE.clear()
            FVAR_CACHE[key] = axes
        }
        val range = axes[targetTag]
        return range?.clone()
    }

    /**
     * يقرأ جدول fvar مرة واحدة ويعيد كل محاور الخط (وسم المحور -> {min, default, max}).
     * يعيد خريطة فارغة اذا لم يكن في الخط جدول fvar، و null اذا فشلت القراءة.
     */
    private fun parseAllAxesFromFvar(fontFile: File, ttcIndex: Int): Map<String, FloatArray>? {
        val result = java.util.HashMap<String, FloatArray>()
        try {
            return RandomAccessFile(fontFile, "r").use { raf ->
                val header = ByteArray(4)
                raf.read(header)
                var fontOffset = 0L
                if ("ttcf" == String(header, Charsets.US_ASCII)) {
                    raf.seek(8)
                    val numFonts = readUInt32(raf)
                    if (ttcIndex >= numFonts) return@use null
                    raf.seek((12 + (ttcIndex * 4)).toLong())
                    fontOffset = readUInt32(raf)
                }
                raf.seek(fontOffset + 4)
                val numTables = readUInt16(raf)

                var fvarOffset = -1L
                for (i in 0 until numTables) {
                    raf.seek(fontOffset + 12 + i * 16)
                    val t = ByteArray(4)
                    raf.read(t)
                    if ("fvar" == String(t, Charsets.US_ASCII)) {
                        raf.seek(fontOffset + 12 + i * 16 + 8)
                        fvarOffset = readUInt32(raf)
                        break
                    }
                }
                if (fvarOffset == -1L) return@use result    // لا fvar: خريطة فارغة

                raf.seek(fvarOffset + 4)
                val axesArrayOffset = readUInt16(raf)
                raf.seek(fvarOffset + 8)
                val axisCount = readUInt16(raf)
                val axisSize = readUInt16(raf)

                for (i in 0 until axisCount) {
                    raf.seek(fvarOffset + axesArrayOffset + (i * axisSize))
                    val tag = ByteArray(4)
                    raf.read(tag)
                    val min = readFixed(raf)
                    val def = readFixed(raf)
                    val max = readFixed(raf)
                    result.putIfAbsent(String(tag, Charsets.US_ASCII), floatArrayOf(min, def, max))
                }
                result
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to parse fvar", e)
            return null
        }
    }

    /**
     * يعيد القيمة الافتراضية الفعلية لمحور معيّن كما هي مسجّلة في جدول fvar الخاص بالخط،
     * أو fallback في حال تعذّرت القراءة.
     */
    fun readAxisDefaultValue(fontFile: File, ttcIndex: Int, axisTag: String, fallback: Float): Float {
        val range = readAxisRangeFromFvar(fontFile, ttcIndex, axisTag)
        if (range == null) {
            return fallback
        }
        return range[1]
    }

    private fun readUInt16(raf: RandomAccessFile): Int {
        val bytes = ByteArray(2)
        raf.read(bytes)
        return ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    }

    private fun readUInt32(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.read(bytes)
        return ((bytes[0].toInt() and 0xFF).toLong() shl 24) or
               ((bytes[1].toInt() and 0xFF).toLong() shl 16) or
               ((bytes[2].toInt() and 0xFF).toLong() shl 8) or
               (bytes[3].toInt() and 0xFF).toLong()
    }

    private fun readFixed(raf: RandomAccessFile): Float {
        val bytes = ByteArray(4)
        raf.read(bytes)
        val value = ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)
        return value / 65536.0f
    }

    private fun addWeightIfInRange(
        instances: MutableList<VariableInstance>,
        name: String, value: Float,
        min: Float, max: Float
    ) {
        if (value >= min && value <= max) {
            instances.add(VariableInstance(name, "wght", value))
        }
    }
}
