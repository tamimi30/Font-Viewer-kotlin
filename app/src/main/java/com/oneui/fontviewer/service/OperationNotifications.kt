package com.oneui.fontviewer.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.main.MainActivity

object OperationNotifications {

    const val CHANNEL_ID = "trash_ops"

    const val NOTIF_ID_MOVE = 1001
    const val NOTIF_ID_RESTORE = 1002
    const val NOTIF_ID_DELETE = 1003

    private const val PI_REQUEST_CONTENT = 100
    private const val PI_REQUEST_CANCEL = 101

    fun createChannelIfNeeded(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.trash_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.description = context.getString(R.string.trash_notif_channel_desc)
        channel.setShowBadge(false)
        channel.setSound(null, null)
        nm.createNotificationChannel(channel)
    }

    private fun buildContentIntent(context: Context): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java)
        openIntent.flags = (Intent.FLAG_ACTIVITY_CLEAR_TOP
                or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val sourceDrawerListItem = BatchOperationState.getSourceDrawerListItem()
        if (sourceDrawerListItem != null) {
            openIntent.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, sourceDrawerListItem.name)
        }

        openIntent.putExtra("from_notification", true)

        return PendingIntent.getActivity(
            context, PI_REQUEST_CONTENT, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildCancelIntent(context: Context): PendingIntent {
        val cancelIntent = Intent(context, NotificationActionReceiver::class.java)
        cancelIntent.action = NotificationActionReceiver.ACTION_CANCEL
        return PendingIntent.getBroadcast(
            context, PI_REQUEST_CANCEL, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildNotificationBase(
        context: Context,
        title: String,
        progress: Int,
        total: Int
    ): NotificationCompat.Builder {

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$progress/$total")
            .setProgress(total, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buildContentIntent(context))
            .addAction(
                0,
                context.getString(android.R.string.cancel),
                buildCancelIntent(context)
            )
    }

    fun showMoveToTrashNotification(context: Context, total: Int) {
        createChannelIfNeeded(context)
        val title = context.resources
            .getQuantityString(R.plurals.progress_moving_to_trash, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_MOVE,
            buildNotificationBase(context, title, 0, total).build()
        )
    }

    fun updateMoveToTrashNotification(
        context: Context, progress: Int, total: Int
    ) {
        val title = context.resources
            .getQuantityString(R.plurals.progress_moving_to_trash, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_MOVE,
            buildNotificationBase(context, title, progress, total).build()
        )
    }

    fun dismissMoveToTrashNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_MOVE)
    }

    fun showRestoreNotification(context: Context, total: Int) {
        createChannelIfNeeded(context)
        val title = context.resources
            .getQuantityString(R.plurals.progress_restoring, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_RESTORE,
            buildNotificationBase(context, title, 0, total).build()
        )
    }

    fun updateRestoreNotification(
        context: Context, progress: Int, total: Int
    ) {
        val title = context.resources
            .getQuantityString(R.plurals.progress_restoring, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_RESTORE,
            buildNotificationBase(context, title, progress, total).build()
        )
    }

    fun dismissRestoreNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RESTORE)
    }

    fun showDeleteNotification(context: Context, total: Int) {
        createChannelIfNeeded(context)
        val title = context.resources
            .getQuantityString(R.plurals.progress_deleting, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_DELETE,
            buildNotificationBase(context, title, 0, total).build()
        )
    }

    fun updateDeleteNotification(
        context: Context, progress: Int, total: Int
    ) {
        val title = context.resources
            .getQuantityString(R.plurals.progress_deleting, total)
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID_DELETE,
            buildNotificationBase(context, title, progress, total).build()
        )
    }

    fun dismissDeleteNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DELETE)
    }
}
