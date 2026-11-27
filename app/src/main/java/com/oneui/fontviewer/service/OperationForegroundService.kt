package com.oneui.fontviewer.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

import androidx.core.app.NotificationCompat

import com.oneui.fontviewer.R

class OperationForegroundService : Service() {

    private var mNotifId = OperationNotifications.NOTIF_ID_MOVE

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: foreground service starting")

        if (intent != null) {
            mNotifId = intent.getIntExtra(EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_MOVE)
            var title = intent.getStringExtra(EXTRA_TITLE)
            val total = intent.getIntExtra(EXTRA_TOTAL, 0)

            if (title == null) title = getString(R.string.app_name)

            val notification = buildInitialNotification(title, total)
            startForeground(mNotifId, notification)

            Log.d(
                TAG, "startForeground: id=" + mNotifId + ", total=" + total
                        + ", source=" + BatchOperationState.getSourceDrawerListItem()
            )
        } else {
            Log.w(TAG, "onStartCommand: null intent — using fallback notification")
            startForeground(mNotifId, buildFallbackNotification())
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: removing foreground notification (id=$mNotifId)")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun buildInitialNotification(title: String, total: Int): Notification {
        OperationNotifications.createChannelIfNeeded(this)
        return OperationNotifications.buildNotificationBase(this, title, 0, total).build()
    }

    private fun buildFallbackNotification(): Notification {
        OperationNotifications.createChannelIfNeeded(this)
        return NotificationCompat.Builder(this, OperationNotifications.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "OperationFgService"

        const val EXTRA_NOTIF_ID = "extra_notif_id"

        const val EXTRA_TITLE = "extra_title"

        const val EXTRA_TOTAL = "extra_total"
    }
}
