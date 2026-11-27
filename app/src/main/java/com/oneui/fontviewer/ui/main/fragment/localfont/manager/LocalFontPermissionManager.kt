package com.oneui.fontviewer.ui.main.fragment.localfont.manager

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.util.Log

class LocalFontPermissionManager(private val fragment: Fragment) {

    private val context: Context = fragment.requireContext()
    private var listener: PermissionResultListener? = null

    interface PermissionResultListener {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }

    fun setPermissionResultListener(listener: PermissionResultListener?) {
        this.listener = listener
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    @Suppress("DEPRECATION")
    fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                requestManageStoragePermission()
            }
        } else {
            fragment.requestPermissions(
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_REQUEST_CODE
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:" + context.packageName)
                fragment.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open MANAGE_EXTERNAL_STORAGE settings", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    fragment.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
                } catch (ex: Exception) {
                    Log.e(TAG, "Failed to open settings fallback", ex)
                }
            }
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                listener?.onPermissionGranted()
            } else {
                listener?.onPermissionDenied()
            }
            return true
        }
        return false
    }

    fun handleActivityResult(requestCode: Int): Boolean {
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    listener?.onPermissionGranted()
                    return true
                } else {
                    listener?.onPermissionDenied()
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val TAG = "LocalFontPermissionManager"
        const val STORAGE_PERMISSION_REQUEST_CODE = 100
        const val MANAGE_STORAGE_REQUEST_CODE = 101
    }

}
