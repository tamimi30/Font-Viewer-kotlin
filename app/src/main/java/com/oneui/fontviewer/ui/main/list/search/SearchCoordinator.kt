package com.oneui.fontviewer.ui.main.list.search

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment

import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout
import com.oneui.fontviewer.ui.widget.layout.OneUiToolBarLayout

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.main.fragment.favorite.FavoriteFontListFragment
import com.oneui.fontviewer.ui.main.fragment.localfont.LocalFontListFragment
import com.oneui.fontviewer.ui.main.fragment.systemfont.SystemFontListFragment


class SearchCoordinator(
    private val activity: Activity,
    private val drawerLayout: OneUiDrawerLayout
) {

    private var searchMenuItem: MenuItem? = null
    private var searchView: SearchView? = null
    private var drawerListItemProvider: DrawerListItemProvider? = null
    private var fragmentProvider: FragmentProvider? = null

    var isSearchExpanded: Boolean = false
        private set
    private var savedSearchQuery: String = ""
    private var lastDrawerListItem: DrawerListItem? = null

    private var stateListener: SearchStateListener? = null

    private var mPendingSearchRestore = false


    fun interface DrawerListItemProvider {
        fun getCurrentDrawerListItem(): DrawerListItem?
    }

    fun interface FragmentProvider {
        fun getFragment(drawerListItem: DrawerListItem?): Fragment?
    }

    interface SearchStateListener {
        fun onSearchExpanded()
        fun onSearchCollapsed()
        fun onSearchQueryChanged(query: String?)
    }


    fun setProviders(
        drawerListItemProvider: DrawerListItemProvider,
        fragmentProvider: FragmentProvider
    ) {
        this.drawerListItemProvider = drawerListItemProvider
        this.fragmentProvider = fragmentProvider
    }

    fun bindSearchMenuItem(searchMenuItem: MenuItem) {
        this.searchMenuItem = searchMenuItem

        searchMenuItem.setOnMenuItemClickListener {
            expandSearch()
            true
        }

        setupSearchView()

        if (mPendingSearchRestore) {
            mPendingSearchRestore = false
            drawerLayout.post {
                expandSearch()
                val view = searchView
                if (view != null && savedSearchQuery.isNotEmpty()) {
                    view.setQuery(savedSearchQuery, false)
                }
            }
        }
    }

    fun setSearchStateListener(listener: SearchStateListener?) {
        this.stateListener = listener
    }


    private fun setupSearchView() {
        val view = drawerLayout.searchView
        searchView = view

        view.queryHint = activity.getString(R.string.search_font)
        view.maxWidth = Integer.MAX_VALUE

        val searchManager = activity.getSystemService(Context.SEARCH_SERVICE) as SearchManager?
        if (searchManager != null) {
            view.setSearchableInfo(searchManager.getSearchableInfo(activity.componentName))
        }

        view.imeOptions = view.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

        drawerLayout.setSearchModeListener(object : OneUiToolBarLayout.SearchModeListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                performSearch(query)
                stateListener?.onSearchQueryChanged(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                performSearch(newText)
                stateListener?.onSearchQueryChanged(newText)
                return true
            }

            override fun onSearchModeToggle(searchView: SearchView, visible: Boolean) {
                if (visible) {
                    handleSearchExpand()
                } else {
                    handleSearchCollapse()
                }
            }
        })

        Log.d(TAG, "SearchView setup completed successfully")
    }


    private fun handleSearchExpand(): Boolean {
        isSearchExpanded = true

        stateListener?.onSearchExpanded()

        Log.d(TAG, "Search expanded")
        return true
    }

    private fun handleSearchCollapse(): Boolean {
        isSearchExpanded = false
        savedSearchQuery = ""


        val provider = fragmentProvider
        if (provider != null) {
            val localFrag = provider.getFragment(DrawerListItem.LOCAL_FONTS)
            if (localFrag is LocalFontListFragment) localFrag.resetFilter()

            val sysFrag = provider.getFragment(DrawerListItem.SYSTEM_FONTS)
            if (sysFrag is SystemFontListFragment) sysFrag.resetFilter()

            val favFrag = provider.getFragment(DrawerListItem.FAVORITES)
            if (favFrag is FavoriteFontListFragment) favFrag.resetFilter()
        }

        stateListener?.onSearchCollapsed()

        Log.d(TAG, "Search collapsed")
        return true
    }


    private fun performSearch(query: String?) {
        val currentFragment = getCurrentFragment()
        if (currentFragment is LocalFontListFragment) {
            currentFragment.filterFonts(query)
            Log.d(TAG, "Search performed on LocalFontListFragment with query: $query")
        } else if (currentFragment is SystemFontListFragment) {
            currentFragment.filterFonts(query)
            Log.d(TAG, "Search performed on SystemFontListFragment with query: $query")
        } else if (currentFragment is FavoriteFontListFragment) {
            currentFragment.filterFonts(query)
            Log.d(TAG, "Search performed on FavoriteFontListFragment with query: $query")
        }
    }

    private fun getCurrentFragment(): Fragment? {
        val fragments = fragmentProvider
        val items = drawerListItemProvider
        if (fragments == null || items == null) {
            return null
        }
        val currentDrawerListItem = items.getCurrentDrawerListItem()
        return fragments.getFragment(currentDrawerListItem)
    }


    fun handleSearchIntent(intent: Intent?): Boolean {
        if (intent == null || Intent.ACTION_SEARCH != intent.action) {
            return false
        }

        val currentDrawerListItem = drawerListItemProvider!!.getCurrentDrawerListItem()
        if (currentDrawerListItem != DrawerListItem.LOCAL_FONTS
            && currentDrawerListItem != DrawerListItem.SYSTEM_FONTS
            && currentDrawerListItem != DrawerListItem.FAVORITES
        ) {
            intent.removeExtra(SearchManager.QUERY)
            return false
        }

        if (searchMenuItem == null || !isSearchExpanded) {
            intent.removeExtra(SearchManager.QUERY)
            return false
        }

        val query = intent.getStringExtra(SearchManager.QUERY)
        val view = searchView
        if (query != null && view != null) {
            view.setQuery(query, false)
            performSearch(query)
            Log.d(TAG, "Search intent handled with query: $query")
            return true
        }

        return false
    }


    fun saveState(outState: Bundle) {
        val provider = drawerListItemProvider ?: return

        val currentDrawerListItem = provider.getCurrentDrawerListItem()
        val isSearchableDrawerListItem = (currentDrawerListItem == DrawerListItem.LOCAL_FONTS
                || currentDrawerListItem == DrawerListItem.SYSTEM_FONTS
                || currentDrawerListItem == DrawerListItem.FAVORITES)

        if (isSearchExpanded && isSearchableDrawerListItem) {
            outState.putBoolean(KEY_SEARCH_EXPANDED, true)
            val view = searchView
            if (view != null) {
                val currentQuery = view.query.toString()
                outState.putString(KEY_SEARCH_QUERY, currentQuery)
            } else {
                outState.putString(KEY_SEARCH_QUERY, "")
            }
        } else {
            outState.putBoolean(KEY_SEARCH_EXPANDED, false)
            outState.putString(KEY_SEARCH_QUERY, "")
        }

        Log.d(
            TAG, "Search state saved - expanded: " + isSearchExpanded
                    + ", query: " + savedSearchQuery
        )
    }

    fun restoreState(savedInstanceState: Bundle) {
        val provider = drawerListItemProvider ?: return

        isSearchExpanded = savedInstanceState.getBoolean(KEY_SEARCH_EXPANDED, false)
        savedSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "") ?: ""

        val currentDrawerListItem = provider.getCurrentDrawerListItem()
        val isSearchableDrawerListItem = (currentDrawerListItem == DrawerListItem.LOCAL_FONTS
                || currentDrawerListItem == DrawerListItem.SYSTEM_FONTS
                || currentDrawerListItem == DrawerListItem.FAVORITES)

        if (isSearchExpanded && isSearchableDrawerListItem) {
            if (searchMenuItem != null) {
                drawerLayout.post {
                    if (searchMenuItem != null) {
                        expandSearch()
                        val view = searchView
                        if (view != null && savedSearchQuery.isNotEmpty()) {
                            view.setQuery(savedSearchQuery, false)
                        }
                    }
                }
            } else {
                mPendingSearchRestore = true
            }
        }

        Log.d(
            TAG, "Search state restored - expanded: " + isSearchExpanded
                    + ", query: " + savedSearchQuery
        )
    }


    fun collapseSearch() {
        if (!isSearchExpanded) return
        drawerLayout.dismissSearchMode()
    }

    fun expandSearch() {
        if (!drawerLayout.isSearchMode) {
            drawerLayout.showSearchMode()
        }
    }

    fun clearSearchFocus() {
        val view = searchView ?: return

        view.clearFocus()

        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun onFragmentChanged(newDrawerListItem: DrawerListItem) {
        val last = lastDrawerListItem
        if (last != null && last != newDrawerListItem) {
            collapseSearch()
        }
        lastDrawerListItem = newDrawerListItem

        Log.d(TAG, "Fragment changed to: " + newDrawerListItem.name)
    }


    fun cleanup() {
        searchMenuItem = null
        searchView = null
        drawerListItemProvider = null
        fragmentProvider = null
        stateListener = null
        mPendingSearchRestore = false

        Log.d(TAG, "SearchCoordinator cleaned up")
    }

    companion object {
        private const val TAG = "SearchCoordinator"
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_SEARCH_EXPANDED = "search_expanded"
    }
}
