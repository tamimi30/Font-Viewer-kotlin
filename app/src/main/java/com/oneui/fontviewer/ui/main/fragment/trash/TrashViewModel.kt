package com.oneui.fontviewer.ui.main.fragment.trash

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

import java.util.concurrent.atomic.AtomicBoolean

import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.trash.TrashRepository
import com.oneui.fontviewer.service.BatchOperationState
import com.oneui.fontviewer.service.OperationForegroundService
import com.oneui.fontviewer.service.OperationNotifications
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.trash.model.OperationProgress
import com.oneui.fontviewer.data.trash.model.OperationResult
import com.oneui.fontviewer.data.trash.model.OperationType

class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val repository: TrashRepository = TrashRepository.getInstance(application)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _operationProgress = MutableLiveData<OperationProgress>()
    private val _operationResult = MutableLiveData<OperationResult?>()
    private val _isLoading = MutableLiveData(false)
    private var cancelFlag = AtomicBoolean(false)

    init {
        repository.deleteExpiredItems(null)
    }

    val trashedFontsLiveData: LiveData<List<FontEntity>>
        get() = repository.trashedFonts

    val trashedFontsCountLiveData: LiveData<Int>
        get() = repository.trashedFontsCount

    val operationProgressLiveData: LiveData<OperationProgress>
        get() = _operationProgress

    val operationResultLiveData: LiveData<OperationResult?>
        get() = _operationResult

    fun restoreFonts(fonts: List<FontEntity>) {
        if (fonts.isEmpty()) return

        BatchOperationState.setSourceDrawerListItem(DrawerListItem.TRASH)
        BatchOperationState.setProcessing(true)
        cancelFlag = AtomicBoolean(false)
        BatchOperationState.setCancelFlag(cancelFlag)
        _isLoading.postValue(true)
        val startTime = System.currentTimeMillis()

        val restoreTitle = app.resources
            .getQuantityString(R.plurals.progress_restoring, fonts.size)
        val restoreServiceIntent = Intent(app, OperationForegroundService::class.java)
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_RESTORE)
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, restoreTitle)
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size)
        ContextCompat.startForegroundService(app, restoreServiceIntent)

        OperationNotifications.showRestoreNotification(app, fonts.size)

        repository.restoreBatch(
            app,
            fonts,
            cancelFlag,

            { current, total ->
                OperationNotifications.updateRestoreNotification(app, current, total)
                _operationProgress.postValue(
                    OperationProgress(current, total, OperationType.RESTORE)
                )

                val progressTitle = app.resources
                    .getQuantityString(R.plurals.progress_restoring, total)
                BatchOperationState.updateProgress(current, total, progressTitle, 2)
            },

            { succeeded, failed ->
                val elapsedTime = System.currentTimeMillis() - startTime
                val delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime)

                mainHandler.postDelayed({
                    app.stopService(
                        Intent(app, OperationForegroundService::class.java)
                    )
                    OperationNotifications.dismissRestoreNotification(app)
                    _isLoading.postValue(false)
                    _operationResult.postValue(
                        OperationResult(
                            succeeded, failed,
                            OperationType.RESTORE,
                            cancelFlag.get()
                        )
                    )
                    BatchOperationState.setProcessing(false)
                    Log.d(
                        TAG, "restoreFonts complete — succeeded: " + succeeded
                                + ", failed: " + failed
                    )
                }, delay)
            }
        )
    }

    fun deletePermanently(fonts: List<FontEntity>) {
        if (fonts.isEmpty()) return

        BatchOperationState.setSourceDrawerListItem(DrawerListItem.TRASH)
        BatchOperationState.setProcessing(true)

        cancelFlag = AtomicBoolean(false)
        BatchOperationState.setCancelFlag(cancelFlag)
        _isLoading.postValue(true)

        val startTime = System.currentTimeMillis()

        val deleteTitle = app.resources
            .getQuantityString(R.plurals.progress_deleting, fonts.size)
        val deleteServiceIntent = Intent(app, OperationForegroundService::class.java)
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_DELETE)
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, deleteTitle)
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size)
        ContextCompat.startForegroundService(app, deleteServiceIntent)
        OperationNotifications.showDeleteNotification(app, fonts.size)

        repository.deletePermanentlyBatch(
            app,
            fonts,
            cancelFlag,

            { current, total ->
                OperationNotifications.updateDeleteNotification(app, current, total)
                _operationProgress.postValue(
                    OperationProgress(current, total, OperationType.DELETE_PERMANENTLY)
                )

                val progressTitle = app.resources
                    .getQuantityString(R.plurals.progress_deleting, total)
                BatchOperationState.updateProgress(current, total, progressTitle, 3)
            },

            { succeeded, failed ->
                val elapsedTime = System.currentTimeMillis() - startTime
                val delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime)

                mainHandler.postDelayed({
                    app.stopService(
                        Intent(app, OperationForegroundService::class.java)
                    )
                    OperationNotifications.dismissDeleteNotification(app)
                    _isLoading.postValue(false)
                    _operationResult.postValue(
                        OperationResult(
                            succeeded, failed,
                            OperationType.DELETE_PERMANENTLY,
                            cancelFlag.get()
                        )
                    )
                    BatchOperationState.setProcessing(false)
                    Log.d(
                        TAG, "deletePermanently complete — succeeded: " + succeeded
                                + ", failed: " + failed
                    )
                }, delay)
            }
        )
    }

    fun emptyTrash() {
        BatchOperationState.setSourceDrawerListItem(DrawerListItem.TRASH)
        BatchOperationState.setProcessing(true)

        cancelFlag = AtomicBoolean(false)
        BatchOperationState.setCancelFlag(cancelFlag)
        _isLoading.postValue(true)

        val startTime = System.currentTimeMillis()

        val notifShownForEmpty = AtomicBoolean(false)

        repository.emptyTrash(
            app,
            cancelFlag,

            { current, total ->
                if (notifShownForEmpty.compareAndSet(false, true)) {
                    val emptyTitle = app.resources
                        .getQuantityString(R.plurals.progress_deleting, total)
                    val emptyServiceIntent = Intent(app, OperationForegroundService::class.java)
                    emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_DELETE)
                    emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, emptyTitle)
                    emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, total)
                    ContextCompat.startForegroundService(app, emptyServiceIntent)
                    OperationNotifications.showDeleteNotification(app, total)
                }
                OperationNotifications.updateDeleteNotification(app, current, total)
                _operationProgress.postValue(
                    OperationProgress(current, total, OperationType.EMPTY_TRASH)
                )

                val progressTitle = app.resources
                    .getQuantityString(R.plurals.progress_deleting, total)
                BatchOperationState.updateProgress(current, total, progressTitle, 3)
            },

            { succeeded, failed ->
                val elapsedTime = System.currentTimeMillis() - startTime
                val delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime)

                mainHandler.postDelayed({
                    app.stopService(
                        Intent(app, OperationForegroundService::class.java)
                    )
                    OperationNotifications.dismissDeleteNotification(app)
                    _isLoading.postValue(false)
                    _operationResult.postValue(
                        OperationResult(
                            succeeded, failed,
                            OperationType.EMPTY_TRASH,
                            cancelFlag.get()
                        )
                    )
                    BatchOperationState.setProcessing(false)
                    Log.d(
                        TAG, "emptyTrash complete — succeeded: " + succeeded
                                + ", failed: " + failed
                    )
                }, delay)
            }
        )
    }

    fun cancelCurrentOperation() {
        cancelFlag.set(true)
        Log.d(TAG, "Operation cancellation requested")
    }

    fun clearOperationResult() {
        _operationResult.postValue(null)
    }

    companion object {
        private const val TAG = "TrashViewModel"
        private const val MIN_DIALOG_DURATION_MS = 2500L
    }
}
