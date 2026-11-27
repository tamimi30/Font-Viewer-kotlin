package com.oneui.fontviewer.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

import com.oneui.fontviewer.R

object AxisInfoDialog {

    fun show(context: Context, @StringRes titleRes: Int, @DrawableRes imageRes: Int, @StringRes descriptionRes: Int) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
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

        val content: View = inflater.inflate(R.layout.dialog_axis_info, scrollHost, false)

        val imageView = content.findViewById<ImageView>(R.id.axis_info_image)
        val descriptionView = content.findViewById<TextView>(R.id.axis_info_description)

        if (imageView != null) {
            imageView.setImageResource(imageRes)
        }
        if (descriptionView != null) {
            descriptionView.setText(descriptionRes)
        }

        scrollHost.addView(content)
    }
}
