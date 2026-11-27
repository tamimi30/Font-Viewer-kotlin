package com.oneui.fontviewer.data.systemfont

import android.graphics.fonts.Font
import android.graphics.fonts.SystemFonts
import android.os.Build
import android.util.Log

import androidx.annotation.RequiresApi

import java.io.File


class SystemFontManager private constructor() {

    val isSystemFontsAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    @get:RequiresApi(api = Build.VERSION_CODES.Q)
    val systemFonts: List<SystemFontInfo>
        get() {
            if (!isSystemFontsAvailable) {
                Log.w(TAG, "SystemFonts API not available on this Android version")
                return ArrayList()
            }

            val fontInfoList: MutableList<SystemFontInfo> = ArrayList()
            val processedPaths: MutableSet<String> = HashSet()

            try {
                val availableFonts: Set<Font>? = SystemFonts.getAvailableFonts()

                if (availableFonts == null || availableFonts.isEmpty()) {
                    Log.w(TAG, "No system fonts available")
                    return fontInfoList
                }

                Log.d(TAG, "Found " + availableFonts.size + " system fonts")

                for (font in availableFonts) {
                    try {
                        val fontFile: File? = font.file

                        if (fontFile == null || !fontFile.exists()) {
                            continue
                        }

                        val fontPath = fontFile.absolutePath

                        if (processedPaths.contains(fontPath)) {
                            continue
                        }

                        processedPaths.add(fontPath)

                        val fontName = fontFile.name
                        val fileSize = fontFile.length()
                        val lastModified = fontFile.lastModified()

                        var weight = 400
                        var slant = 0
                        var ttcIndex = 0
                        var axes: String? = null

                        try {
                            weight = font.style.weight
                            slant = font.style.slant
                            ttcIndex = font.ttcIndex
                            axes = extractAxesInfo(font)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not extract font style info for: $fontName")
                        }

                        val fontInfo = SystemFontInfo(
                            fontName,
                            fontPath,
                            fileSize,
                            lastModified,
                            weight,
                            slant,
                            ttcIndex,
                            axes
                        )

                        fontInfoList.add(fontInfo)

                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing font: " + e.message)
                    }
                }

                fontInfoList.sortWith { f1, f2 ->
                    f1.name.compareTo(f2.name, ignoreCase = true)
                }

                Log.d(TAG, "Successfully processed " + fontInfoList.size + " unique system fonts")

            } catch (e: Exception) {
                Log.e(TAG, "Error getting system fonts", e)
            }

            return fontInfoList
        }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private fun extractAxesInfo(font: Font): String? {
        try {
            val axes = font.axes
            if (axes != null && axes.size > 0) {
                val axesInfo = StringBuilder()
                for (i in axes.indices) {
                    if (i > 0) axesInfo.append(", ")
                    axesInfo.append(axes[i].tag)
                }
                return axesInfo.toString()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting axes info: " + e.message)
        }
        return null
    }

    companion object {
        private const val TAG = "SystemFontManager"
        private var instance: SystemFontManager? = null

        @Synchronized
        fun getInstance(): SystemFontManager {
            if (instance == null) {
                instance = SystemFontManager()
            }
            return instance!!
        }
    }
}
