package com.oneui.fontviewer.ui.dialog

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

import androidx.appcompat.app.AlertDialog

import java.util.regex.Pattern

import com.oneui.fontviewer.R

class FontInfoDialog(
    private val context: Context,
    private val metadata: Map<String, String?>?,
    private val originalFileName: String?,
    private val originalPath: String?
) {

    fun show() {
        if (metadata == null || metadata.isEmpty()) {
            showNoMetadataDialog()
            return
        }

        val orderedKeys = arrayOf(
            "FullName",
            "PostScriptName",
            "Family",
            "SubFamily",
            "Weight",
            "Width",
            "Hinted",
            "SupportedScripts",
            "GlyphCount",
            "UnitsPerEm",
            "VariableAxes",
            "Version",
            "ModifiedDate",
            "CreatedDate",
            "Designer",
            "DesignerURL",
            "Manufacturer",
            "Copyright",
            "VendorURL",
            "Trademark",
            "Description",
            "LicenseDescription",
            "LicenseURL",
            "FileName",
            "Path"
        )

        val displayNameResIds = intArrayOf(
            R.string.font_info_label_full_name,
            R.string.font_info_label_post_script_name,
            R.string.font_info_label_family,
            R.string.font_info_label_sub_family,
            R.string.font_info_label_weight,
            R.string.font_info_label_width,
            R.string.font_info_label_hinted,
            R.string.font_info_label_supported_scripts,
            R.string.font_info_label_glyph_count,
            R.string.font_info_label_units_per_em,
            R.string.font_info_label_variable_axes,
            R.string.font_info_label_version,
            R.string.font_info_label_modified_date,
            R.string.font_info_label_created_date,
            R.string.font_info_label_designer,
            R.string.font_info_label_designer_url,
            R.string.font_info_label_manufacturer,
            R.string.font_info_label_copyright,
            R.string.font_info_label_vendor_url,
            R.string.font_info_label_trademark,
            R.string.font_info_label_description,
            R.string.font_info_label_license_description,
            R.string.font_info_label_license_url,
            R.string.font_info_label_file_name,
            R.string.font_info_label_path
        )

        val fullName = metadata["FullName"]
        val family = metadata["Family"]
        val dialogTitle: String = if (!fullName.isNullOrEmpty()) {
            fullName
        } else if (!family.isNullOrEmpty()) {
            family
        } else {
            context.getString(R.string.font_viewer_select_font)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setMessage(" ")
            .setPositiveButton(android.R.string.ok, null)
            .create()

        dialog.show()

        val messageView = dialog.findViewById<TextView>(android.R.id.message)
        if (messageView == null || messageView.parent !is ViewGroup) {
            return
        }

        val scrollHost = messageView.parent as ViewGroup
        scrollHost.removeView(messageView)

        val dialogContext = dialog.context
        val inflater = LayoutInflater.from(dialogContext)

        val itemsContainer = LinearLayout(dialogContext)
        itemsContainer.orientation = LinearLayout.VERTICAL
        itemsContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        var hasContent = false

        for (i in orderedKeys.indices) {
            val key = orderedKeys[i]
            val displayName = context.getString(displayNameResIds[i])
            var value: String? = metadata[key]

            if ("FileName" == key && originalFileName != null) {
                value = originalFileName
            } else if ("Path" == key && originalPath != null) {
                value = originalPath
            }

            if (value == null || value.isEmpty()) {
                continue
            }

            // ننظّف القيمة: نشيل فواصل الأسطر، ونستبدل الرموز النادرة
            // (شرطة طويلة، علامات تنصيص مُقوّسة...) برموز عادية يدعمها أي خط
            value = localizeMetadataValue(key, value)
            value = sanitizeMetadataValue(value)

            if ("Version" == key) {
                value = cleanVersionString(value)
            }

            val itemView = inflater.inflate(R.layout.dialog_font_info_item, itemsContainer, false)
            val labelView = itemView.findViewById<TextView>(R.id.font_info_label)
            val valueView = itemView.findViewById<TextView>(R.id.font_info_value)

            labelView.text = displayName

            if (isLinklessField(key)) {
                valueView.autoLinkMask = 0
            }
            valueView.text = value
            valueView.movementMethod = LinkMovementMethod.getInstance()

            itemsContainer.addView(itemView)
            hasContent = true
        }

        if (!hasContent) {
            val emptyView = TextView(dialogContext)
            emptyView.text = "No metadata available."
            val pad = (16 * dialogContext.resources.displayMetrics.density).toInt()
            emptyView.setPadding(pad, pad, pad, pad)
            itemsContainer.addView(emptyView)
        }

        scrollHost.addView(itemsContainer)
    }

    private fun showNoMetadataDialog() {
        AlertDialog.Builder(context)
            .setTitle("Font Information")
            .setMessage("No metadata available.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // تحويل القيم المحدودة (الوزن، العرض، تحسين الوضوح، اللغات المدعومة، المحاور) لسلاسل نصية حسب لغة التطبيق
    // بدون الاعتماد على خدمة الترجمة، وبقية الحقول تُعرض كما هي
    private fun localizeMetadataValue(key: String, value: String): String {
        return when (key) {
            "Weight" -> localizeWeight(value)
            "Width" -> localizeWidth(value)
            "Hinted" -> localizeHinted(value)
            "SupportedScripts" -> localizeScripts(value)
            "VariableAxes" -> localizeAxes(value)
            "Path" -> localizePath(value)
            else -> value
        }
    }

    // ايضاً مسار الملف: نستبدل storage/emulated/0 بكلمة "الذاكرة الداخلية"، ومعرّف بطاقة الذاكرة (الارقام) بكلمة "بطاقة SD"،
    // حسب لغة التطبيق، وبقية المسار (المجلدات واسم الملف) كما هي
    private fun localizePath(path: String): String {
        if (path.isEmpty()) {
            return path
        }

        val internal = INTERNAL_STORAGE_PATH.matcher(path)
        if (internal.matches()) {
            return buildLocalizedPath(R.string.storage_internal, internal.group(1))
        }

        val sdCard = SD_CARD_PATH.matcher(path)
        if (sdCard.matches()) {
            return buildLocalizedPath(R.string.storage_sd_card, sdCard.group(1))
        }

        val document = STORAGE_DOCUMENT_URI.matcher(path)
        if (document.matches()) {
            val nameResId = if ("primary".equals(document.group(1), ignoreCase = true))
                R.string.storage_internal
            else
                R.string.storage_sd_card
            return buildLocalizedPath(nameResId, document.group(2))
        }

        return path
    }

    private fun buildLocalizedPath(storageNameResId: Int, relativePath: String?): String {
        val storageName = context.getString(storageNameResId)
        if (relativePath == null || relativePath.isEmpty()) {
            return storageName
        }
        return "$storageName/$relativePath"
    }

    private fun localizeWeight(value: String): String {
        val weightClass: Int
        try {
            weightClass = Integer.parseInt(value.trim())
        } catch (e: NumberFormatException) {
            return value
        }

        return when (weightClass) {
            100 -> context.getString(R.string.font_info_weight_thin)
            200 -> context.getString(R.string.font_info_weight_extra_light)
            300 -> context.getString(R.string.font_info_weight_light)
            400 -> context.getString(R.string.font_info_weight_regular)
            500 -> context.getString(R.string.font_info_weight_medium)
            600 -> context.getString(R.string.font_info_weight_semi_bold)
            700 -> context.getString(R.string.font_info_weight_bold)
            800 -> context.getString(R.string.font_info_weight_extra_bold)
            900 -> context.getString(R.string.font_info_weight_black)
            else -> context.getString(R.string.font_info_weight_other, weightClass)
        }
    }

    private fun localizeWidth(value: String): String {
        val widthClass: Int
        try {
            widthClass = Integer.parseInt(value.trim())
        } catch (e: NumberFormatException) {
            return value
        }

        return when (widthClass) {
            1 -> context.getString(R.string.font_info_width_ultra_condensed)
            2 -> context.getString(R.string.font_info_width_extra_condensed)
            3 -> context.getString(R.string.font_info_width_condensed)
            4 -> context.getString(R.string.font_info_width_semi_condensed)
            5 -> context.getString(R.string.font_info_width_normal)
            6 -> context.getString(R.string.font_info_width_semi_expanded)
            7 -> context.getString(R.string.font_info_width_expanded)
            8 -> context.getString(R.string.font_info_width_extra_expanded)
            9 -> context.getString(R.string.font_info_width_ultra_expanded)
            else -> context.getString(R.string.font_info_width_other, widthClass)
        }
    }

    private fun localizeHinted(value: String): String {
        if ("Improved".equals(value, ignoreCase = true)) {
            return context.getString(R.string.font_info_hinted_improved)
        } else if ("Not improved".equals(value, ignoreCase = true)) {
            return context.getString(R.string.font_info_hinted_not_improved)
        }
        return value
    }

    private fun localizeScripts(value: String): String {
        val separator = context.getString(R.string.font_info_list_separator)
        val result = StringBuilder()

        for (rawScript in value.split(",")) {
            val script = rawScript.trim()
            if (script.isEmpty()) {
                continue
            }

            val resId = getScriptNameResId(script)
            if (result.length > 0) {
                result.append(separator)
            }
            result.append(if (resId != 0) context.getString(resId) else script)
        }
        return result.toString()
    }

    // كل سطر بصيغة "tag<TAB>اسم المحور في الخط": نستخدم السلسلة الثابتة إن وُجدت،
    // وإلا اسم الخط نفسه (بدون ترجمة)، وإلا الرمز
    private fun localizeAxes(value: String): String {
        val separator = context.getString(R.string.font_info_list_separator)
        val result = StringBuilder()

        for (line in value.split("\n")) {
            val parts = line.split("\t")
            val tag = parts[0].trim()
            if (tag.isEmpty()) {
                continue
            }

            val fontName = if (parts.size > 1) parts[1].trim() else ""
            val resId = getAxisNameResId(tag)

            val name: String
            if (resId != 0) {
                name = context.getString(resId)
            } else if (!fontName.isEmpty()) {
                name = fontName
            } else {
                name = tag
            }

            if (result.length > 0) {
                result.append(separator)
            }
            result.append(name)
        }
        return result.toString()
    }

    private fun cleanVersionString(version: String): String {
        if (version.isEmpty()) {
            return version
        }

        val lowerVersion = version.lowercase().trim()
        if (lowerVersion.startsWith("version ")) {
            return version.substring(8).trim()
        }

        return version
    }

    companion object {
        // مسارات الذاكرة الداخلية: /storage/emulated/0 و /storage/self/primary و /sdcard ... والباقي بعدها هو المسار النسبي
        private val INTERNAL_STORAGE_PATH: Pattern = Pattern.compile(
            "^/(?:storage/emulated/\\d+|storage/self/primary|sdcard|mnt/sdcard|storage/sdcard0|data/media/\\d+)(?:/(.*))?$"
        )

        // مسارات بطاقة الذاكرة: /storage/XXXX-XXXX (أي مجلد داخل /storage غير emulated و self) أو /mnt/media_rw/XXXX-XXXX
        private val SD_CARD_PATH: Pattern = Pattern.compile(
            "^/(?:storage/(?!emulated(?:/|$)|self(?:/|$))[^/]+|mnt/media_rw/[^/]+)(?:/(.*))?$"
        )

        // اذا فُتح الخط عبر رابط content:// ولم يُعثر على مساره الحقيقي: primary يعني الذاكرة الداخلية وغيره (XXXX-XXXX) بطاقة الذاكرة
        private val STORAGE_DOCUMENT_URI: Pattern = Pattern.compile(
            "^content://com\\.android\\.externalstorage\\.documents/(?:tree/.*?/)?document/([^:/]+):(.*)$"
        )

        private fun getScriptNameResId(script: String): Int {
            return when (script) {
                "Latin" -> R.string.font_info_script_latin
                "Arabic" -> R.string.font_info_script_arabic
                "Cyrillic" -> R.string.font_info_script_cyrillic
                "Greek" -> R.string.font_info_script_greek
                "Hebrew" -> R.string.font_info_script_hebrew
                "CJK" -> R.string.font_info_script_cjk
                "Devanagari" -> R.string.font_info_script_devanagari
                "Thai" -> R.string.font_info_script_thai
                "Hangul" -> R.string.font_info_script_hangul
                "Hiragana" -> R.string.font_info_script_hiragana
                "Katakana" -> R.string.font_info_script_katakana
                else -> 0
            }
        }

        private fun getAxisNameResId(tag: String): Int {
            return when (tag) {
                "wght" -> R.string.font_info_axis_weight
                "wdth" -> R.string.font_info_axis_width
                "ital" -> R.string.font_info_axis_italic
                "slnt" -> R.string.font_info_axis_slant
                "opsz" -> R.string.font_info_axis_optical_size
                "GRAD" -> R.string.font_info_axis_grade
                "XTRA" -> R.string.font_info_axis_counter_width
                "XOPQ" -> R.string.font_info_axis_thick_stroke
                "YOPQ" -> R.string.font_info_axis_thin_stroke
                "YTLC" -> R.string.font_info_axis_lowercase_height
                "YTUC" -> R.string.font_info_axis_uppercase_height
                "YTAS" -> R.string.font_info_axis_ascender_height
                "YTDE" -> R.string.font_info_axis_descender_depth
                "YTFI" -> R.string.font_info_axis_figure_height
                "CASL" -> R.string.font_info_axis_casual
                "CRSV" -> R.string.font_info_axis_cursive
                "MONO" -> R.string.font_info_axis_monospace
                "ROND" -> R.string.font_info_axis_roundness
                "SOFT" -> R.string.font_info_axis_softness
                "WONK" -> R.string.font_info_axis_wonky
                "FILL" -> R.string.font_info_axis_fill
                "ELSH" -> R.string.font_info_axis_element_shape
                "ELXP" -> R.string.font_info_axis_element_expansion
                "MORF" -> R.string.font_info_axis_morph
                "SHLN" -> R.string.font_info_axis_shadow_length
                else -> 0
            }
        }

        private fun sanitizeMetadataValue(value: String): String {
            return value
                .replace("\r\n", " ")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("—", "-")
                .replace("–", "-")
                .replace("‘", "'")
                .replace("’", "'")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("…", "...")
                .replace("•", "-")
                .replace(" ", " ")
                .replace("​", "")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        private fun isLinklessField(key: String): Boolean {
            return when (key) {
                "FullName",
                "PostScriptName",
                "Family",
                "SubFamily",
                "Weight",
                "Width",
                "Hinted",
                "SupportedScripts",
                "GlyphCount",
                "UnitsPerEm",
                "VariableAxes",
                "Version",
                "ModifiedDate",
                "CreatedDate",
                "FileName",
                "Path" -> true
                else -> false
            }
        }
    }
}
