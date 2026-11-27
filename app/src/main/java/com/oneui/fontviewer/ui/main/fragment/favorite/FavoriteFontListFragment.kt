package com.oneui.fontviewer.ui.main.fragment.favorite

import android.content.Context
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
import android.widget.TextView
import android.widget.Toast

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
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
import com.oneui.fontviewer.ui.dialog.RenameFontDialog
import com.oneui.fontviewer.ui.main.MainActivity
import com.oneui.fontviewer.ui.main.fragment.localfont.LocalFontListViewModel
import com.oneui.fontviewer.ui.main.fragment.localfont.manager.LocalFontSelectionManager
import com.oneui.fontviewer.ui.main.fragment.localfont.recyclerview.LocalFontListAdapter
import com.oneui.fontviewer.ui.main.list.UiStateManager
import com.oneui.fontviewer.ui.main.list.recyclerview.ItemDecoration
import com.oneui.fontviewer.ui.main.list.search.SearchFilterManager
import com.oneui.fontviewer.ui.main.list.search.SearchViewModel
import com.oneui.fontviewer.ui.main.list.sort.SortManager
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.settings.SettingsViewModel
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

class FavoriteFontListFragment : Fragment(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var mContext: Context
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: LocalFontListAdapter? = null
    private var mFontSelectedListener: OnFontSelectedListener? = null
    private lateinit var mMainHandler: Handler
    private lateinit var mExecutor: ExecutorService
    private var mAppBarLayout: AppBarLayout? = null
    private var mDrawerLayout: OneUiDrawerLayout? = null

    private lateinit var mSearchManager: SearchFilterManager
    private lateinit var mSortManager: SortManager
    private lateinit var mUIManager: UiStateManager
    private var mSelectionManager: LocalFontSelectionManager? = null

    private lateinit var mViewModel: LocalFontListViewModel
    private lateinit var mSearchViewModel: SearchViewModel
    private lateinit var mSettingsViewModel: SettingsViewModel

    private var mCurrentFavoritesList: List<FontFileInfoWithMetadata> = ArrayList()

    // استعادة موضع التمرير تتم مرة واحدة فقط بعد إعادة إنشاء الـ Fragment، وليس مع كل تحديث للقائمة،
    // حتى لا تعود القائمة إلى موضع قديم عند إزالة عنصر من المفضلة
    private var mNeedsScrollRestore = false

    private var mIsBatchOperationRunning = false

    // هل عُرضت في هذه الواجهة قائمة مفضلة حقيقية واحدة على الأقل؟ نؤجّل تحديثات القائمة أثناء العملية الجارية
    // فقط بعد عرض أول قائمة، حتى لا تبقى الواجهة فارغة (رسالة "لا توجد خطوط في المفضلة بعد") عند إعادة إنشائها
    private var mHasAppliedFavorites = false

    private var mPendingFavoritesUpdate: List<FontFileInfoWithMetadata>? = null

    private var mCurrentProgressDialog: ProgressDialog? = null

    // الـ DefaultItemAnimator الافتراضي الذي ينشئه الـ RecyclerView بنفسه، نحتفظ به لإعادته بعد أن يُلغى عند إخفاء الـ Fragment
    private var mItemAnimator: DefaultItemAnimator? = null

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

        mSearchManager = SearchFilterManager()

        mSortManager = SortManager(mContext, "FAVORITES")

        mUIManager = UiStateManager(mContext)

        mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message)

        setupSearchListener()
        setupSortListener()
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
        mExecutor = Executors.newSingleThreadExecutor()

        initializeViewModels()
        setupViewModelObservers()

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
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
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_font_list_search, menu)

        val searchItem = menu.findItem(R.id.action_search_fonts)
        val act = activity
        if (act is MainActivity && searchItem != null) {
            act.searchCoordinator.bindSearchMenuItem(searchItem)
        }

        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    private fun initializeViewModels() {
        mViewModel = ViewModelProvider(this).get(LocalFontListViewModel::class.java)
        mSearchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        mSettingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)
    }

    private fun setupViewModelObservers() {
        mViewModel.favoritesLiveData.observe(this) { favorites ->
            if (favorites != null) {
                // عند فتح التطبيق من إشعار التقدم بعد حذفه من التطبيقات الأخيرة تُنشأ الواجهة من جديد والعملية ما زالت جارية،
                // فلا نؤجّل أول قائمة، وإلا تبقى الواجهة فارغة طوال العملية. نؤجّل ما يصل بعدها فقط
                if (mIsBatchOperationRunning && mHasAppliedFavorites) {
                    mPendingFavoritesUpdate = ArrayList(favorites)
                    return@observe
                }

                mHasAppliedFavorites = true
                mCurrentFavoritesList = ArrayList(favorites)

                mAdapter?.setAllFontsMetadata(favorites)

                refreshAdapterData()
                updateMainActivityFontsCount(favorites.size)

                val pathsToPreload: MutableList<String> = ArrayList()
                for (font in favorites) {
                    pathsToPreload.add(font.path)
                }
                LocalFontCache.getInstance().preloadFonts(pathsToPreload)
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

            val pending = mPendingFavoritesUpdate
            if (!isProcessing && pending != null) {
                mCurrentFavoritesList = ArrayList(pending)
                mAdapter?.setAllFontsMetadata(pending)
                refreshAdapterData()
                updateMainActivityFontsCount(mCurrentFavoritesList.size)
                mPendingFavoritesUpdate = null
            }
        }

        BatchOperationState.getProgress().observe(this) { progressData ->
            val dialog = mCurrentProgressDialog
            if (dialog != null && dialog.isShowing && progressData != null) {
                dialog.setMax(progressData.total)
                dialog.setProgress(progressData.current)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_favorite_font_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupDrawerLayout()
        initializeSelectionManager()
    }

    private fun initializeViews(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.font_recycler_view)
        mRecyclerView = recyclerView

        mUIManager.setViews(
            null,
            view.findViewById<View>(R.id.main_content_layout),
            view.findViewById<View>(R.id.empty_view),
            view.findViewById<TextView>(R.id.empty_text),
            recyclerView
        )

        mUIManager.setEmptyTitleView(view.findViewById<TextView>(R.id.empty_title))

        mUIManager.setNoResultsTextView(view.findViewById<TextView>(R.id.no_results_text))

        mUIManager.setEmptyIconView(view.findViewById<View>(R.id.empty_icon))

        mUIManager.updateUIVisibility(true)
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

    private fun setupRecyclerView() {
        val recyclerView = mRecyclerView!!
        recyclerView.layoutManager = LinearLayoutManager(mContext)

        val adapter = LocalFontListAdapter(mContext, mExecutor, true)
        mAdapter = adapter

        adapter.setFontClickListener { fontPath, realName, fileName, ttcIndex, weightWidthLabel ->

            mFontSelectedListener?.onFontSelected(
                fontPath, realName, fileName,
                ttcIndex, weightWidthLabel
            )

            mMainHandler.postDelayed({ mAdapter?.saveLastOpenedAndUpdate(fontPath) }, 400)
        }

        adapter.setSortChangeListener { type, asc ->
            mSortManager.setSortOptions(type, asc)
        }

        adapter.setFavoriteStatusProvider { true }

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
        val currentAnimator = recyclerView.itemAnimator
        if (currentAnimator is DefaultItemAnimator) {
            mItemAnimator = currentAnimator
        }
        val animator = mItemAnimator ?: return
        animator.supportsChangeAnimations = false
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

        selectionManager.setFavoriteStatusChecker { true }

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

        mSelectionManager?.isSelecting = false

        val dialog = ProgressDialog(mContext)
        mCurrentProgressDialog = dialog
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setCancelable(false)
        dialog.setTitle(
            resources.getQuantityString(
                R.plurals.progress_moving_to_trash, count, count
            )
        )
        dialog.setMax(count)

        BatchOperationState.setSourceDrawerListItem(DrawerListItem.FAVORITES)

        dialog.setButton(
            ProgressDialog.BUTTON_NEGATIVE,
            getString(android.R.string.cancel)
        ) { d, _ ->
            mViewModel.cancelTrashOperation()
            d.dismiss()
        }

        dialog.setButton(
            ProgressDialog.BUTTON_POSITIVE,
            getString(R.string.action_hide_dialog)
        ) { d, _ -> d.dismiss() }

        dialog.show()

        val progressListener = TrashRepository.OnProgressListener { current, _ ->
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
        }
    }

    fun checkAndReopenProgressDialogPublic() {
        if (isHidden || !isAdded) return

        val isProcessing = BatchOperationState.getIsProcessing().value
        if (java.lang.Boolean.TRUE != isProcessing) return

        if (BatchOperationState.getSourceDrawerListItem() != DrawerListItem.FAVORITES) return

        if (!BatchOperationState.consumeShouldReopenDialog()) return

        val dialog = mCurrentProgressDialog
        if (dialog != null && dialog.isShowing) return

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
    }

    private fun handleFavoriteAction(paths: List<String>?, addToFavorites: Boolean) {
        if (paths == null || paths.isEmpty()) return

        mSelectionManager?.isSelecting = false

        // الخروج من وضع التحديد يُشغّل في نفس اللحظة أنيميشن خاص به (إخفاء مربعات الاختيار، وإغلاق الـ ActionMode،
        // وإشعار تغيير لكل عناصر القائمة). لو حُذف العنصر من قاعدة البيانات مباشرة، يصل تحديث القائمة
        // أثناء هذه التغييرات فلا يظهر أنيميشن الحذف. لذلك ننتظر انتهاء الخروج من وضع التحديد أولاً،
        // كما يفعل LocalFontListFragment في نفس الحالة.
        mMainHandler.postDelayed({
            if (!isAdded) return@postDelayed

            mViewModel.toggleFavoritesBatch(paths, addToFavorites) {
                val message = if (addToFavorites)
                    getString(R.string.action_favorite)
                else
                    getString(R.string.action_unfavorite)
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
            }
        }, SELECTION_EXIT_DELAY_MS)
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

    fun handleBackPressed(): Boolean {
        val selectionManager = mSelectionManager
        if (selectionManager != null) return selectionManager.handleBackPress()
        return false
    }

    private fun refreshAdapterData() {
        if (mCurrentFavoritesList.isEmpty()) {
            mSearchManager.updateFontsList(ArrayList())
            val adapter = mAdapter
            if (adapter != null) {
                adapter.updateFilteredFonts(ArrayList(), mSearchManager.currentSearchQuery)
                adapter.updateSortOptionsOnly(
                    mSortManager.currentSortType,
                    mSortManager.isSortAscending
                )
            }
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive)
            return
        }

        val rawFonts: MutableList<FontFileInfo> = ArrayList()
        for (font in mCurrentFavoritesList) {
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

        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false
            mMainHandler.post {
                if (isAdded && view != null) {
                    mUIManager.restoreRecyclerViewState()
                }
            }
        }
    }

    private fun updateMainActivityFontsCount(count: Int) {
        val selectionManager = mSelectionManager
        if (selectionManager != null && selectionManager.isSelecting) return

        val act = activity
        if (act is MainActivity) {
            act.updateFontsCount(DrawerListItem.FAVORITES, count)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            updateMainActivityFontsCount(mCurrentFavoritesList.size)
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
            mAdapter?.smartUpdate()

            updateMainActivityFontsCount(mCurrentFavoritesList.size)

            checkAndReopenProgressDialogPublic()

            mRecyclerView?.postDelayed({
                if (isAdded && !isHidden) {
                    setupRecyclerViewAnimator()
                }
            }, 100)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mSelectionManager?.refreshActionMode()
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        mUIManager.updateEmptyViewPosition(verticalOffset)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mSelectionManager?.cleanup()
        mSelectionManager = null

        mAppBarLayout?.removeOnOffsetChangedListener(this)
        mAppBarLayout = null

        mPendingFavoritesUpdate = null
        mIsBatchOperationRunning = false
        mHasAppliedFavorites = false
        mCurrentProgressDialog = null

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

    companion object {
        private const val TAG = "FavoriteFontListFragment"

        // المدة التي ننتظرها بعد الخروج من وضع التحديد قبل تنفيذ إزالة المفضلة (نفس المدة المستخدمة في LocalFontListFragment)
        private const val SELECTION_EXIT_DELAY_MS = 400L
    }
}
