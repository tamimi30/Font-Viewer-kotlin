package com.oneui.fontviewer.ui.main.list

import android.content.Context
import android.os.Parcelable
import android.util.Log
import android.view.View
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.appbar.AppBarLayout

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.widget.layout.SortByItemLayout

class UiStateManager(private val context: Context) {

    private var selectFolderContainer: View? = null
    private var mainContentLayout: View? = null
    private var emptyView: View? = null
    private var emptyTextView: TextView? = null

    private var emptyTitleView: TextView? = null

    private var noResultsTextView: TextView? = null

    private var emptyIconView: View? = null

    private var recyclerView: RecyclerView? = null
    private var appBarLayout: AppBarLayout? = null

    private var loadingContainer: View? = null

    var recyclerViewState: Parcelable? = null

    private var defaultEmptyMessageResId: Int = R.string.local_fonts_empty_message

    fun setViews(
        selectFolderContainer: View?, mainContentLayout: View?,
        emptyView: View?, emptyTextView: TextView?, recyclerView: RecyclerView?
    ) {
        this.selectFolderContainer = selectFolderContainer
        this.mainContentLayout = mainContentLayout
        this.emptyView = emptyView
        this.emptyTextView = emptyTextView
        this.recyclerView = recyclerView
    }

    fun setViews(emptyView: View?, emptyTextView: TextView?, recyclerView: RecyclerView?) {
        this.emptyView = emptyView
        this.emptyTextView = emptyTextView
        this.recyclerView = recyclerView
    }

    fun setEmptyTitleView(emptyTitleView: TextView?) {
        this.emptyTitleView = emptyTitleView
    }

    fun setNoResultsTextView(noResultsTextView: TextView?) {
        this.noResultsTextView = noResultsTextView
    }

    fun setEmptyIconView(emptyIconView: View?) {
        this.emptyIconView = emptyIconView
    }

    fun setLoadingContainer(loadingContainer: View?) {
        this.loadingContainer = loadingContainer
    }

    fun setAppBarLayout(appBarLayout: AppBarLayout?) {
        this.appBarLayout = appBarLayout
    }

    fun setDefaultEmptyMessage(resId: Int) {
        this.defaultEmptyMessageResId = resId
    }

    fun updateUIVisibility(hasFolderUri: Boolean) {
        selectFolderContainer?.visibility = if (hasFolderUri) View.GONE else View.VISIBLE
        mainContentLayout?.visibility = if (hasFolderUri) View.VISIBLE else View.GONE
    }

    fun updateEmptyView(isEmpty: Boolean, isSearchActive: Boolean) {
        if (isEmpty) {
            showEmptyView(isSearchActive)
        } else {
            hideEmptyView()
        }
    }

    fun updateEmptyView(isEmpty: Boolean) {
        updateEmptyView(isEmpty, false)
    }

    private fun showEmptyView(isSearchActive: Boolean) {
        recyclerView?.visibility = View.GONE

        val emptyView = emptyView
        if (emptyView != null) {
            emptyView.visibility = View.VISIBLE

            val appBarLayout = appBarLayout
            if (appBarLayout != null) {
                updateEmptyViewPosition(Math.abs(appBarLayout.top))
            }
        }

        emptyIconView?.visibility = if (isSearchActive) View.VISIBLE else View.GONE

        val noResultsTextView = noResultsTextView
        if (noResultsTextView != null) {
            if (isSearchActive) {
                emptyTitleView?.visibility = View.GONE
                emptyTextView?.visibility = View.GONE
                noResultsTextView.visibility = View.VISIBLE
            } else {
                emptyTitleView?.visibility = View.VISIBLE
                val emptyTextView = emptyTextView
                if (emptyTextView != null) {
                    emptyTextView.visibility = View.VISIBLE
                    emptyTextView.text = context.getString(defaultEmptyMessageResId)
                }
                noResultsTextView.visibility = View.GONE
            }
        } else {
            emptyTitleView?.visibility = if (isSearchActive) View.GONE else View.VISIBLE
            val emptyTextView = emptyTextView
            if (emptyTextView != null) {
                if (isSearchActive) {
                    emptyTextView.text = context.getString(R.string.no_results_found)
                } else {
                    emptyTextView.text = context.getString(defaultEmptyMessageResId)
                }
            }
        }
    }

    private fun hideEmptyView() {
        recyclerView?.visibility = View.VISIBLE
        emptyView?.visibility = View.GONE
        noResultsTextView?.visibility = View.GONE
    }

    fun updateEmptyViewPosition(verticalOffset: Int) {
        val appBarLayout = appBarLayout ?: return

        val totalScrollRange = appBarLayout.totalScrollRange
        var translationY = 0f

        if (totalScrollRange != 0) {
            translationY = (Math.abs(verticalOffset) - totalScrollRange) / 2.0f
        }

        val emptyView = emptyView
        if (emptyView != null && emptyView.visibility == View.VISIBLE) {
            val innerEmptyView = emptyView.findViewById<View?>(R.id.empty_container)
            if (innerEmptyView != null) {
                innerEmptyView.translationY = translationY
            }
        }

        val selectFolderContainer = selectFolderContainer
        if (selectFolderContainer != null && selectFolderContainer.visibility == View.VISIBLE) {
            val innerFolderView = selectFolderContainer.findViewById<View?>(R.id.folder_container_inner)
            if (innerFolderView != null) {
                innerFolderView.translationY = translationY
            }
        }
    }

    fun saveRecyclerViewState() {
        val layoutManager = recyclerView?.layoutManager
        if (layoutManager != null) {
            recyclerViewState = layoutManager.onSaveInstanceState()
            Log.d(TAG, "RecyclerView state saved")
        }
    }

    fun restoreRecyclerViewState() {
        val state = recyclerViewState
        val layoutManager = recyclerView?.layoutManager
        if (state != null && layoutManager != null) {
            layoutManager.onRestoreInstanceState(state)
            recyclerViewState = null
            Log.d(TAG, "RecyclerView state restored")
        }
    }

    // تُستخدم عند إخفاء الشاشة، لإعادة القائمة إلى الأعلى وهي غير مرئية،
    // حتى تبدأ من الأعلى فعلياً في المرة القادمة التي تُعرض فيها، دون أي قفزة مرئية
    fun scrollRecyclerViewToTop() {
        recyclerView?.scrollToPosition(0)
        recyclerViewState = null
    }

    fun saveSortState(sortType: SortByItemLayout.SortType?, ascending: Boolean) {
        Log.d(TAG, "Sort state saved: type=$sortType, ascending=$ascending")
    }

    fun showLoadingState() {
        recyclerView?.visibility = View.GONE
        emptyView?.visibility = View.GONE
        loadingContainer?.visibility = View.VISIBLE
    }

    fun hideLoadingState() {
        loadingContainer?.visibility = View.GONE
    }

    companion object {
        private const val TAG = "UiStateManager"
    }
}
