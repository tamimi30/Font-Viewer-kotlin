package com.oneui.fontviewer.ui.main.list.search

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import androidx.core.content.ContextCompat
import com.oneui.fontviewer.R

import java.util.Locale

class SearchHighlighter(context: Context) {

    private val highlightColor: Int = ContextCompat.getColor(context, R.color.sesl_primary_color_dark)

    fun highlightText(text: String?, searchQuery: String?): SpannableString? {
        if (text == null || text.isEmpty()) {
            return null
        }

        if (searchQuery == null || searchQuery.isEmpty()) {
            return SpannableString(text)
        }

        val spannableString = SpannableString(text)
        val lowerText = text.lowercase(Locale.getDefault())
        val lowerQuery = searchQuery.lowercase(Locale.getDefault())

        val startPos = lowerText.indexOf(lowerQuery)

        if (startPos >= 0) {
            spannableString.setSpan(
                ForegroundColorSpan(highlightColor),
                startPos,
                startPos + lowerQuery.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannableString
    }

}
