package com.oneui.fontviewer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        if (ACTION_CANCEL == intent.action) {
            Log.d(TAG, "Cancel requested from notification — setting global cancel flag")

            BatchOperationState.requestCancel()
        }
    }

    companion object {
        private const val TAG = "NotifActionReceiver"

        const val ACTION_CANCEL = "com.oneui.fontviewer.ACTION_CANCEL_OPERATION"
    }
}
