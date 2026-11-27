package com.oneui.fontviewer.ui.main.navigation

import android.view.View
import android.view.ViewGroup

import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

import com.oneui.fontviewer.ui.main.fragment.localfont.LocalFontListFragment
import com.oneui.fontviewer.ui.main.fragment.favorite.FavoriteFontListFragment
import com.oneui.fontviewer.ui.main.fragment.trash.TrashFragment
import com.oneui.fontviewer.ui.main.list.search.SearchCoordinator
import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListAdapter
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem

class NavManager(private val mHost: Host) {

    interface Host {
        val appFragmentManager: FragmentManager
        fun getFragment(drawerListItem: DrawerListItem?): Fragment?
        var currentDrawerListItem: DrawerListItem?
        val drawerLayout: OneUiDrawerLayout
        val drawerAdapter: DrawerListAdapter
        val searchCoordinator: SearchCoordinator?
        fun updateDrawerTitle(drawerListItem: DrawerListItem)
        fun performExit()
    }

    private var mInnerDrawer: androidx.drawerlayout.widget.DrawerLayout? = null

    fun setup(drawerLayout: OneUiDrawerLayout?) {
        if (drawerLayout != null) {
            mInnerDrawer = findInnerDrawerLayout(drawerLayout)
        }
    }

    val isDrawerCurrentlyOpen: Boolean
        get() = mInnerDrawer != null && mInnerDrawer!!.isDrawerOpen(GravityCompat.START)

    private fun findInnerDrawerLayout(parent: ViewGroup): androidx.drawerlayout.widget.DrawerLayout? {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is androidx.drawerlayout.widget.DrawerLayout) {
                return child
            }
            if (child is ViewGroup) {
                val result = findInnerDrawerLayout(child)
                if (result != null) return result
            }
        }
        return null
    }

    fun navigateFromDrawer(drawerListItem: DrawerListItem) {
        mHost.currentDrawerListItem = drawerListItem
        showFragmentFast(drawerListItem)
        mHost.drawerAdapter.setSelectedItem(drawerListItem)
        mHost.updateDrawerTitle(drawerListItem)
    }

    fun showFragmentFast(drawerListItem: DrawerListItem) {
        val fm = mHost.appFragmentManager
        val transaction = fm.beginTransaction()

        for (s in DrawerListItem.values()) {
            val frag = mHost.getFragment(s)
            if (frag != null && frag.isAdded) {
                if (s == drawerListItem) {
                    transaction.show(frag)
                    frag.setMenuVisibility(true)
                } else {
                    transaction.hide(frag)
                    frag.setMenuVisibility(false)
                }
            }
        }

        transaction.commitNow()

        // الشاشة المعروضة يجب أن تكون واجهتها مرئية دائماً. التسخين الصامت في MainActivity يجعل واجهة الفرجمنت
        // INVISIBLE وهو ظاهر، وإن جاء التنقل إليه بعد ذلك فإن show() وحدها لا تعيد الظهور لفرجمنت ظاهر أصلاً
        val shownFragment = mHost.getFragment(drawerListItem)
        if (shownFragment != null && shownFragment.isAdded) {
            val shownView = shownFragment.view
            if (shownView != null && shownView.visibility != View.VISIBLE) {
                shownView.visibility = View.VISIBLE
            }
        }

        mHost.currentDrawerListItem = drawerListItem
        mHost.searchCoordinator?.onFragmentChanged(drawerListItem)
    }

    fun navigateFromDrawerAnimated(drawerListItem: DrawerListItem) {
        mHost.currentDrawerListItem = drawerListItem
        showFragmentAnimated(drawerListItem)
        mHost.drawerAdapter.setSelectedItem(drawerListItem)
        mHost.updateDrawerTitle(drawerListItem)
    }

    fun showFragmentAnimated(drawerListItem: DrawerListItem) {
        val fm = mHost.appFragmentManager
        val transaction = fm.beginTransaction()
        transaction.setCustomAnimations(R.anim.note_style_fragment_enter, R.anim.note_style_fragment_exit)

        for (s in DrawerListItem.values()) {
            val frag = mHost.getFragment(s)
            if (frag != null && frag.isAdded && s != drawerListItem && !frag.isHidden) {
                transaction.hide(frag)
                frag.setMenuVisibility(false)
            }
        }

        val newFrag = mHost.getFragment(drawerListItem)
        if (newFrag != null && newFrag.isAdded) {
            transaction.show(newFrag)
            newFrag.setMenuVisibility(true)
        }

        transaction.commitNow()

        mHost.currentDrawerListItem = drawerListItem
        mHost.searchCoordinator?.onFragmentChanged(drawerListItem)
    }

    fun handleBackPressed() {
        if (isDrawerCurrentlyOpen) {
            mHost.drawerLayout.setDrawerOpen(false, true)
            return
        }

        val currentDrawerListItem = mHost.currentDrawerListItem
        val currentFragment = mHost.getFragment(currentDrawerListItem)
        if (currentFragment is LocalFontListFragment) {
            if (currentFragment.handleBackPressed()) return
        } else if (currentFragment is FavoriteFontListFragment) {
            if (currentFragment.handleBackPressed()) return
        } else if (currentFragment is TrashFragment) {
            if (currentFragment.handleBackPressed()) return
        }

        val searchCoordinator = mHost.searchCoordinator
        if (searchCoordinator != null && searchCoordinator.isSearchExpanded) {
            searchCoordinator.collapseSearch()
            return
        }

        if (mHost.currentDrawerListItem != DrawerListItem.LOCAL_FONTS) {
            navigateFromDrawerAnimated(DrawerListItem.LOCAL_FONTS)
            return
        }

        val localFrag = mHost.getFragment(DrawerListItem.LOCAL_FONTS)
        if (localFrag is LocalFontListFragment) {
            if (localFrag.handleExitBackPress()) {
                mHost.performExit()
            }
        } else {
            mHost.performExit()
        }
    }
}
