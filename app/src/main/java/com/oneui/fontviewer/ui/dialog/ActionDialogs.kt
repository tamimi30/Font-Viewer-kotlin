package com.oneui.fontviewer.ui.dialog

import android.content.Context

import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

import dev.oneuiproject.oneui.dialog.ProgressDialog

import com.oneui.fontviewer.R

object ActionDialogs {

    fun interface OnConfirmListener {
        fun onConfirmed()
    }

    fun interface OnProgressCancelListener {
        fun onCancel()
    }

    fun interface OnProgressHideListener {
        fun onHide()
    }

    fun showDeletePermanentlyDialog(
        context: Context,
        count: Int,
        listener: OnConfirmListener
    ) {

        if (count <= 0) return

        val message = context.resources
            .getQuantityString(R.plurals.dialog_delete_permanently_question, count, count)

        val dialog = AlertDialog.Builder(context)
            .setMessage(message)
            .setPositiveButton(R.string.action_delete) { _, _ -> listener.onConfirmed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(context, R.color.oui_functional_red_color))
    }

    fun createDeleteProgressDialog(
        context: Context,
        total: Int,
        cancelListener: OnProgressCancelListener,
        hideListener: OnProgressHideListener
    ): ProgressDialog {

        val title = context.resources
            .getQuantityString(R.plurals.progress_deleting, total)

        return buildProgressDialog(context, title, total, cancelListener, hideListener)
    }

    fun createRestoreProgressDialog(
        context: Context,
        total: Int,
        cancelListener: OnProgressCancelListener,
        hideListener: OnProgressHideListener
    ): ProgressDialog {

        val title = context.resources
            .getQuantityString(R.plurals.progress_restoring, total)

        return buildProgressDialog(context, title, total, cancelListener, hideListener)
    }

    private fun buildProgressDialog(
        context: Context,
        title: String,
        total: Int,
        cancelListener: OnProgressCancelListener,
        hideListener: OnProgressHideListener
    ): ProgressDialog {

        val dialog = ProgressDialog(context)
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setTitle(title)

        dialog.setMax(total)

        dialog.setCancelable(false)

        dialog.setButton(
            ProgressDialog.BUTTON_NEGATIVE,
            context.getString(android.R.string.cancel)
        ) { d, _ ->
            cancelListener.onCancel()
            d.dismiss()
        }

        dialog.setButton(
            ProgressDialog.BUTTON_POSITIVE,
            context.getString(R.string.action_hide_dialog)
        ) { d, _ ->
            hideListener.onHide()
            d.dismiss()
        }

        return dialog
    }
}
