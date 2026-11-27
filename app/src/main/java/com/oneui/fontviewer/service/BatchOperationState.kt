package com.oneui.fontviewer.service

import android.util.Log

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem

import java.util.concurrent.atomic.AtomicBoolean

object BatchOperationState {

    private const val TAG = "BatchOperationState"

    private val _isProcessing = MutableLiveData(false)

    @Volatile
    private var _sourceDrawerListItem: DrawerListItem? = null

    class ProgressData(
        @JvmField val current: Int,
        @JvmField val total: Int,
        @JvmField val title: String?,
        @JvmField val operationCode: Int = 1
    )

    private val _progress = MutableLiveData<ProgressData>()

    @Volatile
    private var _currentCancelFlag: AtomicBoolean? = null

    @Volatile
    private var _shouldReopenDialog = false

    fun setShouldReopenDialog(shouldReopen: Boolean) {
        _shouldReopenDialog = shouldReopen
    }

    fun consumeShouldReopenDialog(): Boolean {
        val value = _shouldReopenDialog
        _shouldReopenDialog = false
        return value
    }


    fun getIsProcessing(): LiveData<Boolean> {
        return _isProcessing
    }

    fun getProgress(): LiveData<ProgressData> {
        return _progress
    }

    fun getSourceDrawerListItem(): DrawerListItem? {
        return _sourceDrawerListItem
    }

    fun setProcessing(isProcessing: Boolean) {
        if (!isProcessing) {
            _sourceDrawerListItem = null
        }
        _isProcessing.postValue(isProcessing)
    }

    fun setSourceDrawerListItem(drawerListItem: DrawerListItem?) {
        _sourceDrawerListItem = drawerListItem
        Log.d(TAG, "setSourceDrawerListItem=$drawerListItem")
    }

    fun updateProgress(current: Int, total: Int, title: String?) {
        _progress.postValue(ProgressData(current, total, title, 1))
    }

    fun updateProgress(current: Int, total: Int, title: String?, operationCode: Int) {
        _progress.postValue(ProgressData(current, total, title, operationCode))
    }


    fun setCancelFlag(flag: AtomicBoolean?) {
        _currentCancelFlag = flag
        Log.d(TAG, "Cancel flag registered for current operation")
    }

    fun requestCancel() {
        val flag = _currentCancelFlag
        if (flag != null) {
            flag.set(true)
            Log.d(TAG, "Cancel requested via notification — flag set to true")
        } else {
            Log.w(TAG, "requestCancel() called but no cancel flag is registered")
        }
    }
}
