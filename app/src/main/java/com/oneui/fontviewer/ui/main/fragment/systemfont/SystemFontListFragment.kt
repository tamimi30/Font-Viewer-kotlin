package com.oneui.fontviewer.ui.main.fragment.systemfont

import android.content.Context
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

import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.appbar.AppBarLayout

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.list.FontFileInfo
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.systemfont.SystemFontCache
import com.oneui.fontviewer.data.systemfont.SystemFontInfo
import com.oneui.fontviewer.ui.main.MainActivity
import com.oneui.fontviewer.ui.main.fragment.systemfont.recyclerview.SystemFontListAdapter
import com.oneui.fontviewer.ui.main.list.UiStateManager
import com.oneui.fontviewer.ui.main.list.recyclerview.ItemDecoration
import com.oneui.fontviewer.ui.main.list.search.SearchFilterManager
import com.oneui.fontviewer.ui.main.list.search.SearchViewModel
import com.oneui.fontviewer.ui.main.list.sort.SortManager
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.settings.SettingsViewModel
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

class SystemFontListFragment : Fragment(), AppBarLayout.OnOffsetChangedListener {

    private lateinit var mContext: Context
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: SystemFontListAdapter? = null
    private var mFontSelectedListener: OnFontSelectedListener? = null
    private lateinit var mMainHandler: Handler
    private lateinit var mExecutor: ExecutorService
    private var mAppBarLayout: AppBarLayout? = null
    private var mDrawerLayout: OneUiDrawerLayout? = null

    private lateinit var mSearchManager: SearchFilterManager
    private lateinit var mSortManager: SortManager
    private lateinit var mUIManager: UiStateManager

    private lateinit var mViewModel: SystemFontListViewModel
    private lateinit var mSearchViewModel: SearchViewModel
    private lateinit var mSettingsViewModel: SettingsViewModel
    private var mCurrentFontsList: List<FontEntity> = ArrayList()

    private var mIsFirstLoad = true

    private var mNeedsScrollRestore = false

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

        initializeManagers()
        setupManagerListeners()
    }

    private fun initializeManagers() {
        mSearchManager = SearchFilterManager()

        mSortManager = SortManager(mContext, true)

        mUIManager = UiStateManager(mContext)
    }

    private fun setupManagerListeners() {
        mSearchManager.setSearchResultListener { _, empty ->
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive)
        }

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

        if (savedInstanceState != null) {
            restoreInstanceState(savedInstanceState)
            mIsFirstLoad = savedInstanceState.getBoolean("is_first_load", true)
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
        mViewModel = ViewModelProvider(this).get(SystemFontListViewModel::class.java)
        mSearchViewModel = ViewModelProvider(this).get(SearchViewModel::class.java)
        mSettingsViewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        mViewModel.fontsLiveData.observe(this) { fonts ->
            if (fonts != null) {
                mCurrentFontsList = ArrayList(fonts)
                refreshAdapterData()
                updateMainActivityFontsCount(fonts.size)

                val pathsToPreload: MutableList<String> = ArrayList()
                for (font in fonts) {
                    pathsToPreload.add(font.path)
                }
                SystemFontCache.getInstance().preloadFonts(pathsToPreload)
            }
        }

        mViewModel.isLoadingLiveData.observe(this) { isLoading ->
            if (isLoading == true) {
                mUIManager.showLoadingState()
            } else {
                mUIManager.hideLoadingState()
            }
        }

        mViewModel.isApiAvailableLiveData.observe(this) { isAvailable ->
            if (isAvailable == false) {
                showUnsupportedView()
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
                Log.e(TAG, "Failed to restore sort state", e)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_system_font_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupAppBarLayout()

        if (mIsFirstLoad) {
            mViewModel.loadSystemFonts()
            mIsFirstLoad = false
        }
    }

    private fun initializeViews(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.system_font_recycler_view)
        mRecyclerView = recyclerView
        mUIManager.setViews(
            null,
            view.findViewById<View>(R.id.main_content_layout),
            view.findViewById<View>(R.id.empty_view),
            null,
            recyclerView
        )

        mUIManager.setNoResultsTextView(view.findViewById<TextView>(R.id.no_results_text))
        mUIManager.setEmptyIconView(view.findViewById<View>(R.id.empty_icon))
    }

    private fun setupRecyclerView() {
        val recyclerView = mRecyclerView!!
        recyclerView.layoutManager = LinearLayoutManager(mContext)

        val adapter = SystemFontListAdapter(mContext, mExecutor)
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

        recyclerView.adapter = adapter

        adapter.updateSortOptionsOnly(
            mSortManager.currentSortType,
            mSortManager.isSortAscending
        )

        setupRecyclerViewAnimator()

        // الفواصل وزوايا القائمة تتولاها المكتبة عبر ItemDecoration،
        // حيث يُعامل شريط الفرز والـ Bottom space كـ Separator (أول خط، وآخر خط، والخط الوحيد، وعناصر المنتصف)
        recyclerView.addItemDecoration(ItemDecoration(mContext, SystemFontListAdapter.VIEW_TYPE_FONT))

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

    private fun setupAppBarLayout() {
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

    private fun showUnsupportedView() {
        val root = view ?: return
        val unsupportedView = root.findViewById<View>(R.id.unsupported_view)
        val mainContent = root.findViewById<View>(R.id.main_content_layout)
        if (unsupportedView != null) unsupportedView.visibility = View.VISIBLE
        if (mainContent != null) mainContent.visibility = View.GONE
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
            return
        }

        val rawFonts: MutableList<FontFileInfo> = ArrayList()
        for (font in mCurrentFontsList) {
            rawFonts.add(
                FontFileInfo(
                    font.fileName,
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
            adapter.setAllFontsInfo(convertEntitiesToSystemFontInfo(mCurrentFontsList))

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

    private fun convertEntitiesToSystemFontInfo(entities: List<FontEntity>): List<SystemFontInfo> {
        val result: MutableList<SystemFontInfo> = ArrayList()
        for (entity in entities) {
            val info = SystemFontInfo(
                entity.fileName,
                entity.path,
                entity.size,
                entity.lastModified,
                400,
                0,
                entity.ttcIndex,
                null
            )
            val realName = entity.realName
            if (realName != null && !realName.isEmpty()) {
                info.realName = realName
            }
            info.weightWidthLabel = entity.weightWidthLabel
            result.add(info)
        }
        return result
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
        mSearchManager.filterFonts(query)
        mAdapter?.updateFilteredFonts(
            mSearchManager.filteredFonts,
            mSearchManager.currentSearchQuery
        )
    }

    fun resetFilter() {
        mSearchManager.resetFilter()
        mAdapter?.updateFilteredFonts(
            mSearchManager.filteredFonts,
            mSearchManager.currentSearchQuery
        )
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

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            updateMainActivityFontsCount(mCurrentFontsList.size)
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
        } else {
            mAdapter?.smartUpdate()

            updateMainActivityFontsCount(mCurrentFontsList.size)

            mRecyclerView?.postDelayed({
                if (isAdded && !isHidden) {
                    setupRecyclerViewAnimator()
                }
            }, 100)
        }
    }

    private fun updateMainActivityFontsCount(count: Int) {
        val act = activity
        if (act is MainActivity) {
            act.updateFontsCount(DrawerListItem.SYSTEM_FONTS, count)
        }
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        mUIManager.updateEmptyViewPosition(verticalOffset)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mAppBarLayout?.removeOnOffsetChangedListener(this)
        mAppBarLayout = null
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
        private const val TAG = "SystemFontListFragment"
    }
}
