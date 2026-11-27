package com.oneui.fontviewer.data.trash

import android.content.Context
import android.util.Log

import androidx.lifecycle.LiveData

import com.oneui.fontviewer.data.common.room.FontDao
import com.oneui.fontviewer.data.common.room.FontDatabase
import com.oneui.fontviewer.data.common.room.FontEntity

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

class TrashRepository private constructor(context: Context) {

    private val fontDao: FontDao
    private val executorService: ExecutorService

    init {
        val database = FontDatabase.getInstance(context)
        fontDao = database.fontDao()
        executorService = FontDatabase.databaseWriteExecutor
    }


    val trashedFonts: LiveData<List<FontEntity>>
        get() = fontDao.getTrashFonts()

    val trashedFontsCount: LiveData<Int>
        get() = fontDao.getTrashFontsCount()


    fun moveToTrashBatch(
        context: Context,
        fonts: List<FontEntity>,
        cancelFlag: AtomicBoolean?,
        progressListener: OnProgressListener?,
        completeListener: OnBatchCompleteListener?
    ) {
        executorService.execute {
            try {
                val total = fonts.size
                var succeeded = 0
                var failed = 0
                val now = System.currentTimeMillis()

                for (i in 0 until total) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "moveToTrashBatch: cancelled at index $i")
                        break
                    }

                    val font = fonts[i]

                    val sourceFile = File(font.path)
                    if (!sourceFile.exists()) {
                        Log.w(TAG, "Ghost file detected. Removing from DB: " + font.path)
                        fontDao.deleteByPath(font.path)
                        succeeded++
                        progressListener?.onProgress(i + 1, total)
                        continue
                    }

                    val newTrashedPath = TrashFileManager.moveToTrash(
                        context, font.path
                    )

                    if (newTrashedPath != null) {
                        try {
                            fontDao.moveToTrash(
                                font.path,
                                newTrashedPath,
                                now,
                                now
                            )
                            succeeded++
                            Log.d(TAG, "Moved to trash [" + (i + 1) + "/" + total + "]: "
                                    + font.fileName)
                        } catch (e: Exception) {
                            Log.e(TAG, "DB update failed after moving: " + font.path, e)
                            fontDao.deleteByPath(font.path)
                            succeeded++
                        }
                    } else {
                        Log.e(TAG, "Failed to move file to trash: " + font.path)
                        failed++
                    }

                    if (progressListener != null) {
                        val currentProgress = i + 1
                        progressListener.onProgress(currentProgress, total)
                    }
                }

