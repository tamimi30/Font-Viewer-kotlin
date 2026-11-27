package com.oneui.fontviewer.ui.fontviewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns


class FontOpenTriggerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sourceIntent = intent

        if (isFontViewIntent(sourceIntent)) {
            val fontUri = sourceIntent.data
            val fileName = getFileName(this, fontUri!!)

            val viewerIntent = Intent(this, FontViewerActivity::class.java)
            viewerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_PATH, fontUri.toString())
            viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_FILE_NAME, fileName)
            startActivity(viewerIntent)
        }

        finish()
    }

    private fun isFontViewIntent(intent: Intent?): Boolean {
        return intent != null && Intent.ACTION_VIEW == intent.action && intent.data != null
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if ("content" == uri.scheme) {
            try {
                context.contentResolver.query(uri, null, null, null, null).use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
        }
        if (result == null) {
            result = uri.path
            val path = result
            if (path != null) {
                val cut = path.lastIndexOf('/')
                if (cut != -1) {
                    result = path.substring(cut + 1)
                }
            }
        }
        return result ?: "Unknown Font"
    }
}
