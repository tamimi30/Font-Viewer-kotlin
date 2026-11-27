package com.oneui.fontviewer.ui.main.fragment.trash.manager

import android.os.Build
import android.view.View
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.ui.main.fragment.trash.recyclerview.TrashListAdapter
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

class TrashSelectionManager(
    private val activity: FragmentActivity,
    private val drawerLayout: OneUiDrawerLayout,
    private val adapter: TrashListAdapter,
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


    interface SelectionActionListener {
        fun onRestoreRequested(fonts: List<FontEntity>)

        fun onDeletePermanentlyRequested(fonts: List<FontEntity>)
    }

    init {
        setupRecyclerViewListener()
        setupBackHandling()

        // إذا اختفى عنصر محدد من القائمة نحدّث العدّاد وأزرار القائمة
        adapter.setOnSelectionPrunedListener { refreshActionMode() }
    }

    fun setActionListener(listener: SelectionActionListener?) {
        this.actionListener = listener
    }


    private fun setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
            object : RecyclerView.SeslLongPressMultiSelectionListener {
                override fun onItemSelected(
                    view: RecyclerView, child: View,
                    position: Int, id: Long
                ) {
                    if (adapter.getItemViewType(position) == TrashListAdapter.VIEW_TYPE_ITEM) {
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
        drawerLayout.setActionModeMenu(R.menu.menu_trash_actions)
        drawerLayout.showActionMode()

        drawerLayout.setActionModeMenuListener { item ->
            val id = item.itemId
            if (id == R.id.action_restore) {
                handleRestoreAction()
                true
            } else if (id == R.id.action_delete) {
                handleDeleteAction()
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

        // حالة التحديد محفوظة في الـ Adapter بمعرّف العنصر
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

            val deleteItemBottom = bottomMenu.findItem(R.id.action_delete)
            val deleteItemToolbar = toolbarMenu.findItem(R.id.action_delete)
            val restoreItemBottom = bottomMenu.findItem(R.id.action_restore)
            val restoreItemToolbar = toolbarMenu.findItem(R.id.action_restore)

            val isAllSelected = (selectedCount == totalCount)

            val deleteText = if (isAllSelected)
                activity.getString(R.string.action_delete_all)
            else
                activity.getString(R.string.action_delete)

            val restoreText = if (isAllSelected)
                activity.getString(R.string.action_restore_all)
            else
                activity.getString(R.string.action_restore)

            deleteItemBottom?.title = deleteText
            deleteItemToolbar?.title = deleteText
            restoreItemBottom?.title = restoreText
            restoreItemToolbar?.title = restoreText
        }

        checkAllListening = true
    }

    fun refreshActionMode() {
        if (isSelecting) {
            recyclerView.post { updateActionModeUI() }
        }
    }


    private fun handleRestoreAction() {
        val listener = actionListener
        if (adapter.selectedCount == 0 || listener == null) return
        listener.onRestoreRequested(adapter.selectedFonts)
    }

    private fun handleDeleteAction() {
        val listener = actionListener
        if (adapter.selectedCount == 0 || listener == null) return
        listener.onDeletePermanentlyRequested(adapter.selectedFonts)
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
    }
}
