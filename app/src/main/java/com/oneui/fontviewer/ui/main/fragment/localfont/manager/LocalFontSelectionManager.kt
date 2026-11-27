package com.oneui.fontviewer.ui.main.fragment.localfont.manager

import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.main.fragment.localfont.recyclerview.LocalFontListAdapter
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

class LocalFontSelectionManager(
    private val activity: FragmentActivity,
    private val drawerLayout: OneUiDrawerLayout,
    private val adapter: LocalFontListAdapter,
    private val recyclerView: RecyclerView
) {

    var isSelecting: Boolean = false
        set(enabled) {
            if (field == enabled) return
            field = enabled
            if (enabled) activateSelectionMode()
            else deactivateSelectionMode()
        }
    private var checkAllListening = true

    private var actionListener: SelectionActionListener? = null

    var onBackPressedCallback: OnBackPressedCallback? = null
        private set
    private var onBackInvokedCallback: OnBackInvokedCallback? = null

    private var favoriteStatusChecker: FavoriteStatusChecker? = null

    fun interface FavoriteStatusChecker {
        fun isFavorited(path: String): Boolean
    }

    // الإجراءات تُمرَّر بمسارات الملفات وليس بمواضعها، لأن المواضع قد تتغير أثناء التحديد أو أثناء ظهور الحوارات
    interface SelectionActionListener {
        fun onRenameRequested(path: String)
        fun onDeleteRequested(paths: List<String>)

        fun onFavoriteRequested(paths: List<String>, addToFavorites: Boolean)
    }

    init {
        setupRecyclerViewListener()
        setupBackHandling()

        // إذا اختفى عنصر محدد من القائمة (تحديث من قاعدة البيانات مثلاً) نحدّث العدّاد وأزرار القائمة
        adapter.setOnSelectionPrunedListener { refreshActionMode() }
    }

    fun setActionListener(listener: SelectionActionListener?) {
        this.actionListener = listener
    }

    fun setFavoriteStatusChecker(checker: FavoriteStatusChecker?) {
        this.favoriteStatusChecker = checker
    }

    private fun setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
            object : RecyclerView.SeslLongPressMultiSelectionListener {
                override fun onItemSelected(view: RecyclerView, child: View, position: Int, id: Long) {
                    if (adapter.getItemViewType(position) == LocalFontListAdapter.VIEW_TYPE_FONT) {
                        toggleSelection(position)
                    }
                }

                override fun onLongPressMultiSelectionStarted(x: Int, y: Int) {}

                override fun onLongPressMultiSelectionEnded(x: Int, y: Int) {}
            }
        )
    }

    private fun setupBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = OnBackInvokedCallback {
                if (isSelecting) isSelecting = false
            }
        }

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (isSelecting) isSelecting = false
            }
        }
    }

    private fun activateSelectionMode() {
        adapter.isSelectionMode = true

        drawerLayout.actionModeBottomMenu.clear()
        drawerLayout.setActionModeMenu(R.menu.menu_font_actions)
        drawerLayout.showActionMode()

        drawerLayout.setActionModeMenuListener { item ->
            val id = item.itemId
            if (id == R.id.action_delete) {
                handleDeleteAction()
                true
            } else if (id == R.id.action_rename) {
                handleRenameAction()
                true
            } else if (id == R.id.action_favorite) {
                handleFavoriteAction()
                true
            } else {
                false
            }
        }

        drawerLayout.setActionModeCheckboxListener { _, isChecked ->
            if (checkAllListening) toggleSelectAll(isChecked)
            updateActionModeUI()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                onBackInvokedCallback!!
            )
        }
        onBackPressedCallback!!.isEnabled = true
    }

    private fun deactivateSelectionMode() {
        adapter.clearSelection()
        adapter.isSelectionMode = false


        drawerLayout.dismissActionMode()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(
                onBackInvokedCallback!!
            )
        }
        onBackPressedCallback!!.isEnabled = false
    }

    fun toggleSelection(position: Int) {
        if (!isSelecting) isSelecting = true

        // حالة التحديد محفوظة في الـ Adapter بمسار الملف
        adapter.setItemSelected(position, !adapter.isItemSelected(position))
        updateActionModeUI()
    }

    private fun toggleSelectAll(selectAll: Boolean) {
        val itemCount = adapter.itemCount
        for (i in 1 until itemCount - 1) {
            adapter.setItemSelected(i, selectAll)
        }
    }

    private fun updateActionModeUI() {
        checkAllListening = false

        val selectedCount = adapter.selectedCount

        val totalCount = adapter.itemCount - 2

        drawerLayout.setActionModeAllSelector(selectedCount, true, selectedCount == totalCount)

        if (selectedCount > 0) {
            val bottomMenu = drawerLayout.actionModeBottomMenu
            val toolbarMenu = drawerLayout.actionModeToolbarMenu

            val renameItemBottom = bottomMenu.findItem(R.id.action_rename)
            val renameItemToolbar = toolbarMenu.findItem(R.id.action_rename)
            val deleteItemBottom = bottomMenu.findItem(R.id.action_delete)
            val deleteItemToolbar = toolbarMenu.findItem(R.id.action_delete)
            val favoriteItemBottom = bottomMenu.findItem(R.id.action_favorite)
            val favoriteItemToolbar = toolbarMenu.findItem(R.id.action_favorite)

            val isSingleSelection = (selectedCount == 1)

            val isPortrait = activity.resources.configuration.orientation ==
                    Configuration.ORIENTATION_PORTRAIT

            renameItemBottom?.isVisible = isSingleSelection
            renameItemToolbar?.isVisible = !isPortrait && isSingleSelection

            val deleteText = if (selectedCount == totalCount)
                activity.getString(R.string.action_delete_all)
            else
                activity.getString(R.string.action_delete)

            deleteItemBottom?.title = deleteText
            deleteItemToolbar?.title = deleteText

            val allFavorited = resolveFavoriteAction()

            val favoriteText = if (allFavorited)
                activity.getString(R.string.action_unfavorite)
            else
                activity.getString(R.string.action_favorite)
            val favoriteIcon = if (allFavorited)
                dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off
            else
                dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on

            if (favoriteItemBottom != null) {
                favoriteItemBottom.title = favoriteText
                favoriteItemBottom.setIcon(favoriteIcon)
            }
            if (favoriteItemToolbar != null) {
                favoriteItemToolbar.title = favoriteText
            }
        }

        checkAllListening = true
    }

    private fun resolveFavoriteAction(): Boolean {
        val checker = favoriteStatusChecker
        if (checker == null || adapter.selectedCount == 0) return false
        for (path in adapter.selectedPaths) {
            if (!checker.isFavorited(path)) {
                return false
            }
        }
        return true
    }

    fun refreshActionMode() {
        if (isSelecting) {
            recyclerView.post { updateActionModeUI() }
        }
    }

    private fun handleRenameAction() {
        val listener = actionListener
        if (adapter.selectedCount != 1 || listener == null) return
        val paths = adapter.selectedPaths
        if (paths.size != 1) return
        listener.onRenameRequested(paths[0])
    }

    private fun handleDeleteAction() {
        val listener = actionListener
        if (adapter.selectedCount == 0 || listener == null) return
        listener.onDeleteRequested(adapter.selectedPaths)
    }

    private fun handleFavoriteAction() {
        val listener = actionListener
        if (adapter.selectedCount == 0 || listener == null) return

        val allFavorited = resolveFavoriteAction()
        val addToFavorites = !allFavorited

        listener.onFavoriteRequested(adapter.selectedPaths, addToFavorites)
    }

    fun handleBackPress(): Boolean {
        if (isSelecting) {
            isSelecting = false
            return true
        }
        return false
    }

    fun cleanup() {
        if (isSelecting) isSelecting = false
        adapter.setOnSelectionPrunedListener(null)
        onBackPressedCallback = null
        onBackInvokedCallback = null
        actionListener = null
        favoriteStatusChecker = null
    }
}
