package com.oneui.fontviewer.utils

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private lateinit var ctx: Context

    @Suppress("DEPRECATION")
    override fun uncaughtException(t: Thread, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val stack = sw.toString()

            val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                .format(Date())

            val report = StringBuilder()
            report.append("Timestamp: ").append(ts).append("\n")
            report.append("Thread: ").append(t.name).append("\n")
            report.append("Package: ").append(ctx.packageName).append("\n")
            report.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n")
            report.append("SDK: ").append(android.os.Build.VERSION.SDK_INT).append("\n\n")
            report.append(stack)

            val fileName = "crash_$ts.txt"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore to write to public Downloads (no runtime permission required)
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OneUIApp")
                val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    ctx.contentResolver.openOutputStream(uri)!!.use { os ->
                        BufferedWriter(OutputStreamWriter(os)).use { bw ->
                            bw.write(report.toString())
                        }
                    }
                }
            } else {
                // Legacy path for Android 9 and below — requires WRITE_EXTERNAL_STORAGE permission (runtime on M+)
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OneUIApp")
                if (!dir.exists()) dir.mkdirs()
                val out = File(dir, fileName)
                FileWriter(out, false).use { fw ->
                    fw.write(report.toString())
                    fw.flush()
                }
            }

            Log.e("CrashHandler", "Crash logged (Downloads/OneUIApp): $fileName")
        } catch (ex: Exception) {
            Log.e("CrashHandler", "Failed to log crash", ex)
        }

        val handler = defaultHandler
        if (handler != null) {
            handler.uncaughtException(t, e)
        } else {
            System.exit(2)
        }
    }

    companion object {
        private var instance: CrashHandler? = null

        @Synchronized
        fun init(context: Context) {
            if (instance == null) {
                val handler = CrashHandler()
                instance = handler
                handler.ctx = context.applicationContext
                handler.defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler(handler)
            }
        }
    }
}
