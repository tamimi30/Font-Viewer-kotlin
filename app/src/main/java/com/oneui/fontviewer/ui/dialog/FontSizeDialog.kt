package com.oneui.fontviewer.ui.dialog

import android.content.Context
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SeslSeekBar
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.widget.OneUiTipPopup

class FontSizeDialog(
    private val context: Context,
    private val currentSize: Float,
    private val minSize: Float,
    private val maxSize: Float
) {

    fun interface OnFontSizeChangedListener {
        fun onFontSizeChanged(newSize: Float)
    }

    fun interface OnDialogCancelledListener {
        fun onDialogCancelled()
    }

    private var sizeListener: OnFontSizeChangedListener? = null
    private var cancelListener: OnDialogCancelledListener? = null
    private var fontSizeValue: AppCompatEditText? = null
    private var seekBar: SeslSeekBar? = null

    private var dialog: AlertDialog? = null
    private var tempSize: Float = currentSize
    private val originalSize: Float = currentSize
    private var fontSizeTipPopup: OneUiTipPopup? = null
    private var isNumericKeyboardVisible = false
    private var invalidSizeRealtimeToast: Toast? = null

    fun setOnFontSizeChangedListener(listener: OnFontSizeChangedListener?) {
        this.sizeListener = listener
    }

    fun setOnDialogCancelledListener(listener: OnDialogCancelledListener?) {
        this.cancelListener = listener
    }

    fun show() {
        val builder = AlertDialog.Builder(context)


        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size, null)
        builder.setView(dialogView)
        val valueView: AppCompatEditText = dialogView.findViewById(R.id.font_size_value)
        fontSizeValue = valueView
        val helpIcon: View = dialogView.findViewById(R.id.font_size_help_icon)
        helpIcon.setOnClickListener {
            val existingPopup = fontSizeTipPopup
            if (existingPopup != null && existingPopup.isShowing) {
                existingPopup.dismiss(true)
                return@setOnClickListener
            }
            val popup = OneUiTipPopup(helpIcon, OneUiTipPopup.Mode.TRANSLUCENT)
            fontSizeTipPopup = popup
            popup.setKeepSoftInputEnabled(true)
            popup.setMessage(context.getString(R.string.font_size_dialog_tip))
            // set custom background color with alpha using project color
            // fontSizeTipPopup.setBackgroundColorWithAlpha(ContextCompat.getColor(context, R.color.orange));
            popup.setExpanded(true)
            val isRtl = helpIcon.layoutDirection == View.LAYOUT_DIRECTION_RTL
            popup.show(if (isRtl) OneUiTipPopup.Direction.BOTTOM_RIGHT else OneUiTipPopup.Direction.BOTTOM_LEFT)
        }

        // يرصد ظهور/اختفاء الكيبورد مباشرة، بغض النظر عن السبب (رجوع، Done، إلخ)
        ViewCompat.setOnApplyWindowInsetsListener(dialogView) { _, insets ->
            val isVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (isNumericKeyboardVisible && !isVisibleNow) {
                fontSizeValue?.clearFocus()
            }
            isNumericKeyboardVisible = isVisibleNow
            insets
        }

        valueView.isLongClickable = false

        // ننتظر رفع الإصبع (ACTION_UP) قبل التنفيذ، كسلوك النقر القياسي،
        // بدل التنفيذ الفوري عند أول لمسة (ACTION_DOWN).
        valueView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> true
                MotionEvent.ACTION_UP -> {
                    valueView.selectAll()
                    valueView.requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                    if (imm != null) {
                        imm.viewClicked(valueView)
                        imm.showSoftInput(valueView, 0)
                    }
                    seekBar?.let {
                        it.isEnabled = false
                        it.alpha = 0.4f
                    }
                    true
                }
                else -> false
            }
        }

        // Also track focus changes to re-enable/disable the seekBar when keyboard
        // is shown/hidden by other means.
        valueView.setOnFocusChangeListener { _, hasFocus ->
            val bar = seekBar
            if (bar != null) {
                if (hasFocus) {
                    bar.isEnabled = false
                    bar.alpha = 0.4f
                } else {
                    bar.isEnabled = true
                    bar.alpha = 1f
                }
            }
        }

        // نفس نمط سامسونج: رفض فوري للرقم الزائد أثناء الكتابة + Toast واحد يُعاد استخدامه.
        val maxValueRealtimeFilter = InputFilter { source, start, end, dest, dstart, dend ->
            val result = dest.subSequence(0, dstart).toString() +
                    source.subSequence(start, end) +
                    dest.subSequence(dend, dest.length)

            if (result.isEmpty()) {
                return@InputFilter null
            }

            try {
                val enteredValue = Integer.parseInt(result)
                if (enteredValue > maxSize.toInt()) {
                    var toast = invalidSizeRealtimeToast
                    if (toast == null) {
                        toast = Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT)
                        invalidSizeRealtimeToast = toast
                    }
                    toast!!.show()
                    return@InputFilter ""
                }
            } catch (e: NumberFormatException) {
                return@InputFilter ""
            }

            null
        }
        val existingFilters: Array<InputFilter> = valueView.filters
        val combinedFilters = arrayOfNulls<InputFilter>(existingFilters.size + 1)
        System.arraycopy(existingFilters, 0, combinedFilters, 0, existingFilters.size)
        combinedFilters[existingFilters.size] = maxValueRealtimeFilter
        @Suppress("UNCHECKED_CAST")
        valueView.filters = combinedFilters as Array<InputFilter>

        valueView.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { return true }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { menu.clear(); return true }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean { return false }
            override fun onDestroyActionMode(mode: ActionMode) {}
        }

        valueView.customInsertionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { return false }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { return false }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean { return false }
            override fun onDestroyActionMode(mode: ActionMode) {}
        }

        valueView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val inputText = valueView.text.toString()
                if (!inputText.isEmpty()) {
                    try {
                        var enteredSize = inputText.toFloat()
                        if (enteredSize > maxSize || enteredSize < minSize) {
                            Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT).show()
                        }
                        if (enteredSize > maxSize) enteredSize = maxSize
                        if (enteredSize < minSize) enteredSize = minSize

                        val newProgress = (enteredSize - minSize).toInt()
                        seekBar?.progress = newProgress
                        updateFontSizeText(enteredSize)
                        tempSize = enteredSize

                        // For immediate preview we inform the listener, but we will
                        // restore original size if the dialog is cancelled.
                        sizeListener?.onFontSizeChanged(enteredSize)
                    } catch (e: NumberFormatException) {
                        updateFontSizeText(tempSize)
                    }
                }
                valueView.clearFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.hideSoftInputFromWindow(valueView.windowToken, 0)
                // Re-enable seekBar after keyboard closed
                seekBar?.let {
                    it.isEnabled = true
                    it.alpha = 1f
                }
                true
            } else {
                false
            }
        }
        seekBar = dialogView.findViewById(R.id.font_size_seekbar)

        setupSeekBar()

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val inputText = valueView.text.toString()
            if (!inputText.isEmpty()) {
                try {
                    var enteredSize = inputText.toFloat()
                    if (enteredSize > maxSize || enteredSize < minSize) {
                        Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT).show()
                    }
                    if (enteredSize > maxSize) enteredSize = maxSize
                    if (enteredSize < minSize) enteredSize = minSize
                    tempSize = enteredSize
                } catch (ignored: NumberFormatException) {
                }
            }

            // Commit the final size
            sizeListener?.onFontSizeChanged(tempSize)

            // Hide keyboard and re-enable seekBar
            valueView.clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(valueView.windowToken, 0)
            seekBar?.let {
                it.isEnabled = true
                it.alpha = 1f
            }
        }

        builder.setNegativeButton(android.R.string.cancel) { _, _ ->
            // Restore original size when cancelling
            sizeListener?.onFontSizeChanged(originalSize)
            cancelListener?.onDialogCancelled()
            // Dialog will be dismissed automatically
        }

        val createdDialog = builder.create()
        dialog = createdDialog
        // Allow tapping outside to cancel and restore size
        createdDialog.setCanceledOnTouchOutside(true)

        createdDialog.setOnDismissListener {
            fontSizeTipPopup?.dismiss(false)
            ViewCompat.setOnApplyWindowInsetsListener(dialogView, null)
        }

        createdDialog.setOnCancelListener {
            // Restore original size when cancelled (including tapping outside)
            sizeListener?.onFontSizeChanged(originalSize)
            cancelListener?.onDialogCancelled()

            // Hide keyboard and re-enable seekBar
            valueView.clearFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(valueView.windowToken, 0)
            seekBar?.let {
                it.isEnabled = true
                it.alpha = 1f
            }
        }

        createdDialog.show()

    }

    private fun setupSeekBar() {
        val bar = seekBar ?: return

        val range = (maxSize - minSize).toInt()
        bar.max = range

        val currentProgress = (currentSize - minSize).toInt()
        bar.progress = currentProgress
        updateFontSizeText(currentSize)

        bar.setOnSeekBarChangeListener(object : SeslSeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeslSeekBar, progress: Int, fromUser: Boolean) {
                tempSize = minSize + progress
                updateFontSizeText(tempSize)

                // Live preview while dragging; we keep originalSize to allow restore on cancel
                if (fromUser) {
                    sizeListener?.onFontSizeChanged(tempSize)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeslSeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeslSeekBar) {}
        })
    }

    private fun updateFontSizeText(size: Float) {
        val valueView = fontSizeValue
        if (valueView != null) {
            val newText = String.format("%.0f", size)
            if (valueView.text.toString() != newText) {
                valueView.setText(newText)
                valueView.setSelection(newText.length)
            }
        }
    }
}
