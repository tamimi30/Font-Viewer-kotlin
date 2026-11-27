package com.oneui.fontviewer.ui.main.fragment.localfont.manager

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.fragment.app.Fragment

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.localfont.file.LocalFontDirectory

class LocalFontDirectoryPicker(private val fragment: Fragment) {

    private var listener: DirectorySelectionListener? = null

    interface DirectorySelectionListener {
        fun onDirectorySelected(directoryPath: String)
        fun onDirectorySelectionCancelled()
        fun onDirectorySelectionError(error: Exception)
    }

    fun setDirectorySelectionListener(listener: DirectorySelectionListener?) {
        this.listener = listener
    }

    @Suppress("DEPRECATION")
    fun openDirectoryPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

            try {
                val downloadsPath = LocalFontDirectory.getDownloadsDirectoryPath()
                if (downloadsPath != null) {
                    intent.putExtra("android.provider.extra.INITIAL_URI", downloadsPath)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not set initial directory", e)
            }

            fragment.startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch directory picker", e)
            listener?.onDirectorySelectionError(e)
        }
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            val treeUri = data?.data
            if (resultCode == Activity.RESULT_OK && treeUri != null) {
                try {
                    val directoryPath = LocalFontDirectory.getRealPathFromTreeUri(treeUri)

                    if (directoryPath != null) {
                        Log.d(TAG, "Directory selected: $directoryPath")
                        listener?.onDirectorySelected(directoryPath)
                    } else {
                        Log.e(TAG, "Could not convert URI to direct path: $treeUri")
                        listener?.onDirectorySelectionError(
                            Exception(fragment.getString(R.string.error_folder_path_conversion_failed))
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing directory selection", e)
                    listener?.onDirectorySelectionError(e)
                }
            } else {
                Log.d(TAG, "Directory selection cancelled")
                listener?.onDirectorySelectionCancelled()
            }
            return true
        }
        return false
    }

    companion object {
        private const val TAG = "LocalFontDirectoryPicker"
        const val FOLDER_PICKER_REQUEST_CODE = 200
    }

}
