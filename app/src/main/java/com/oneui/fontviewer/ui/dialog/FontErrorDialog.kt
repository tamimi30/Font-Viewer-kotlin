package com.oneui.fontviewer.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.appcompat.app.AlertDialog

import com.oneui.fontviewer.R

object FontErrorDialog {

    fun show(context: Context) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.font_metadata_error_title)
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

        val content: View = inflater.inflate(R.layout.dialog_font_error, scrollHost, false)

        scrollHost.addView(content)
    }
}
