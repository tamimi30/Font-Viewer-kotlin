package com.oneui.fontviewer.data.fontviewer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import io.reactivex.rxjava3.schedulers.Schedulers

import com.oneui.fontviewer.data.AppDataStore
import com.oneui.fontviewer.data.common.variable.VariableFontAxisExtractor

class FontViewerPreferenceManager(context: Context) {

    private val dataStore: AppDataStore = AppDataStore.getInstance(context)
    private val originalPathPrefs: SharedPreferences = context.applicationContext
        .getSharedPreferences("font_viewer_original_path", Context.MODE_PRIVATE)

    fun saveLastViewedFontOriginalPath(originalPath: String?) {
        originalPathPrefs.edit().putString("original_path", originalPath).apply()
    }

    val lastViewedFontOriginalPath: String?
        get() = originalPathPrefs.getString("original_path", null)

    fun saveLastViewedFont(fontPath: String?, fileName: String?, realName: String?) {
        if (fontPath == null) {
            Log.w(TAG, "Attempted to save null font path")
            return
        }

        dataStore.setLastViewedFont(fontPath, fileName, realName)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved last viewed font: $fontPath") },
                { error -> Log.e(TAG, "Error saving font: " + error.message) }
            )
    }

    val lastViewedFontPath: String?
        get() {
            return try {
                dataStore.viewerFontPath.blockingFirst()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting font path: " + e.message)
                null
            }
        }

    val lastViewedFontFileName: String?
        get() {
            return try {
                dataStore.viewerFileName.blockingFirst()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file name: " + e.message)
                null
            }
        }

    val lastViewedFontRealName: String?
        get() {
            return try {
                dataStore.viewerRealName.blockingFirst()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting real name: " + e.message)
                null
            }
        }

    fun clearLastViewedFont() {
        dataStore.clearLastViewedFont()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Cleared last viewed font") },
                { error -> Log.e(TAG, "Error clearing font: " + error.message) }
            )
    }

    fun saveFontWeight(fontWeight: Float) {
        dataStore.setViewerFontWeight(fontWeight)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font weight: $fontWeight") },
                { error -> Log.e(TAG, "Error saving font weight: " + error.message) }
            )
    }

    fun getFontWeight(defaultWeight: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontWeight.blockingFirst()
            value ?: defaultWeight
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font weight: " + e.message)
            defaultWeight
        }
    }

    fun saveFontWidth(fontWidth: Float) {
        dataStore.setViewerFontWidth(fontWidth)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font width: $fontWidth") },
                { error -> Log.e(TAG, "Error saving font width: " + error.message) }
            )
    }

    fun getFontWidth(defaultWidth: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontWidth.blockingFirst()
            value ?: defaultWidth
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font width: " + e.message)
            defaultWidth
        }
    }

    fun saveFontGrade(fontGrade: Float) {
        dataStore.setViewerFontGrade(fontGrade)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font grade: $fontGrade") },
                { error -> Log.e(TAG, "Error saving font grade: " + error.message) }
            )
    }

    fun getFontGrade(defaultGrade: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontGrade.blockingFirst()
            value ?: defaultGrade
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font grade: " + e.message)
            defaultGrade
        }
    }

    fun saveFontRoundness(fontRoundness: Float) {
        dataStore.setViewerFontRoundness(fontRoundness)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font roundness: $fontRoundness") },
                { error -> Log.e(TAG, "Error saving font roundness: " + error.message) }
            )
    }

    fun getFontRoundness(defaultRoundness: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontRoundness.blockingFirst()
            value ?: defaultRoundness
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font roundness: " + e.message)
            defaultRoundness
        }
    }

    fun saveFontItalicAxis(italValue: Float) {
        dataStore.setViewerFontItalicAxis(italValue)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font italic axis: $italValue") },
                { error -> Log.e(TAG, "Error saving font italic axis: " + error.message) }
            )
    }

    fun getFontItalicAxis(defaultItal: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontItalicAxis.blockingFirst()
            value ?: defaultItal
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font italic axis: " + e.message)
            defaultItal
        }
    }

    fun saveFontMono(monoValue: Float) {
        dataStore.setViewerFontMono(monoValue)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { prefs -> Log.d(TAG, "Saved font monospace: $monoValue") },
                { error -> Log.e(TAG, "Error saving font monospace: " + error.message) }
            )
    }

    fun getFontMono(defaultMono: Float): Float {
        return try {
            val value: Float? = dataStore.viewerFontMono.blockingFirst()
            value ?: defaultMono
        } catch (e: Exception) {
            Log.e(TAG, "Error getting font monospace: " + e.message)
            defaultMono
        }
    }

    // ★ دالتان عامتان (Generic) للحفظ والاسترجاع حسب وسم المحور، تُستخدمان من FontViewerFragment
    //   بدل استدعاء الدالة المخصصة لكل محور يدوياً في كل مرة ★
    fun saveFontAxisValue(axisTag: String?, value: Float) {
        if (VariableFontAxisExtractor.AXIS_WGHT == axisTag) {
            saveFontWeight(value)
        } else if (VariableFontAxisExtractor.AXIS_WDTH == axisTag) {
            saveFontWidth(value)
        } else if (VariableFontAxisExtractor.AXIS_ITAL == axisTag) {
            saveFontItalicAxis(value)
        } else if (VariableFontAxisExtractor.AXIS_GRAD == axisTag) {
            saveFontGrade(value)
        } else if (VariableFontAxisExtractor.AXIS_ROND == axisTag) {
            saveFontRoundness(value)
        } else if (VariableFontAxisExtractor.AXIS_MONO == axisTag) {
            saveFontMono(value)
        } else {
            Log.w(TAG, "Unknown axis tag for saving: $axisTag")
        }
    }

    fun getFontAxisValue(axisTag: String?, defaultValue: Float): Float {
        if (VariableFontAxisExtractor.AXIS_WGHT == axisTag) {
            return getFontWeight(defaultValue)
        } else if (VariableFontAxisExtractor.AXIS_WDTH == axisTag) {
            return getFontWidth(defaultValue)
        } else if (VariableFontAxisExtractor.AXIS_ITAL == axisTag) {
            return getFontItalicAxis(defaultValue)
        } else if (VariableFontAxisExtractor.AXIS_GRAD == axisTag) {
            return getFontGrade(defaultValue)
        } else if (VariableFontAxisExtractor.AXIS_ROND == axisTag) {
            return getFontRoundness(defaultValue)
        } else if (VariableFontAxisExtractor.AXIS_MONO == axisTag) {
            return getFontMono(defaultValue)
        }
        return defaultValue
    }

    companion object {
        private const val TAG = "FontViewerPrefManager"
    }
}