                Log.d(TAG, "moveToTrashBatch done — succeeded: $succeeded, failed: $failed")
                completeListener?.onBatchComplete(succeeded, failed)
            } catch (e: Exception) {
                Log.e(TAG, "moveToTrashBatch: unexpected error", e)
                completeListener?.onBatchComplete(0, 0)
            }
        }
    }


    fun restoreBatch(
        context: Context,
        fonts: List<FontEntity>,
        cancelFlag: AtomicBoolean?,
        progressListener: OnProgressListener?,
        completeListener: OnBatchCompleteListener?
    ) {
        executorService.execute {
            try {
                val total = fonts.size
                var succeeded = 0
                var failed = 0
                val now = System.currentTimeMillis()

                for (i in 0 until total) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "restoreBatch: cancelled at index $i")
                        break
                    }

                    val font = fonts[i]

                    val trashedFile = File(font.path)
                    if (!trashedFile.exists()) {
                        Log.w(TAG, "Ghost file detected in trash. Cleaning DB: " + font.path)
                        fontDao.deleteFromTrash(font.path)
                        succeeded++
                        progressListener?.onProgress(i + 1, total)
                        continue
                    }

                    val originalPath: String = font.originalPath ?: font.path

                    val restoredPath = TrashFileManager.restoreFromTrash(
                        context,
                        font.path,
                        originalPath
                    )

                    if (restoredPath != null) {
                        val newFileName = File(restoredPath).name
                        try {
                            fontDao.restoreFromTrash(
                                font.path,
                                restoredPath,
                                newFileName,
                                now
                            )
                            succeeded++
                            Log.d(TAG, "Restored [" + (i + 1) + "/" + total + "]: "
                                    + font.fileName + " → " + restoredPath)
                        } catch (e: Exception) {
                            Log.e(TAG, "DB update failed after restoring: " + font.path, e)
                            fontDao.deleteFromTrash(font.path)
                            succeeded++
                        }
                    } else {
                        Log.e(TAG, "Failed to restore file: " + font.path)
                        failed++
                    }

                    if (progressListener != null) {
                        val currentProgress = i + 1
                        progressListener.onProgress(currentProgress, total)
                    }
                }

                Log.d(TAG, "restoreBatch done — succeeded: $succeeded, failed: $failed")
                completeListener?.onBatchComplete(succeeded, failed)
            } catch (e: Exception) {
                Log.e(TAG, "restoreBatch: unexpected error", e)
                completeListener?.onBatchComplete(0, 0)
            }
        }
    }


    fun deletePermanentlyBatch(
        context: Context,
        fonts: List<FontEntity>,
        cancelFlag: AtomicBoolean?,
        progressListener: OnProgressListener?,
        completeListener: OnBatchCompleteListener?
    ) {
        executorService.execute {
            try {
                val total = fonts.size
                var succeeded = 0
                var failed = 0

                for (i in 0 until total) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "deletePermanentlyBatch: cancelled at index $i")
                        break
                    }

                    val font = fonts[i]

                    val fileDeleted = TrashFileManager.deletePermanently(font.path)

                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.path)
                            succeeded++
                            Log.d(TAG, "Permanently deleted [" + (i + 1) + "/" + total + "]: "
                                    + font.fileName)
                        } catch (e: Exception) {
                            Log.e(TAG, "DB delete failed after file deletion: " + font.path, e)
                            failed++
                        }
                    } else {
                        Log.e(TAG, "Failed to permanently delete file: " + font.path)
                        failed++
                    }

                    if (progressListener != null) {
                        val currentProgress = i + 1
                        progressListener.onProgress(currentProgress, total)
                    }
                }

                Log.d(TAG, "deletePermanentlyBatch done — succeeded: " + succeeded
                        + ", failed: " + failed)
                completeListener?.onBatchComplete(succeeded, failed)
            } catch (e: Exception) {
                Log.e(TAG, "deletePermanentlyBatch: unexpected error", e)
                completeListener?.onBatchComplete(0, 0)
            }
        }
    }


    fun emptyTrash(
        context: Context,
        cancelFlag: AtomicBoolean?,
        progressListener: OnProgressListener?,
        completeListener: OnBatchCompleteListener?
    ) {
        executorService.execute {
            try {
                // NOTE(kotlin-migration): the DAO returns a non-null List in Kotlin; the Java null check is kept for fidelity.
                val allTrashed: List<FontEntity>? = fontDao.getTrashFontsSync()

                if (allTrashed == null || allTrashed.isEmpty()) {
                    Log.d(TAG, "emptyTrash: trash is already empty")
                    completeListener?.onBatchComplete(0, 0)
                    return@execute
                }

                Log.d(TAG, "emptyTrash: deleting " + allTrashed.size + " items")

                val total = allTrashed.size
                var succeeded = 0
                var failed = 0

                for (i in 0 until total) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "emptyTrash: cancelled at index $i")
                        break
                    }

                    val font = allTrashed[i]
                    val fileDeleted = TrashFileManager.deletePermanently(font.path)

                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.path)
                            succeeded++
                        } catch (e: Exception) {
                            Log.e(TAG, "DB delete failed during emptyTrash: " + font.path, e)
                            failed++
                        }
                    } else {
                        Log.e(TAG, "File delete failed during emptyTrash: " + font.path)
                        failed++
                    }

                    progressListener?.onProgress(i + 1, total)
                }

                Log.d(TAG, "emptyTrash done — succeeded: $succeeded, failed: $failed")
                completeListener?.onBatchComplete(succeeded, failed)

            } catch (e: Exception) {
                Log.e(TAG, "emptyTrash: unexpected error", e)
                completeListener?.onBatchComplete(0, 0)
            }
        }
    }


    fun deleteExpiredItems(completeListener: OnBatchCompleteListener?) {
        executorService.execute {
            try {
                val expirationThreshold = System.currentTimeMillis() - TRASH_EXPIRY_MS

                // NOTE(kotlin-migration): the DAO returns a non-null List in Kotlin; the Java null check is kept for fidelity.
                val expiredFonts: List<FontEntity>? =
                    fontDao.getExpiredTrashFonts(expirationThreshold)

                if (expiredFonts == null || expiredFonts.isEmpty()) {
                    Log.d(TAG, "deleteExpiredItems: no expired items found")
                    completeListener?.onBatchComplete(0, 0)
                    return@execute
                }

                Log.d(TAG, "deleteExpiredItems: found " + expiredFonts.size + " expired items")

                var succeeded = 0
                var failed = 0

                for (font in expiredFonts) {
                    val fileDeleted = TrashFileManager.deletePermanently(font.path)
                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.path)
                            succeeded++
                        } catch (e: Exception) {
                            Log.e(TAG, "DB delete failed for expired item: " + font.path, e)
                            failed++
                        }
                    } else {
                        failed++
                    }
                }

                Log.d(TAG, "deleteExpiredItems done — deleted: " + succeeded
                        + ", failed: " + failed)
                completeListener?.onBatchComplete(succeeded, failed)

            } catch (e: Exception) {
                Log.e(TAG, "deleteExpiredItems: unexpected error", e)
                completeListener?.onBatchComplete(0, 0)
            }
        }
    }


    fun interface OnProgressListener {
        fun onProgress(current: Int, total: Int)
    }

    fun interface OnBatchCompleteListener {
        fun onBatchComplete(succeeded: Int, failed: Int)
    }

    companion object {
        private const val TAG = "TrashRepository"

        const val TRASH_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000

        @Volatile
        private var INSTANCE: TrashRepository? = null

        fun getInstance(context: Context): TrashRepository {
            if (INSTANCE == null) {
                synchronized(TrashRepository::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = TrashRepository(context.applicationContext)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
