package com.oneui.fontviewer.ui.fontviewer.utils

import android.os.Bundle
import android.view.View

class BoldItalicFormatting {

    var isBoldActive: Boolean = false
        private set
    var isItalicActive: Boolean = false
        private set

    private var btnBold: View? = null
    private var btnItalic: View? = null
    private var listener: OnStyleChangedListener? = null

    fun interface OnStyleChangedListener {
        fun onStyleChanged(isFakeBold: Boolean, isFakeItalic: Boolean)
    }

    fun setup(btnBold: View?, btnItalic: View?, listener: OnStyleChangedListener?) {
        this.btnBold = btnBold
        this.btnItalic = btnItalic
        this.listener = listener

        if (btnBold != null) {
            btnBold.setOnClickListener {
                isBoldActive = !isBoldActive
                btnBold.isSelected = isBoldActive
                notifyStyleChanged()
            }
        }

        if (btnItalic != null) {
            btnItalic.setOnClickListener {
                isItalicActive = !isItalicActive
                btnItalic.isSelected = isItalicActive
                notifyStyleChanged()
            }
        }
    }

    private fun notifyStyleChanged() {
        listener?.onStyleChanged(isBoldActive, isItalicActive)
    }

    fun reset() {
        isBoldActive = false
        isItalicActive = false
        btnBold?.isSelected = false
        btnItalic?.isSelected = false
        notifyStyleChanged()
    }

    fun unbind() {
        btnBold?.setOnClickListener(null)
        btnItalic?.setOnClickListener(null)
        btnBold = null
        btnItalic = null
        listener = null
    }

    fun syncViewState() {
        btnBold?.isSelected = isBoldActive
        btnItalic?.isSelected = isItalicActive
    }

    fun saveState(outState: Bundle) {
        outState.putBoolean(KEY_IS_BOLD_ACTIVE, isBoldActive)
        outState.putBoolean(KEY_IS_ITALIC_ACTIVE, isItalicActive)
    }

    fun restoreState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            isBoldActive = savedInstanceState.getBoolean(KEY_IS_BOLD_ACTIVE, false)
            isItalicActive = savedInstanceState.getBoolean(KEY_IS_ITALIC_ACTIVE, false)
        }
    }

    companion object {
        private const val KEY_IS_BOLD_ACTIVE = "is_bold_active"
        private const val KEY_IS_ITALIC_ACTIVE = "is_italic_active"
    }
}
