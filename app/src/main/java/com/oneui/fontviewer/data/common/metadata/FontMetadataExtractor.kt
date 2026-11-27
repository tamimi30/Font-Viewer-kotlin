package com.oneui.fontviewer.data.common.metadata

import android.util.Log

import java.io.File

object FontMetadataExtractor {

    private const val TAG = "FontMetadataExtractor"

    fun extractFontName(fontFile: File?, ttcIndex: Int): String {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist")
            return "Unknown Font"
        }

        try {
            val fontName = FontMetadataParser.extractFontName(fontFile, ttcIndex)

            if (fontName != null && fontName.isNotEmpty() && fontName != "Unknown Font") {
                Log.d(TAG, "Successfully extracted font name: $fontName")
                return fontName
            }
        } catch (e: Exception) {
            Log.w(TAG, "FontMetadataParser.extractFontName failed: " + e.message)
        }

        Log.d(TAG, "Could not extract real name, returning Unknown Font")
        return "Unknown Font"
    }

    fun extractMetadataWithTtcIndex(fontFile: File?, ttcIndex: Int): Map<String, String> {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist")
            return HashMap()
        }

        try {
            val metadata = FontMetadataParser.extractMetadataWithTtcIndex(fontFile, ttcIndex)

            if (metadata != null && metadata.isNotEmpty() &&
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))
            ) {
                Log.d(TAG, "Successfully extracted metadata with TTC index: $ttcIndex")
                return metadata
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract metadata with TTC index: " + e.message)
        }

        try {
            val metadata = FontMetadataParser.extractMetadata(fontFile)

            if (metadata != null && metadata.isNotEmpty() &&
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))
            ) {
                Log.d(TAG, "Successfully extracted metadata using fallback method")
                return metadata
            }
        } catch (e: Exception) {
            Log.w(TAG, "FontMetadataParser.extractMetadata failed: " + e.message)
        }

        Log.w(TAG, "Failed to extract metadata, returning empty map")
        return HashMap()
    }

}
