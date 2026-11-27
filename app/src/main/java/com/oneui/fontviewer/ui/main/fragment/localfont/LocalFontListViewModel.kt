package com.oneui.fontviewer.ui.main.fragment.localfont

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.map

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.localfont.LocalFontCache
import com.oneui.fontviewer.data.localfont.LocalFontPreferenceManager
import com.oneui.fontviewer.data.localfont.LocalFontRepository
import com.oneui.fontviewer.data.localfont.file.FontFileManager
import com.oneui.fontviewer.data.localfont.file.LocalFontDirectory
import com.oneui.fontviewer.data.localfont.model.FontFileInfoWithMetadata
import com.oneui.fontviewer.data.trash.TrashRepository
import com.oneui.fontviewer.service.BatchOperationState
import com.oneui.fontviewer.service.OperationForegroundService
import com.oneui.fontviewer.service.OperationNotifications
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem

class LocalFontListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: LocalFontRepository = LocalFontRepository.getInstance(application)
    private val trashRepository: TrashRepository = TrashRepository.getInstance(application)
    private val preferenceManager: LocalFontPreferenceManager = LocalFontPreferenceManager(application)
    private val loadingMutableLiveData: MutableLiveData<Boolean> = MutableLiveData(false)
    private val errorMessageMutableLiveData: MutableLiveData<String?> = MutableLiveData()
    private val fontsMutableLiveData: MutableLiveData<List<FontEntity>> = MutableLiveData()

    // بلا قيمة ابتدائية: القيمة الفارغة المبدئية كانت تصل للواجهة قبل قائمة المفضلة الحقيقية،
    // فتعرض رسالة "لا توجد خطوط في المفضلة بعد" بالخطأ
    private val favoritesMutableLiveData: MutableLiveData<List<FontEntity>> = MutableLiveData()

    private var mIsFolderSyncing = false
    private var mPendingSyncFonts: List<FontEntity>? = null

    private var mLastSilentRefreshTime = 0L

    private var trashCancelFlag: AtomicBoolean = AtomicBoolean(false)

    // خيط خلفي مخصص لتحميل معاينات الخطوط قبل نشر القائمة للواجهة
    private val fontPreloadExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // نحتفظ بمرجع الـ LiveData الأصلية ومرجع الـ Observer لكل منهما
    // حتى نقدر نفك ربطهما صح في onCleared() ونمنعهما من الاستمرار بالعمل بعد موت الـ ViewModel
    private val mLocalFontsSource: LiveData<List<FontEntity>>
    private val mFavoriteFontsSource: LiveData<List<FontEntity>>
    private val mLocalFontsObserver: Observer<List<FontEntity>>
    private val mFavoriteFontsObserver: Observer<List<FontEntity>>

    init {
        mLocalFontsSource = repository.localFonts
        mLocalFontsObserver = Observer { entities ->
            if (entities != null) {
                if (mIsFolderSyncing) {
                    mPendingSyncFonts = entities
                } else {
                    publishFontsAfterPreload(entities, null)
                }
            }
        }
        mLocalFontsSource.observeForever(mLocalFontsObserver)

        mFavoriteFontsSource = repository.favoriteFonts
        mFavoriteFontsObserver = Observer { entities ->
            if (entities != null) {
                favoritesMutableLiveData.postValue(entities)
            }
        }
        mFavoriteFontsSource.observeForever(mFavoriteFontsObserver)
    }

    /**
     * يحمّل معاينات الخطوط لكل عناصر القائمة داخل LocalFontCache على خيط
     * خلفي، ثم يُرسل القائمة لواجهة المستخدم فقط بعد اكتمال التحميل.
     */
    private fun publishFontsAfterPreload(entities: List<FontEntity>, onPublished: Runnable?) {
        if (fontPreloadExecutor.isShutdown) {
            // الـ ViewModel اتقفلت (onCleared) بالفعل — منحاولش ننفذ حاجة على Executor مقفول
            Log.w(TAG, "publishFontsAfterPreload: executor is shut down, skipping")
            return
        }
        try {
            fontPreloadExecutor.execute {
                val paths: MutableList<String> = ArrayList()
                for (entity in entities) {
                    paths.add(entity.path)
                }
                LocalFontCache.getInstance().preloadFontsBlocking(paths)
                fontsMutableLiveData.postValue(entities)
                onPublished?.run()
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "publishFontsAfterPreload: task rejected, executor already shut down", e)
        }
    }

    val fontsLiveData: LiveData<List<FontFileInfoWithMetadata>>
        get() = fontsMutableLiveData.map { entities ->
            val result: MutableList<FontFileInfoWithMetadata> = ArrayList()
            if (entities != null) {
                for (entity in entities) {
                    result.add(FontFileInfoWithMetadata(entity))
                }
            }
            result
        }

    val favoritesLiveData: LiveData<List<FontFileInfoWithMetadata>>
        get() = favoritesMutableLiveData.map { entities ->
            val result: MutableList<FontFileInfoWithMetadata> = ArrayList()
            if (entities != null) {
                for (entity in entities) {
                    result.add(FontFileInfoWithMetadata(entity))
                }
            }
            result
        }

    fun toggleFavoritesBatch(paths: List<String>?, isFavorite: Boolean, onSuccess: Runnable?) {
        if (paths == null || paths.isEmpty()) return

        BatchOperationState.setProcessing(true)

        repository.updateFavoriteStatusBatch(paths, isFavorite) { success ->
            if (success) {
                Log.d(TAG, "★ Batch favorite toggled: " + paths.size + " fonts → " + isFavorite)
                if (onSuccess != null) {
                    Handler(Looper.getMainLooper()).post {
                        onSuccess.run()
                        BatchOperationState.setProcessing(false)
                    }
                } else {
                    BatchOperationState.setProcessing(false)
                }
            } else {
                Log.w(TAG, "Failed to batch toggle favorites")
                BatchOperationState.setProcessing(false)
            }
        }
    }

    fun renameFontInMemory(oldPath: String, newFileName: String): Boolean {
        val renameResult = FontFileManager.renameFile(oldPath, newFileName)

        if (!renameResult.isSuccess) {
            errorMessageMutableLiveData.postValue(renameResult.errorMessage)
            return false
        }

        val newPath = renameResult.newPath!!
        val newLastModified = renameResult.newLastModified

        val currentList = fontsMutableLiveData.value
        if (currentList != null) {
            val updatedList: MutableList<FontEntity> = ArrayList(currentList)

            for (i in updatedList.indices) {
                val entity = updatedList[i]
                if (entity.path == oldPath) {
                    val updatedEntity = FontEntity(
                        newPath,
                        newFileName
                    )
                    updatedEntity.size = entity.size
                    updatedEntity.lastModified = newLastModified
                    updatedEntity.realName = entity.realName
                    updatedEntity.accessCount = entity.accessCount
                    updatedEntity.lastAccessTime = entity.lastAccessTime
                    updatedEntity.weightWidthLabel = entity.weightWidthLabel
                    updatedEntity.isFavorite = entity.isFavorite

                    updatedList[i] = updatedEntity
                    break
                }
            }

            fontsMutableLiveData.postValue(updatedList)
        }

        val currentFavorites = favoritesMutableLiveData.value
        if (currentFavorites != null) {
            val updatedFavorites: MutableList<FontEntity> = ArrayList(currentFavorites)

            for (i in updatedFavorites.indices) {
                val entity = updatedFavorites[i]
                if (entity.path == oldPath) {
                    val updatedEntity = FontEntity(
                        newPath,
                        newFileName
                    )
                    updatedEntity.size = entity.size
                    updatedEntity.lastModified = newLastModified
                    updatedEntity.realName = entity.realName
                    updatedEntity.accessCount = entity.accessCount
                    updatedEntity.lastAccessTime = entity.lastAccessTime
                    updatedEntity.weightWidthLabel = entity.weightWidthLabel
                    updatedEntity.isFavorite = true

                    updatedFavorites[i] = updatedEntity
                    break
                }
            }

            favoritesMutableLiveData.postValue(updatedFavorites)
        }

        repository.updatePath(oldPath, newPath, newFileName)

        Log.d(TAG, "Font renamed in memory: $oldPath -> $newPath")

        return true
    }

    fun moveFontsToTrashInMemory(
        paths: List<String>,
        progressListener: TrashRepository.OnProgressListener?,
        onComplete: Runnable?
    ) {
        if (paths.isEmpty()) return

        val currentList = fontsMutableLiveData.value
        if (currentList == null || currentList.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: font list is empty")
            if (onComplete != null) Handler(Looper.getMainLooper()).post(onComplete)
            return
        }

        val fontsToMove: MutableList<FontEntity> = ArrayList()
        for (entity in currentList) {
            if (paths.contains(entity.path)) {
                fontsToMove.add(entity)
            }
        }

        if (fontsToMove.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: no matching entities found for given paths")
            if (onComplete != null) Handler(Looper.getMainLooper()).post(onComplete)
            return
        }

        if (BatchOperationState.getSourceDrawerListItem() == null) {
            BatchOperationState.setSourceDrawerListItem(DrawerListItem.LOCAL_FONTS)
        }
        BatchOperationState.setProcessing(true)

        trashCancelFlag = AtomicBoolean(false)
        BatchOperationState.setCancelFlag(trashCancelFlag)

        val startTime = System.currentTimeMillis()
        val app = getApplication<Application>()

        val movingTitle = app.resources
            .getQuantityString(R.plurals.progress_moving_to_trash, fontsToMove.size)
        val moveServiceIntent = Intent(app, OperationForegroundService::class.java)
        moveServiceIntent.putExtra(
            OperationForegroundService.EXTRA_NOTIF_ID,
            OperationNotifications.NOTIF_ID_MOVE
        )
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, movingTitle)
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fontsToMove.size)
        ContextCompat.startForegroundService(app, moveServiceIntent)

        trashRepository.moveToTrashBatch(
            app,
            fontsToMove,
            trashCancelFlag,

            TrashRepository.OnProgressListener { current, total ->
                OperationNotifications.updateMoveToTrashNotification(app, current, total)

                val progressTitle = app.resources
                    .getQuantityString(R.plurals.progress_moving_to_trash, total)
                BatchOperationState.updateProgress(current, total, progressTitle)

                if (progressListener != null) {
                    try {
                        progressListener.onProgress(current, total)
                    } catch (e: Exception) {
                        Log.w(
                            TAG, "progressListener.onProgress() failed"
                                    + " (fragment may be detached): " + e.message
                        )
                    }
                }
            },

            TrashRepository.OnBatchCompleteListener { succeeded, failed ->
                Log.d(
                    TAG, "moveFontsToTrashInMemory done"
                            + " — succeeded: " + succeeded
                            + ", failed: " + failed
                            + ", cancelled: " + trashCancelFlag.get()
                )

                val elapsedTime = System.currentTimeMillis() - startTime
                val delay = Math.max(0L, MIN_DIALOG_DURATION_MS - elapsedTime)

                if (onComplete != null) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        app.stopService(Intent(app, OperationForegroundService::class.java))
                        OperationNotifications.dismissMoveToTrashNotification(app)

                        onComplete.run()
                        BatchOperationState.setProcessing(false)
                    }, delay)
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({
                        app.stopService(Intent(app, OperationForegroundService::class.java))
                        OperationNotifications.dismissMoveToTrashNotification(app)
                        BatchOperationState.setProcessing(false)
                    }, delay)
                }
            }
        )
    }

    fun cancelTrashOperation() {
        trashCancelFlag.set(true)
        Log.d(TAG, "cancelTrashOperation: cancellation requested")
    }

    val isLoadingLiveData: LiveData<Boolean>
        get() = loadingMutableLiveData

    fun loadFonts() {
        val folderPath = preferenceManager.fontFolderPath
        if (folderPath == null) {
            Log.w(TAG, "No folder path saved")
            return
        }

        loadFontsFromPath(folderPath, false, false)
    }

    fun loadFontsFromPath(folderPath: String?) {
        loadFontsFromPath(folderPath, true, false)
    }

    /**
     * يفحص مجلد الخطوط بصمت بعد عودة المستخدم للتطبيق (مثلاً بعد إضافة ملف من تطبيق ملفات آخر)،
     * دون شاشة تحميل، وينشر القائمة للواجهة فقط إن تغيّر شيء فعلاً، حتى لا يحدث وميض عند عدم وجود تغييرات.
     */
    fun refreshFontsSilently() {
        val folderPath = preferenceManager.fontFolderPath
        if (folderPath == null || folderPath.isEmpty()) return

        // فحص سابق أو تحميل ما زال شغالاً، لا نبدأ فحصاً ثانياً فوقه
        if (mIsFolderSyncing || java.lang.Boolean.TRUE == loadingMutableLiveData.value) return

        val now = SystemClock.elapsedRealtime()
        if (now - mLastSilentRefreshTime < MIN_SILENT_REFRESH_INTERVAL_MS) return
        mLastSilentRefreshTime = now

        if (fontPreloadExecutor.isShutdown) return
        try {
            fontPreloadExecutor.execute {
                // إن كان المجلد غير متاح الآن (صلاحية سُحبت، أو ذاكرة خارجية انفصلت) نتجاهل الفحص،
                // لأن المزامنة كانت ستعدّ ذلك مجلداً فارغاً وتحذف كل الخطوط من قاعدة البيانات
                if (!LocalFontDirectory.isAccessible(folderPath)) {
                    Log.w(TAG, "refreshFontsSilently: folder not accessible, skipping")
                    return@execute
                }
                Handler(Looper.getMainLooper()).post {
                    // قد يبدأ تحميل آخر أو يتغير المجلد أثناء فحص الوصول، فنتأكد مرة أخيرة قبل المزامنة
                    if (mIsFolderSyncing || folderPath != preferenceManager.fontFolderPath) return@post
                    loadFontsFromPath(folderPath, false, true)
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "refreshFontsSilently: task rejected, executor already shut down", e)
        }
    }

    fun loadFontsFromPath(folderPath: String?, showLoading: Boolean) {
        loadFontsFromPath(folderPath, showLoading, false)
    }

    private fun loadFontsFromPath(folderPath: String?, showLoading: Boolean, publishOnlyIfChanged: Boolean) {
        if (folderPath == null || folderPath.isEmpty()) {
            return
        }

        if (java.lang.Boolean.TRUE == BatchOperationState.getIsProcessing().value) {
            Log.w(TAG, "Skipping load and sync: A batch operation is currently running.")
            return
        }

        if (showLoading) {
            loadingMutableLiveData.postValue(true)
        }

        mIsFolderSyncing = true

        val startTime = System.currentTimeMillis()

        // عدد التغييرات التي وجدتها المزامنة (إضافة + تعديل + حذف)، نحتاجه في الفحص الصامت
        val changedCount = intArrayOf(0)

        repository.loadAndSyncLocalFonts(folderPath, object : LocalFontRepository.OnSyncCompleteListener {
            override fun onSyncComplete(added: Int, updated: Int, deleted: Int) {
                changedCount[0] = added + updated + deleted
                val message = String.format(
                    "Synced: %d added, %d updated, %d deleted",
                    added, updated, deleted
                )
                Log.d(TAG, message)
            }

            override fun onFullExtractionComplete() {
                // الفحص الصامت ولم يتغير شيء في المجلد: لا ننشر القائمة ولا نلمس الواجهة، فلا وميض
                if (publishOnlyIfChanged && changedCount[0] == 0) {
                    Handler(Looper.getMainLooper()).post {
                        mIsFolderSyncing = false

                        // إن وصل تحديث من قاعدة البيانات أثناء الفحص (مثل استخراج اسم خط) لا نضيّعه
                        val pending = mPendingSyncFonts
                        mPendingSyncFonts = null
                        if (pending != null) {
                            publishFontsAfterPreload(pending, null)
                        }
                    }
                    return
                }

                var freshFontsFromDb: List<FontEntity>?
                try {
                    freshFontsFromDb = repository.localFontsSync
                } catch (e: Exception) {
                    freshFontsFromDb = null
                }
                val finalFreshFonts = freshFontsFromDb

                val elapsedTime = System.currentTimeMillis() - startTime
                val delay = if (showLoading) Math.max(0L, 2500 - elapsedTime) else 0L

                Handler(Looper.getMainLooper()).postDelayed({
                    mIsFolderSyncing = false

                    val pendingFonts = mPendingSyncFonts
                    val toPublish: List<FontEntity> = if (finalFreshFonts != null) {
                        finalFreshFonts
                    } else if (pendingFonts != null) {
                        pendingFonts
                    } else {
                        fontsMutableLiveData.value ?: ArrayList()
                    }
                    mPendingSyncFonts = null

                    publishFontsAfterPreload(toPublish) {
                        if (showLoading) {
                            loadingMutableLiveData.postValue(false)
                        }
                    }
                }, delay)
            }
        })
    }

    fun recordFontAccess(fontPath: String?) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath)
        }
    }

    fun saveFolderPath(folderPath: String?) {
        if (folderPath != null && !folderPath.isEmpty()) {
            preferenceManager.saveFontFolderPath(folderPath)
        }
    }

    fun hasSavedFolder(): Boolean {
        return preferenceManager.hasFontFolderPath()
    }

    override fun onCleared() {
        super.onCleared()
        // لازم نلغي تسجيل الـ Observers قبل قفل الـ Executor
        // عشان أي تحديث متأخر من قاعدة البيانات ميحاولش يستخدم Executor مقفول
        mLocalFontsSource.removeObserver(mLocalFontsObserver)
        mFavoriteFontsSource.removeObserver(mFavoriteFontsObserver)
        fontPreloadExecutor.shutdownNow()
    }

    companion object {
        private const val TAG = "LocalFontListViewModel"

        private const val MIN_DIALOG_DURATION_MS = 2500L

        // أقل مدة بين فحصين صامتين متتاليين للمجلد، حتى لا يتكرر الفحص عند التنقل السريع بين التطبيقات
        private const val MIN_SILENT_REFRESH_INTERVAL_MS = 2000L
    }
}
