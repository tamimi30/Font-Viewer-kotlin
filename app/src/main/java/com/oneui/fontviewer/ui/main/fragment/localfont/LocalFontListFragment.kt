package com.oneui.fontviewer.ui.main.fragment.localfont

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.appbar.AppBarLayout

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import dev.oneuiproject.oneui.dialog.ProgressDialog

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.list.FontFileInfo
import com.oneui.fontviewer.data.localfont.LocalFontCache
import com.oneui.fontviewer.data.localfont.model.FontFileInfoWithMetadata
import com.oneui.fontviewer.data.trash.TrashRepository
import com.oneui.fontviewer.service.BatchOperationState
import com.oneui.fontviewer.service.OperationNotifications
import com.oneui.fontviewer.ui.dialog.RenameFontDialog
import com.oneui.fontviewer.ui.main.MainActivity
import com.oneui.fontviewer.ui.main.fragment.localfont.manager.LocalFontDirectoryPicker
import com.oneui.fontviewer.ui.main.fragment.localfont.manager.LocalFontPermissionManager
import com.oneui.fontviewer.ui.main.fragment.localfont.manager.LocalFontSelectionManager
import com.oneui.fontviewer.ui.main.fragment.localfont.recyclerview.LocalFontListAdapter
import com.oneui.fontviewer.ui.main.list.UiStateManager
import com.oneui.fontviewer.ui.main.list.recyclerview.DurationItemAnimator
import com.oneui.fontviewer.ui.main.list.recyclerview.ItemDecoration
import com.oneui.fontviewer.ui.main.list.search.SearchFilterManager
import com.oneui.fontviewer.ui.main.list.search.SearchViewModel
import com.oneui.fontviewer.ui.main.list.sort.SortManager
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.settings.SettingsViewModel
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

