package com.oneui.fontviewer.ui.dialog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button

import androidx.appcompat.app.AlertDialog

import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

import com.oneui.fontviewer.R
import com.oneui.fontviewer.utils.FileUtils

object RenameFontDialog {

    fun interface OnRenameListener {
        fun onRename(oldPath: String, newFileName: String)
    }

    // NOTE(kotlin-migration): parameters kept nullable because the Java method null-checks them and returns early.
    fun show(context: Context?, currentPath: String?, listener: OnRenameListener?) {
        if (context == null || currentPath == null || listener == null) return

        val parentPath = FileUtils.getParentPath(currentPath)
        val currentFileName = FileUtils.getFileName(currentPath)
        val nameWithoutExt = FileUtils.removeExtension(currentFileName)
        val extension = FileUtils.getExtension(currentFileName)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_font, null)
        val inputLayout = dialogView.findViewById<TextInputLayout>(R.id.rename_input_layout)
        val input = dialogView.findViewById<TextInputEditText>(R.id.rename_edit_text)

        input.setText(nameWithoutExt)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.rename_dialog_title)
        builder.setView(dialogView)
        builder.setPositiveButton(R.string.action_rename, null)
        builder.setNegativeButton(android.R.string.cancel, null)

        val dialog = builder.create()
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        dialog.setOnShowListener {
            input.requestFocus()
            input.selectAll()

            Handler(Looper.getMainLooper()).postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 100)

            val positiveButton: Button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.isEnabled = false

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable) {
                    val newText = s.toString().trim()

                    val isEmpty = newText.isEmpty()
                    val isSameName = newText == nameWithoutExt
                    val isInvalid = !isEmpty && !FileUtils.isValidFileName(newText)
                    var fileExists = false

                    if (!isEmpty && !isSameName && !isInvalid) {
                        val fullNewName = if (extension.isEmpty()) newText else "$newText.$extension"
                        fileExists = FileUtils.exists(parentPath, fullNewName)
                    }

                    if (fileExists) {
                        inputLayout.error = context.getString(R.string.error_file_exists)
                        positiveButton.isEnabled = false
                    } else {
                        inputLayout.error = null

                        val isValid = !isEmpty && !isSameName && !isInvalid
                        positiveButton.isEnabled = isValid
                    }
                }
            })

            positiveButton.setOnClickListener { _: View? ->
                val newName = input.text?.toString()?.trim() ?: ""

                if (newName.isEmpty() || !FileUtils.isValidFileName(newName)) {
                    inputLayout.error = context.getString(R.string.error_invalid_name)
                    return@setOnClickListener
                }

                if (newName == nameWithoutExt) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                listener.onRename(currentPath, if (extension.isEmpty()) newName else "$newName.$extension")

                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                if (imm != null) imm.hideSoftInputFromWindow(input.windowToken, 0)

                dialog.dismiss()
            }
        }

        dialog.show()
    }
}
