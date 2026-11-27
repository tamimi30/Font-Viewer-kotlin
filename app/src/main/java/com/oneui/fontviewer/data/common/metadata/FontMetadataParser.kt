package com.oneui.fontviewer.data.common.metadata

import android.util.Log

import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FontMetadataParser {

    private const val TAG = "FontMetadataParser"

    fun extractMetadataWithTtcIndex(fontFile: File, ttcIndex: Int): Map<String, String>? {
        val out: MutableMap<String, String> = HashMap()

        try {
            fun putName(key: String, nameId: Int) {
                val value = getFontNameDirectWithTtc(fontFile, nameId, ttcIndex)
                if (value != null && !value.isEmpty()) out[key] = value
            }

            putName("Copyright", 0)
            putName("FullName", 4)
            putName("Family", 1)
            putName("SubFamily", 2)
            putName("PostScriptName", 6)
            putName("Version", 5)
            putName("Manufacturer", 8)
            putName("Designer", 9)
            putName("Trademark", 7)
            putName("Description", 10)
            putName("DesignerURL", 12)
            putName("VendorURL", 11)
            putName("LicenseDescription", 13)
            putName("LicenseURL", 14)

            val technicalData = extractTechnicalDataDirectWithTtc(fontFile, ttcIndex)
            out.putAll(technicalData)

        } catch (e: Exception) {
            Log.w(TAG, "Fallback extraction failed: " + e.message)
        }

        return if (out.isEmpty()) null else out
    }

    fun extractMetadata(fontFile: File): Map<String, String>? {
        return extractMetadataWithTtcIndex(fontFile, 0)
    }

    fun extractFontName(fontFile: File, ttcIndex: Int): String {
        var directName = getFontNameDirectWithTtc(fontFile, 4, ttcIndex)
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 4 (Full Name): $directName")
            return directName
        }

        directName = getFontNameDirectWithTtc(fontFile, 1, ttcIndex)
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 1 (Family): $directName")
            return directName
        }

        directName = getFontNameDirectWithTtc(fontFile, 6, ttcIndex)
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 6 (PostScript): $directName")
            return directName
        }

        directName = getFontNameDirectWithTtc(fontFile, 3, ttcIndex)
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 3 (Unique ID): $directName")
            return directName
        }

        directName = getFontNameDirectWithTtc(fontFile, 2, ttcIndex)
        if (directName != null && !directName.isEmpty() && isValidFontName(directName)) {
            Log.d(TAG, "Extracted from nameId 2 (SubFamily): $directName")
            return directName
        }

        Log.w(TAG, "Failed to extract font name from: " + fontFile.name)
        return "Unknown Font"
    }

    private fun getFontNameDirectWithTtc(fontFile: File, nameId: Int, ttcIndex: Int): String? {
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(fontFile, "r")

            val headerBytes = ByteArray(4)
            raf.read(headerBytes)
            val headerTag = String(headerBytes, Charsets.US_ASCII)

            var fontOffset = 0L

            if ("ttcf" == headerTag) {
                raf.skipBytes(4)
                val numFonts = readUInt32(raf)

                if (ttcIndex >= numFonts) {
                    Log.w(TAG, "TTC index $ttcIndex exceeds number of fonts: $numFonts")
                    return null
                }

                raf.seek((12 + (ttcIndex * 4)).toLong())
                fontOffset = readUInt32(raf)
            }

            raf.seek(fontOffset + 4)
            val numTables = raf.readUnsignedShort()
            raf.skipBytes(6)

            var nameTableOffset = -1L
            for (i in 0 until numTables) {
                val tag = ByteArray(4)
                raf.read(tag)
                val tagStr = String(tag, Charsets.ISO_8859_1)

                raf.skipBytes(4)
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.skipBytes(4)

                if ("name" == tagStr) {
                    nameTableOffset = offset
                    break
                }
            }

            if (nameTableOffset == -1L) {
                return null
            }

            raf.seek(nameTableOffset)
            raf.readUnsignedShort() // format
            val count = raf.readUnsignedShort()
            val stringOffset = raf.readUnsignedShort()

            var bestName: String? = null
            var bestPriority = Int.MAX_VALUE

            for (i in 0 until count) {
                val platformID = raf.readUnsignedShort()
                val encodingID = raf.readUnsignedShort()
                val languageID = raf.readUnsignedShort()
                val nameID = raf.readUnsignedShort()
                val length = raf.readUnsignedShort()
                val offset = raf.readUnsignedShort()

                if (nameID == nameId && length > 0) {
                    val priority = getPlatformPriority(platformID, encodingID, languageID)

                    if (priority < bestPriority) {
                        val currentPos = raf.filePointer
                        raf.seek(nameTableOffset + stringOffset + offset)

                        val nameBytes = ByteArray(length)
                        raf.readFully(nameBytes)

                        val name = decodeNameBytes(nameBytes, platformID, encodingID)
                        if (name != null && !name.trim().isEmpty() && isValidFontName(name)) {
                            bestName = name.trim()
                            bestPriority = priority
                        }

                        raf.seek(currentPos)

                        if (bestPriority == 0) {
                            break
                        }
                    }
                }
            }

            return bestName

        } catch (e: Exception) {
            Log.w(TAG, "getFontNameDirectWithTtc failed: " + e.message)
            return null
        } finally {
            if (raf != null) {
                try {
                    raf.close()
                } catch (e: Exception) {
                }
            }
        }
    }

    private fun extractTechnicalDataDirectWithTtc(fontFile: File, ttcIndex: Int): Map<String, String> {
        val data: MutableMap<String, String> = HashMap()
        var raf: RandomAccessFile? = null

        try {
            raf = RandomAccessFile(fontFile, "r")

            val headerBytes = ByteArray(4)
            raf.read(headerBytes)
            val headerTag = String(headerBytes, Charsets.US_ASCII)

            var fontOffset = 0L

            if ("ttcf" == headerTag) {
                raf.skipBytes(4)
                val numFonts = readUInt32(raf)

                if (ttcIndex >= numFonts) {
                    return data
                }

                raf.seek((12 + (ttcIndex * 4)).toLong())
                fontOffset = readUInt32(raf)
            }

            raf.seek(fontOffset + 4)
            val numTables = raf.readUnsignedShort()
            raf.skipBytes(6)
            var os2TableOffset = -1L
            var headTableOffset = -1L
            var maxpTableOffset = -1L
            var cmapTableOffset = -1L
            var fvarTableOffset = -1L
            var isHinted = false

            for (i in 0 until numTables) {
                val tag = ByteArray(4)
                raf.read(tag)
                val tagStr = String(tag, Charsets.ISO_8859_1)

                raf.skipBytes(4)
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.readInt()

                if ("OS/2" == tagStr) {
                    os2TableOffset = offset
                } else if ("head" == tagStr) {
                    headTableOffset = offset
                } else if ("maxp" == tagStr) {
                    maxpTableOffset = offset
                } else if ("cmap" == tagStr) {
                    cmapTableOffset = offset
                } else if ("fvar" == tagStr) {
                    fvarTableOffset = offset
                } else if ("fpgm" == tagStr || "prep" == tagStr) {
                    isHinted = true
                }
            }

            if (isHinted) {
                data["Hinted"] = "Improved"
            } else {
                data["Hinted"] = "Not improved"
            }

            if (os2TableOffset != -1L) {
                try {
                    raf.seek(os2TableOffset)
                    raf.readUnsignedShort()
                    raf.skipBytes(2)
                    val weightClass = raf.readUnsignedShort()
                    val widthClass = raf.readUnsignedShort()

                    data["Weight"] = weightClass.toString()
                    data["Width"] = widthClass.toString()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read OS/2 table: " + e.message)
                }
            }

            if (headTableOffset != -1L) {
                try {
                    raf.seek(headTableOffset)
                    raf.skipBytes(4)
                    raf.skipBytes(4)
                    raf.skipBytes(4)
                    raf.skipBytes(4)
                    raf.skipBytes(2)
                    val unitsPerEm = raf.readUnsignedShort()
                    data["UnitsPerEm"] = unitsPerEm.toString()

                    val created = raf.readLong()
                    val modified = raf.readLong()

                    try {
                        val createdMs = (created - 2082844800L) * 1000L
                        val createdDate = Date(createdMs)
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        data["CreatedDate"] = sdf.format(createdDate)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse created date: " + e.message)
                    }

                    try {
                        val modifiedMs = (modified - 2082844800L) * 1000L
                        val modifiedDate = Date(modifiedMs)
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        data["ModifiedDate"] = sdf.format(modifiedDate)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse modified date: " + e.message)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read head table: " + e.message)
                }
            }

            if (maxpTableOffset != -1L) {
                try {
                    raf.seek(maxpTableOffset)
                    raf.skipBytes(4)
                    val numGlyphs = raf.readUnsignedShort()
                    data["GlyphCount"] = numGlyphs.toString()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read maxp table: " + e.message)
                }
            }

            if (cmapTableOffset != -1L) {
                try {
                    val scripts = detectSupportedScriptsDirect(raf, cmapTableOffset)
                    if (!scripts.isEmpty()) {
                        val scriptsStr = StringBuilder()
                        for (script in scripts) {
                            if (scriptsStr.length > 0) scriptsStr.append(", ")
                            scriptsStr.append(script)
                        }
                        data["SupportedScripts"] = scriptsStr.toString()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to detect scripts: " + e.message)
                }
            }

            if (fvarTableOffset != -1L) {
                try {
                    val axes = extractVariableAxes(raf, fvarTableOffset, fontFile, ttcIndex)
                    if (axes != null && !axes.isEmpty()) {
                        data["VariableAxes"] = axes
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read fvar table: " + e.message)
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "extractTechnicalDataDirectWithTtc failed: " + e.message)
        } finally {
            if (raf != null) {
                try {
                    raf.close()
                } catch (e: Exception) {
                }
            }
        }

        return data
    }

    // قراءة المحاور المتغيرة من جدول fvar: كل محور في سطر بصيغة "tag<TAB>اسم المحور في الخط"
    // (الاسم قد يكون فارغاً)، وتحويلها لأسماء مترجمة يتم لاحقاً في FontInfoDialog
    @Throws(Exception::class)
    private fun extractVariableAxes(raf: RandomAccessFile, fvarOffset: Long, fontFile: File, ttcIndex: Int): String? {
        raf.seek(fvarOffset)
        raf.skipBytes(4)
        val axesArrayOffset = raf.readUnsignedShort()
        raf.skipBytes(2)
        val axisCount = raf.readUnsignedShort()
        val axisSize = raf.readUnsignedShort()

        if (axisCount <= 0 || axisCount > 64 || axisSize < 20) {
            return null
        }

        val tags = arrayOfNulls<String>(axisCount)
        val nameIds = IntArray(axisCount)

        for (i in 0 until axisCount) {
            raf.seek(fvarOffset + axesArrayOffset + i.toLong() * axisSize)

            val tagBytes = ByteArray(4)
            raf.readFully(tagBytes)
            tags[i] = String(tagBytes, Charsets.ISO_8859_1).trim()

            raf.skipBytes(14)
            nameIds[i] = raf.readUnsignedShort()
        }

        val axes = StringBuilder()
        for (i in 0 until axisCount) {
            val tag = tags[i]!!
            if (tag.isEmpty()) {
                continue
            }

            var name = ""
            if (nameIds[i] >= 256) {
                val fontName = getFontNameDirectWithTtc(fontFile, nameIds[i], ttcIndex)
                if (fontName != null) {
                    name = fontName.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()
                }
            }

            if (axes.length > 0) axes.append('\n')
            axes.append(tag).append('\t').append(name)
        }

        return axes.toString()
    }

    @Throws(Exception::class)
    private fun readUInt32(raf: RandomAccessFile): Long {
        val bytes = ByteArray(4)
        raf.read(bytes)
        return ((bytes[0].toLong() and 0xFFL) shl 24) or
                ((bytes[1].toLong() and 0xFFL) shl 16) or
                ((bytes[2].toLong() and 0xFFL) shl 8) or
                (bytes[3].toLong() and 0xFFL)
    }

    private fun detectSupportedScriptsDirect(raf: RandomAccessFile, cmapOffset: Long): Set<String> {
        var scripts: Set<String> = HashSet()

        try {
            raf.seek(cmapOffset)
            raf.readUnsignedShort()
            val numTables = raf.readUnsignedShort()

            var bestSubtableOffset = -1L
            var bestPriority = Int.MAX_VALUE

            for (i in 0 until numTables) {
                val platformID = raf.readUnsignedShort()
                val encodingID = raf.readUnsignedShort()
                val offset = raf.readInt().toLong() and 0xFFFFFFFFL

                var priority = 10
                if (platformID == 3 && encodingID == 10) {
                    priority = 0
                } else if (platformID == 3 && encodingID == 1) {
                    priority = 1
                } else if (platformID == 0 && encodingID == 3) {
                    priority = 2
                }

                if (priority < bestPriority) {
                    bestSubtableOffset = cmapOffset + offset
                    bestPriority = priority
                }
            }

            if (bestSubtableOffset != -1L) {
                raf.seek(bestSubtableOffset)
                val format = raf.readUnsignedShort()

                if (format == 4) {
                    scripts = detectScriptsFormat4(raf)
                } else if (format == 12) {
                    scripts = detectScriptsFormat12(raf)
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "detectSupportedScriptsDirect failed: " + e.message)
        }

        return scripts
    }

    private fun addScriptsForRange(scripts: MutableSet<String>, start: Long, end: Long) {
        if (start <= 0x007A && end >= 0x0041) scripts.add("Latin")
        if (start <= 0x06FF && end >= 0x0600) scripts.add("Arabic")
        if (start <= 0x04FF && end >= 0x0400) scripts.add("Cyrillic")
        if (start <= 0x03FF && end >= 0x0370) scripts.add("Greek")
        if (start <= 0x05FF && end >= 0x0590) scripts.add("Hebrew")
        if (start <= 0x9FFF && end >= 0x4E00) scripts.add("CJK")
        if (start <= 0x097F && end >= 0x0900) scripts.add("Devanagari")
        if (start <= 0x0E7F && end >= 0x0E00) scripts.add("Thai")
        if (start <= 0xD7AF && end >= 0xAC00) scripts.add("Hangul")
        if (start <= 0x309F && end >= 0x3040) scripts.add("Hiragana")
        if (start <= 0x30FF && end >= 0x30A0) scripts.add("Katakana")
    }

    private fun detectScriptsFormat4(raf: RandomAccessFile): Set<String> {
        val scripts: MutableSet<String> = HashSet()

        try {
            raf.skipBytes(2)
            raf.skipBytes(2)
            val segCountX2 = raf.readUnsignedShort()
            val segCount = segCountX2 / 2

            raf.skipBytes(6)

            val endCodes = IntArray(segCount)
            for (i in 0 until segCount) {
                endCodes[i] = raf.readUnsignedShort()
            }

            raf.skipBytes(2)

            val startCodes = IntArray(segCount)
            for (i in 0 until segCount) {
                startCodes[i] = raf.readUnsignedShort()
            }

            for (i in 0 until segCount) {
                addScriptsForRange(scripts, startCodes[i].toLong(), endCodes[i].toLong())
            }

        } catch (e: Exception) {
            Log.w(TAG, "detectScriptsFormat4 failed: " + e.message)
        }

        return scripts
    }

    private fun detectScriptsFormat12(raf: RandomAccessFile): Set<String> {
        val scripts: MutableSet<String> = HashSet()

        try {
            raf.skipBytes(2)
            raf.skipBytes(4)
            raf.skipBytes(4)
            val numGroups = raf.readInt().toLong() and 0xFFFFFFFFL

            var i = 0L
            while (i < numGroups && i < 1000) {
                val startCode = raf.readInt().toLong() and 0xFFFFFFFFL
                val endCode = raf.readInt().toLong() and 0xFFFFFFFFL
                raf.skipBytes(4)

                addScriptsForRange(scripts, startCode, endCode)
                i++
            }

        } catch (e: Exception) {
            Log.w(TAG, "detectScriptsFormat12 failed: " + e.message)
        }

        return scripts
    }

    private fun decodeNameBytes(bytes: ByteArray, platformID: Int, encodingID: Int): String? {
        try {
            val charset: String = if (platformID == 3) {
                "UTF-16BE"
            } else if (platformID == 1) {
                if (encodingID == 0) {
                    "MacRoman"
                } else {
                    "UTF-16BE"
                }
            } else if (platformID == 0) {
                "UTF-16BE"
            } else {
                "UTF-8"
            }

            return String(bytes, charset(charset))
        } catch (e: Exception) {
            try {
                return String(bytes, Charsets.ISO_8859_1)
            } catch (ex: Exception) {
                return null
            }
        }
    }

    private fun getPlatformPriority(platformId: Int, encodingId: Int, languageId: Int): Int {
        if (platformId == 3 && encodingId == 1 && languageId == 0x0409) {
            return 0
        }
        if (platformId == 3 && encodingId == 1) {
            return 1
        }
        if (platformId == 0 && encodingId == 3) {
            return 2
        }
        if (platformId == 1 && encodingId == 0) {
            return 3
        }
        if (platformId == 3) {
            return 4
        }
        if (platformId == 0) {
            return 5
        }
        if (platformId == 1) {
            return 6
        }
        return 10
    }

    private fun isValidFontName(rawName: String?): Boolean {
        if (rawName == null || rawName.trim().isEmpty()) {
            return false
        }

        val name = rawName.trim()

        if (name.length < 2) {
            return false
        }

        var controlChars = 0
        var validChars = 0
        val totalChars = name.length

        for (i in 0 until totalChars) {
            val c = name[i]
            val code = c.code

            if (code < 32 || (code >= 127 && code < 160)) {
                controlChars++
                continue
            }

            if ((c in 'A'..'Z') || (c in 'a'..'z') ||
                (c in '0'..'9') || c == ' ' || c == '-' || c == '_' ||
                c == '.' || c == '\'' || c == ',' || code >= 128
            ) {
                validChars++
            }
        }

        if (controlChars == totalChars) {
            return false
        }

        return validChars > 0 && (validChars * 100 / totalChars) >= 30
    }
}