class LocalFontListFragment : Fragment(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var mContext: Context
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: LocalFontListAdapter? = null
    private var mFontSelectedListener: OnFontSelectedListener? = null
    private lateinit var mMainHandler: Handler
    private lateinit var mExecutor: ExecutorService
    private var mAppBarLayout: AppBarLayout? = null
    private var mDrawerLayout: OneUiDrawerLayout? = null

    private lateinit var mLocalFontPermissionManager: LocalFontPermissionManager
    private lateinit var mLocalFontDirectoryPicker: LocalFontDirectoryPicker
    private lateinit var mSearchManager: SearchFilterManager
    private lateinit var mSortManager: SortManager
    private lateinit var mUIManager: UiStateManager

    private var mSelectionManager: LocalFontSelectionManager? = null

    private lateinit var mViewModel: LocalFontListViewModel
    private lateinit var mSearchViewModel: SearchViewModel
    private lateinit var mSettingsViewModel: SettingsViewModel
    private var mCurrentFontsList: List<FontFileInfoWithMetadata> = ArrayList()

    // مسارات الخطوط المفضلة، لمعرفة حالة المفضلة بسرعة دون المرور على القائمة كاملة مع كل bind
    private val mFavoritePaths: MutableSet<String> = HashSet()

    private var mIsFirstLoad = true

    // يصبح true عند إيقاف الشاشة (التطبيقات الأخيرة أو الانتقال لشاشة أخرى)، فنفحص المجلد عند العودة
    private var mWasStopped = false

    private var mHasNotifiedReady = false
    private var mAwaitingInitialFontsLoad = false

    private var mNeedsScrollRestore = false

    private var mBackPressedTime = 0L

    private var mMenu: Menu? = null

    private var mIsBatchOperationRunning = false

    // هل عُرضت في هذه الواجهة قائمة خطوط واحدة على الأقل؟ نؤجّل تحديثات القائمة أثناء العملية الجارية فقط
    // بعد عرض أول قائمة، أما أول قائمة فنعرضها فوراً حتى لا تبقى الواجهة فارغة (رسالة "لا توجد خطوط")
    private var mHasAppliedFonts = false

    private var mPendingFontsUpdate: List<FontFileInfoWithMetadata>? = null

    private var mCurrentProgressDialog: ProgressDialog? = null

    fun interface OnFontSelectedListener {
        fun onFontSelected(
            fontPath: String, realName: String?, fileName: String?,
            ttcIndex: Int, weightWidthLabel: String?
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
        if (context is OnFontSelectedListener) {
            mFontSelectedListener = context
        }

        mLocalFontPermissionManager = LocalFontPermissionManager(this)
        mLocalFontDirectoryPicker = LocalFontDirectoryPicker(this)
        mSearchManager = SearchFilterManager()

        mSortManager = SortManager(mContext, false)

        mUIManager = UiStateManager(mContext)

        setupDirectoryPickerListener()
        setupPermissionListener()
        setupSearchListener()
        setupSortListener()
    }

    private fun setupDirectoryPickerListener() {
        mLocalFontDirectoryPicker.setDirectorySelectionListener(object : LocalFontDirectoryPicker.DirectorySelectionListener {
            override fun onDirectorySelected(directoryPath: String) {
                if (::mViewModel.isInitialized) {
                    mAppBarLayout?.setExpanded(false, true)

                    mAwaitingInitialFontsLoad = true
                    mViewModel.saveFolderPath(directoryPath)
                    mViewModel.loadFontsFromPath(directoryPath)
                }
                mUIManager.updateUIVisibility(true)
                updateMainActivityFolderState(true)

                val menu = mMenu
                if (menu != null) {
                    val changeFolderItem = menu.findItem(R.id.action_change_folder)
                    if (changeFolderItem != null) {
                        val isSearchActive = ::mSearchViewModel.isInitialized && mSearchViewModel.isSearchActive
                        changeFolderItem.isVisible = !isSearchActive
                    }
                }
            }

            override fun onDirectorySelectionCancelled() {
                Toast.makeText(mContext, R.string.toast_folder_selection_cancelled, Toast.LENGTH_SHORT).show()
            }

            override fun onDirectorySelectionError(error: Exception) {
                Toast.makeText(mContext, getString(R.string.toast_error_with_message, error.message), Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupPermissionListener() {
        mLocalFontPermissionManager.setPermissionResultListener(object : LocalFontPermissionManager.PermissionResultListener {
            override fun onPermissionGranted() {
                mLocalFontDirectoryPicker.openDirectoryPicker()
            }

            override fun onPermissionDenied() {
                Toast.makeText(mContext, getString(R.string.toast_storage_permission_required), Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun setupSearchListener() {
        mSearchManager.setSearchResultListener { _, empty ->
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive)
        }
    }

    private fun setupSortListener() {
        mSortManager.setSortChangeListener { type, asc ->
            mAdapter?.setSortOptions(type, asc)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        mMainHandler = Handler(Looper.getMainLooper())
        mExecutor = Executors.newFixedThreadPool(4)

        initializeViewModels()
        setupViewModelObservers()

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.mMenu = menu

        menu.clear()
        inflater.inflate(R.menu.menu_font_list_search, menu)
        inflater.inflate(R.menu.menu_local_fonts_more, menu)

        val searchItem = menu.findItem(R.id.action_search_fonts)
        val act = activity
        if (act is MainActivity && searchItem != null) {
            act.searchCoordinator.bindSearchMenuItem(searchItem)
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val changeFolderItem = menu.findItem(R.id.action_change_folder)
        if (changeFolderItem != null) {
            val isSearchActive = ::mSearchViewModel.isInitialized && mSearchViewModel.isSearchActive

            changeFolderItem.isVisible = !isSearchActive && mViewModel.hasSavedFolder()
        }
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_change_folder) {
            openFolderPickerPublic()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun initializeViewModels() {
        mViewModel = ViewModelProvider(this).get(LocalFontListViewModel::class.java)
        mSearchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        mSettingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
    }

    // يحدّث القائمة الحالية وقائمة المسارات المفضلة معاً، ليبقيا متطابقين دائماً
    private fun updateCurrentFontsList(fonts: List<FontFileInfoWithMetadata>) {
        val copy: List<FontFileInfoWithMetadata> = ArrayList(fonts)
        mCurrentFontsList = copy

        mFavoritePaths.clear()
        for (font in copy) {
            if (font.isFavorite) {
                mFavoritePaths.add(font.path)
            }
        }
    }

    private fun setupViewModelObservers() {
        mViewModel.fontsLiveData.observe(this) { fonts ->
            if (fonts != null) {
                notifyMainActivityReadyOnce()
                mAwaitingInitialFontsLoad = false

                // عند فتح التطبيق من إشعار التقدم بعد حذفه من التطبيقات الأخيرة تُنشأ الواجهة من جديد والعملية ما زالت جارية،
                // فلو أجّلنا أول قائمة تبقى الواجهة فارغة وتظهر رسالة "لا توجد خطوط في هذا المجلد" طوال العملية.
                // لذلك لا نؤجّل إلا بعد أن تكون قد عُرضت قائمة فعلاً
                if (mIsBatchOperationRunning && mHasAppliedFonts) {
                    mPendingFontsUpdate = ArrayList(fonts)
                    return@observe
                }

                mHasAppliedFonts = true
                updateCurrentFontsList(fonts)

                val adapter = mAdapter
                if (adapter != null) {
                    adapter.setAllFontsMetadata(fonts)

                    adapter.notifyAllFavoritesChanged()
                }

                refreshAdapterData()

                updateMainActivityFontsCount(fonts.size)

                val pathsToPreload: MutableList<String> = ArrayList()
                for (font in fonts) {
                    pathsToPreload.add(font.path)
                }
                LocalFontCache.getInstance().preloadFonts(pathsToPreload)
            }
        }

        mViewModel.isLoadingLiveData.observe(this) { isLoading ->
            if (isLoading == true) {
                mUIManager.showLoadingState()
                if (!isHidden) {
                    setDrawerLocked(true)
                }
            } else {
                mUIManager.hideLoadingState()
                refreshAdapterData()
                setDrawerLocked(false)

                val recyclerView = mRecyclerView
                if (recyclerView != null && recyclerView.visibility == View.VISIBLE) {
                    recyclerView.alpha = 0f
                    recyclerView.animate().alpha(1f).setDuration(650).start()
                }
            }
        }

        mSearchViewModel.searchQueryLiveData.observe(this) { query ->
            if (query != null) {
                mSearchManager.filterFonts(query)
                mAdapter?.updateFilteredFonts(
                    mSearchManager.filteredFonts,
                    mSearchManager.currentSearchQuery
                )
            }
        }

        mSettingsViewModel.fontPreviewEnabled.observe(this) { enabled ->
            val adapter = mAdapter
            if (adapter != null && isAdded) {
                adapter.setFontPreviewEnabled(enabled == true)
                Log.d(TAG, "Font preview setting changed: $enabled")
            }
        }

        BatchOperationState.getIsProcessing().observe(this) { isProcessing ->
            mIsBatchOperationRunning = isProcessing

            val dialog = mCurrentProgressDialog
            if (!isProcessing && dialog != null && dialog.isShowing) {
                dialog.dismiss()
                mCurrentProgressDialog = null
            }

            val currentDialog = mCurrentProgressDialog
            if (isProcessing
                && !isHidden
                && BatchOperationState.getSourceDrawerListItem() == DrawerListItem.LOCAL_FONTS
                && (currentDialog == null || !currentDialog.isShowing)
            ) {
                reconnectToProgressDialog()
            }

            val pending = mPendingFontsUpdate
            if (!isProcessing && pending != null) {
                updateCurrentFontsList(pending)
                val adapter = mAdapter
                if (adapter != null) {
                    adapter.setAllFontsMetadata(pending)
                    adapter.notifyAllFavoritesChanged()
                }
                refreshAdapterData()

                updateMainActivityFontsCount(mCurrentFontsList.size)
                mPendingFontsUpdate = null
            }
        }

        BatchOperationState.getProgress().observe(this) { progressData ->
            val dialog = mCurrentProgressDialog
            if (dialog != null
                && dialog.isShowing
                && progressData != null
            ) {
                dialog.setMax(progressData.total)
                dialog.setProgress(progressData.current)
            }
        }

        mSearchViewModel.isSearchActiveLiveData.observe(this) { isActive ->
            val menu = mMenu
            if (menu != null) {
                val changeFolderItem = menu.findItem(R.id.action_change_folder)
                if (changeFolderItem != null) {
                    if (isActive != true) {
                        changeFolderItem.isVisible = mViewModel.hasSavedFolder()
                    }
                }
            }
        }
    }

    private fun restoreInstanceState(state: Bundle) {
        @Suppress("DEPRECATION")
        mUIManager.recyclerViewState = state.getParcelable<Parcelable>("recycler_state")

        if (mUIManager.recyclerViewState != null) {
            mNeedsScrollRestore = true
        }

        val sortType = state.getString("sort_type")
        if (sortType != null) {
            try {
                mUIManager.saveSortState(
                    SortByItemLayout.SortType.valueOf(sortType),
                    state.getBoolean("sort_asc", true)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring sort state", e)
            }
        }
        mIsFirstLoad = state.getBoolean("is_first_load", true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_local_font_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupDrawerLayout()
        setupFolderButton(view)

        initializeSelectionManager()

        if (mIsFirstLoad) {
            if (mViewModel.hasSavedFolder()) {
                mViewModel.loadFonts()
            } else {
                notifyMainActivityReadyOnce()
            }
            mIsFirstLoad = false
        }
    }

    private fun initializeViews(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.font_recycler_view)
        mRecyclerView = recyclerView

        mUIManager.setViews(
            view.findViewById<View>(R.id.select_folder_container),
            view.findViewById<View>(R.id.main_content_layout),
            view.findViewById<View>(R.id.empty_view),
            view.findViewById<TextView>(R.id.empty_text),
            recyclerView
        )

        mUIManager.setEmptyTitleView(view.findViewById<TextView>(R.id.empty_title))

        mUIManager.setNoResultsTextView(view.findViewById<TextView>(R.id.no_results_text))

        mUIManager.setEmptyIconView(view.findViewById<View>(R.id.empty_icon))

        mUIManager.setLoadingContainer(view.findViewById<View>(R.id.loading_container))

        mUIManager.updateUIVisibility(mViewModel.hasSavedFolder())

        mAwaitingInitialFontsLoad = mViewModel.hasSavedFolder()

        updateMainActivityFolderState(mViewModel.hasSavedFolder())
    }

    private fun setupDrawerLayout() {
        val act = activity
        if (act != null) {
            val drawer = act.findViewById<View>(R.id.drawer_layout)
            if (drawer is OneUiDrawerLayout) {
                mDrawerLayout = drawer
                val appBar = drawer.appBarLayout
                mAppBarLayout = appBar
                appBar.addOnOffsetChangedListener(this)
                mUIManager.setAppBarLayout(appBar)
            }
        }
    }

    private fun setupFolderButton(view: View) {
        val folderBtn = view.findViewById<View>(R.id.select_folder_button)
        if (folderBtn != null) {
            folderBtn.setOnClickListener {
                if (mLocalFontPermissionManager.hasRequiredPermissions()) {
                    mLocalFontDirectoryPicker.openDirectoryPicker()
                } else {
                    mLocalFontPermissionManager.requestPermissions()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = mRecyclerView!!
        recyclerView.layoutManager = LinearLayoutManager(mContext)

        recyclerView.setItemViewCacheSize(20)

        val adapter = LocalFontListAdapter(mContext, mExecutor)
        mAdapter = adapter

        adapter.setFontClickListener { fontPath, realName, fileName, ttcIndex, weightWidthLabel ->
            mViewModel.recordFontAccess(fontPath)

            mFontSelectedListener?.onFontSelected(
                fontPath, realName, fileName,
                ttcIndex, weightWidthLabel
            )

            mMainHandler.postDelayed({ mAdapter?.saveLastOpenedAndUpdate(fontPath) }, 400)
        }

        adapter.setSortChangeListener { type, asc ->
            mSortManager.setSortOptions(type, asc)
        }

        adapter.setFavoriteStatusProvider { fontPath -> mFavoritePaths.contains(fontPath) }

        recyclerView.adapter = adapter

        adapter.updateSortOptionsOnly(
            mSortManager.currentSortType,
            mSortManager.isSortAscending
        )

        setupRecyclerViewAnimator()

        // الفواصل وزوايا القائمة تتولاها المكتبة عبر ItemDecoration،
        // حيث يُعامل شريط الفرز والـ Bottom space كـ Separator (أول خط، وآخر خط، والخط الوحيد، وعناصر المنتصف)
        recyclerView.addItemDecoration(ItemDecoration(mContext, LocalFontListAdapter.VIEW_TYPE_FONT))

        // fillBottom يملأ الفراغ تحت آخر عنصر وأثناء الأنيميشن بلون oui_round_and_bgcolor،
        // وزوايا آخر خط لا ترسمها المكتبة هنا لأن آخر عنصر عندها هو الـ Bottom space
        recyclerView.seslSetFillBottomEnabled(true)
        recyclerView.seslSetLastRoundedCorner(false)
        recyclerView.seslSetFastScrollerEnabled(false)
        recyclerView.seslSetIndexTipEnabled(false)
        recyclerView.seslSetGoToTopEnabled(true)
        recyclerView.seslSetSmoothScrollEnabled(true)
    }

    private fun setupRecyclerViewAnimator() {
        val recyclerView = mRecyclerView ?: return
        val animator = DurationItemAnimator()
        animator.supportsChangeAnimations = false
        animator.enableRemoveDelay(false)
        recyclerView.itemAnimator = animator
    }

    private fun initializeSelectionManager() {
        val drawerLayout = mDrawerLayout
        val adapter = mAdapter
        val recyclerView = mRecyclerView
        if (drawerLayout == null || adapter == null || recyclerView == null) return

        val selectionManager = LocalFontSelectionManager(
            requireActivity(),
            drawerLayout,
            adapter,
            recyclerView
        )
        mSelectionManager = selectionManager

        selectionManager.setFavoriteStatusChecker { path -> mFavoritePaths.contains(path) }

        adapter.setSelectionListener(object : LocalFontListAdapter.OnSelectionListener {
            override fun onStartSelection(position: Int) {
                selectionManager.isSelecting = true
                selectionManager.toggleSelection(position)
            }

            override fun onToggleSelection(position: Int) {
                selectionManager.toggleSelection(position)
            }
        })

        selectionManager.setActionListener(object : LocalFontSelectionManager.SelectionActionListener {
            override fun onRenameRequested(path: String) {
                handleRename(path)
            }

            override fun onDeleteRequested(paths: List<String>) {
                handleDelete(paths)
            }

            override fun onFavoriteRequested(paths: List<String>, addToFavorites: Boolean) {
                handleFavoriteAction(paths, addToFavorites)
            }
        })

        val backCallback = selectionManager.onBackPressedCallback
        if (backCallback != null) {
            requireActivity().onBackPressedDispatcher.addCallback(
                viewLifecycleOwner,
                backCallback
            )
        }
    }

    private fun handleRename(path: String?) {
        if (path == null) return

        RenameFontDialog.show(mContext, path) { oldPath, newFileName ->
            val success = mViewModel.renameFontInMemory(oldPath, newFileName)

            if (success) {
                mSelectionManager?.isSelecting = false

                mMainHandler.postDelayed({
                    val newPath = oldPath.substring(0, oldPath.lastIndexOf("/") + 1) + newFileName
                    val newPosition = mAdapter?.findPositionByPath(newPath) ?: -1
                    if (newPosition != -1) {
                        mRecyclerView?.scrollToPosition(newPosition)
                    }
                }, 300)

                Toast.makeText(mContext, R.string.success_renamed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(mContext, R.string.error_rename_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleDelete(paths: List<String>?) {
        if (paths == null || paths.isEmpty()) return

        val pathsToMove: List<String> = ArrayList(paths)

        val count = pathsToMove.size

        val message = resources.getQuantityString(
            R.plurals.dialog_move_to_trash_question, count, count
        )

        val confirmDialog = AlertDialog.Builder(mContext)
            .setMessage(message)
            .setPositiveButton(R.string.action_move_to_trash, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        confirmDialog.setOnShowListener {
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                confirmDialog.dismiss()
                showMoveToTrashProgressDialog(pathsToMove)
            }
        }

        confirmDialog.show()
    }

    private fun showMoveToTrashProgressDialog(pathsToMove: List<String>) {
        val count = pathsToMove.size

        BatchOperationState.setSourceDrawerListItem(DrawerListItem.LOCAL_FONTS)

        mSelectionManager?.isSelecting = false

        val dialog = ProgressDialog(mContext)
        mCurrentProgressDialog = dialog
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setCancelable(false)

        dialog.setTitle(
            resources.getQuantityString(
                R.plurals.progress_moving_to_trash, count
            )
        )
        dialog.setMax(count)

        dialog.setButton(
            ProgressDialog.BUTTON_NEGATIVE,
            getString(android.R.string.cancel)
        ) { d, _ ->
            mViewModel.cancelTrashOperation()
            BatchOperationState.requestCancel()
            d.dismiss()
        }

        dialog.setButton(
            ProgressDialog.BUTTON_POSITIVE,
            getString(R.string.action_hide_dialog)
        ) { d, _ ->
            d.dismiss()
        }

        OperationNotifications.showMoveToTrashNotification(mContext, count)

        dialog.show()

        val progressListener = TrashRepository.OnProgressListener { current, total ->
            val progressTitle = resources.getQuantityString(
                R.plurals.progress_moving_to_trash, total
            )
            BatchOperationState.updateProgress(
                current, total, progressTitle
            )

            OperationNotifications.updateMoveToTrashNotification(mContext, current, total)

            mMainHandler.post {
                val currentDialog = mCurrentProgressDialog
                if (currentDialog != null && currentDialog.isShowing) {
                    currentDialog.setProgress(current)
                }
            }
        }

        mViewModel.moveFontsToTrashInMemory(pathsToMove, progressListener) {
            val currentDialog = mCurrentProgressDialog
            if (currentDialog != null && currentDialog.isShowing) {
                currentDialog.dismiss()
            }
            mCurrentProgressDialog = null

            OperationNotifications.dismissMoveToTrashNotification(mContext)
        }
    }

    fun checkAndReopenProgressDialogPublic() {
        if (isHidden || !isAdded) return

        val isProcessing = BatchOperationState
            .getIsProcessing().value
        if (java.lang.Boolean.TRUE != isProcessing) return

        if (BatchOperationState.getSourceDrawerListItem() != DrawerListItem.LOCAL_FONTS) return

        if (!BatchOperationState.consumeShouldReopenDialog()) return

        val dialog = mCurrentProgressDialog
        if (dialog != null && dialog.isShowing) return

        Log.d(TAG, "checkAndReopenProgressDialog: reopening dialog for ongoing operation")
        reconnectToProgressDialog()
    }

    private fun reconnectToProgressDialog() {
        if (!isAdded) return

        val dialog = ProgressDialog(mContext)
        mCurrentProgressDialog = dialog
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setCancelable(false)

        val lastProgress = BatchOperationState.getProgress().value

        if (lastProgress != null) {
            val localTitle = resources.getQuantityString(
                R.plurals.progress_moving_to_trash, lastProgress.total
            )
            dialog.setTitle(localTitle)
            dialog.setMax(lastProgress.total)
            dialog.setProgress(lastProgress.current)
        } else {
            dialog.setTitle(
                resources.getQuantityString(
                    R.plurals.progress_moving_to_trash, 1
                )
            )
        }

        dialog.setButton(
            ProgressDialog.BUTTON_NEGATIVE,
            getString(android.R.string.cancel)
        ) { d, _ ->
            BatchOperationState.requestCancel()
            mViewModel.cancelTrashOperation()
            d.dismiss()
        }

        dialog.setButton(
            ProgressDialog.BUTTON_POSITIVE,
            getString(R.string.action_hide_dialog)
        ) { d, _ -> d.dismiss() }

        dialog.show()
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully")
    }

    private fun handleFavoriteAction(paths: List<String>?, addToFavorites: Boolean) {
        if (paths == null || paths.isEmpty()) return

        mSelectionManager?.isSelecting = false

        mMainHandler.postDelayed({
            mViewModel.toggleFavoritesBatch(paths, addToFavorites) {
            }
        }, 400)
    }

    fun handleBackPressed(): Boolean {
        val selectionManager = mSelectionManager
        if (selectionManager != null) return selectionManager.handleBackPress()
        return false
    }

    fun handleExitBackPress(): Boolean {
        val currentTime = System.currentTimeMillis()
        if (currentTime - mBackPressedTime < BACK_PRESS_EXIT_INTERVAL) {
            return true
        } else {
            mBackPressedTime = currentTime
            Toast.makeText(
                mContext,
                getString(R.string.exit_on_double_back),
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mSelectionManager?.refreshActionMode()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mUIManager.saveRecyclerViewState()
        val recyclerState = mUIManager.recyclerViewState
        if (recyclerState != null) {
            outState.putParcelable("recycler_state", recyclerState)
        }
        outState.putString("sort_type", mSortManager.currentSortType.name)
        outState.putBoolean("sort_asc", mSortManager.isSortAscending)
        outState.putBoolean("is_first_load", mIsFirstLoad)
    }

    override fun onStop() {
        super.onStop()
        mWasStopped = true
    }

    override fun onStart() {
        super.onStart()

        // عند العودة للتطبيق (من التطبيقات الأخيرة أو من شاشة أخرى) نفحص المجلد بصمت،
        // فيظهر أي ملف أضيف من خارج التطبيق دون شاشة تحميل ودون وميض
        if (mWasStopped) {
            mWasStopped = false
            if (!mIsFirstLoad
                && !mIsBatchOperationRunning
                && ::mViewModel.isInitialized
                && ::mLocalFontPermissionManager.isInitialized
                && mLocalFontPermissionManager.hasRequiredPermissions()
            ) {
                mViewModel.refreshFontsSilently()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            updateMainActivityFontsCount(mCurrentFontsList.size)

            checkAndReopenProgressDialogPublic()
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        setMenuVisibility(!hidden)
        if (!hidden && activity != null) {
            activity?.invalidateOptionsMenu()
        }

        if (hidden) {
            setDrawerLocked(false)

            // إعادة القائمة إلى الأعلى وهي مخفية، حتى تبدأ من الأعلى فعلياً
            // في المرة القادمة التي تُعرض فيها، دون أي قفزة مرئية
            mUIManager.scrollRecyclerViewToTop()

            mRecyclerView?.itemAnimator = null

            mSearchViewModel.deactivateSearch()
            val selectionManager = mSelectionManager
            if (selectionManager != null && selectionManager.isSelecting) {
                selectionManager.isSelecting = false
            }
        } else {
            val isLoading = mViewModel.isLoadingLiveData.value
            if (isLoading == true) {
                setDrawerLocked(true)
            }

            mAdapter?.smartUpdate()

            updateMainActivityFontsCount(mCurrentFontsList.size)

            mRecyclerView?.postDelayed({
                if (isAdded && !isHidden) {
                    setupRecyclerViewAnimator()
                }
            }, 100)

            checkAndReopenProgressDialogPublic()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (!mLocalFontDirectoryPicker.handleActivityResult(requestCode, resultCode, data)) {
            mLocalFontPermissionManager.handleActivityResult(requestCode)
        }
    }

    private fun notifyMainActivityReadyOnce() {
        if (mHasNotifiedReady) return
        mHasNotifiedReady = true
        val act = activity
        if (act is MainActivity) {
            act.setAppReady()
        }
    }

    private fun updateMainActivityFontsCount(count: Int) {
        val selectionManager = mSelectionManager
        if (selectionManager != null && selectionManager.isSelecting) return

        val act = activity
        if (act is MainActivity) {
            act.updateFontsCount(DrawerListItem.LOCAL_FONTS, count)
        }
    }

    fun openFolderPickerPublic() {
        if (mLocalFontPermissionManager.hasRequiredPermissions()) {
            mLocalFontDirectoryPicker.openDirectoryPicker()
        } else {
            mLocalFontPermissionManager.requestPermissions()
        }
    }

    private fun updateMainActivityFolderState(hasFolder: Boolean) {
        val act = activity
        if (act is MainActivity) {
            act.setLocalFolderSelected(hasFolder)
        }
    }

    private fun refreshAdapterData() {
        if (mCurrentFontsList.isEmpty()) {
            mSearchManager.updateFontsList(ArrayList())
            val adapter = mAdapter
            if (adapter != null) {
                adapter.updateFilteredFonts(ArrayList(), mSearchManager.currentSearchQuery)
                adapter.updateSortOptionsOnly(
                    mSortManager.currentSortType,
                    mSortManager.isSortAscending
                )
            }
            if (!mAwaitingInitialFontsLoad) {
                mUIManager.updateEmptyView(true, mSearchManager.isSearchActive)
            }
            return
        }

        val rawFonts: MutableList<FontFileInfo> = ArrayList()
        for (font in mCurrentFontsList) {
            rawFonts.add(
                FontFileInfo(
                    font.name,
                    font.path,
                    font.size,
                    font.lastModified,
                    font.realName
                )
            )
        }

        mSearchManager.updateFontsList(rawFonts)

        val adapter = mAdapter
        if (adapter != null) {
            adapter.updateFilteredFonts(
                mSearchManager.filteredFonts,
                mSearchManager.currentSearchQuery
            )
            adapter.updateSortOptionsOnly(
                mSortManager.currentSortType,
                mSortManager.isSortAscending
            )
        }

        mUIManager.updateEmptyView(false, mSearchManager.isSearchActive)

        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false
            mMainHandler.post {
                if (isAdded && view != null) {
                    mUIManager.restoreRecyclerViewState()
                }
            }
        }
    }

    fun onSearchStateChanged(isExpanded: Boolean) {
        if (::mSearchViewModel.isInitialized) {
            if (isExpanded) {
                mSearchViewModel.activateSearch()
            } else {
                mSearchViewModel.deactivateSearch()
            }
        }
    }

    fun filterFonts(query: String?) {
        mSearchViewModel.setSearchQuery(query)
    }

    fun resetFilter() {
        mSearchViewModel.deactivateSearch()
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        mUIManager.updateEmptyViewPosition(verticalOffset)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mLocalFontPermissionManager.handlePermissionResult(requestCode, grantResults)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mSelectionManager?.cleanup()
        mSelectionManager = null

        mAppBarLayout?.removeOnOffsetChangedListener(this)
        mAppBarLayout = null

        mPendingFontsUpdate = null
        mIsBatchOperationRunning = false
        mHasAppliedFonts = false

        val dialog = mCurrentProgressDialog
        if (dialog != null && dialog.isShowing) {
            try {
                dialog.dismiss()
            } catch (e: Exception) {
                Log.w(TAG, "onDestroyView: failed to dismiss progress dialog", e)
            }
        }
        mCurrentProgressDialog = null

        mMenu = null

        mDrawerLayout = null
        mRecyclerView = null
        mAdapter = null
    }

    override fun onDetach() {
        super.onDetach()
        mFontSelectedListener = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mMainHandler.isInitialized) mMainHandler.removeCallbacksAndMessages(null)
        if (::mExecutor.isInitialized) mExecutor.shutdown()
    }

    private fun setDrawerLocked(locked: Boolean) {
        val drawerLayout = mDrawerLayout ?: return

        val inner = findInnerDrawer(drawerLayout)
        if (inner != null) {
            // عند طلب فتح القفل (مثلاً بعد انتهاء التحميل) نتأكد أن البحث ووضع التحديد غير نشطين،
            // وإلا يبقى الدرج مقفلاً حتى لا يُفتح أثناء البحث أو التحديد
            val keepLocked = locked
                    || drawerLayout.isSearchMode
                    || drawerLayout.isActionMode
            inner.setDrawerLockMode(if (keepLocked) 1 else 0)
        }

        val toolbar = drawerLayout.toolbar
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ImageButton) {
                child.isEnabled = !locked
                child.isClickable = !locked
            }
        }
    }

    private fun findInnerDrawer(parent: ViewGroup): DrawerLayout? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is DrawerLayout) return child
            if (child is ViewGroup) {
                val result = findInnerDrawer(child)
                if (result != null) return result
            }
        }
        return null
    }

    companion object {
        private const val TAG = "LocalFontListFragment"

        private const val BACK_PRESS_EXIT_INTERVAL = 2000L
    }
}
