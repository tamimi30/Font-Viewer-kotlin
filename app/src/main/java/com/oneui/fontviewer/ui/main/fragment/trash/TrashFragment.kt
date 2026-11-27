package com.oneui.fontviewer.ui.main.fragment.trash

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

import dev.oneuiproject.oneui.dialog.ProgressDialog

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.room.FontEntity
import com.oneui.fontviewer.data.trash.model.OperationType
import com.oneui.fontviewer.service.BatchOperationState
import com.oneui.fontviewer.service.OperationNotifications
import com.oneui.fontviewer.ui.dialog.ActionDialogs
import com.oneui.fontviewer.ui.main.MainActivity
import com.oneui.fontviewer.ui.main.fragment.trash.manager.TrashSelectionManager
import com.oneui.fontviewer.ui.main.fragment.trash.recyclerview.TrashListAdapter
import com.oneui.fontviewer.ui.main.list.UiStateManager
import com.oneui.fontviewer.ui.main.list.recyclerview.ItemDecoration
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

class TrashFragment : Fragment(), AppBarLayout.OnOffsetChangedListener {

    private var mContext: Context? = null
    private lateinit var mMainHandler: Handler

    private var mRecyclerView: RecyclerView? = null
    private var mMainContentLayout: View? = null
    private var mEmptyView: View? = null

    private var mDrawerLayout: OneUiDrawerLayout? = null
    private var mAppBarLayout: AppBarLayout? = null

    private var mAdapter: TrashListAdapter? = null
    private var mSelectionManager: TrashSelectionManager? = null
    private lateinit var mViewModel: TrashViewModel

    private lateinit var mUIManager: UiStateManager

    private var mCurrentProgressDialog: ProgressDialog? = null

    private var mIsDialogHidden = false

    private var mIsBatchOperationRunning = false

    // هل عُرضت في هذه الواجهة قائمة سلة محذوفات واحدة على الأقل؟ نؤجّل تحديثات القائمة أثناء العملية الجارية
    // فقط بعد عرض أول قائمة، حتى لا تبقى الواجهة بلا قائمة ولا رسالة عند إعادة إنشائها
    private var mHasAppliedTrash = false

    private var mPendingTrashUpdate: List<FontEntity>? = null

    private var mPendingTrashCount: Int? = null

    private var mTrashCount = 0

    // الـ DefaultItemAnimator الافتراضي الذي ينشئه الـ RecyclerView بنفسه، نحتفظ به لإعادته بعد أن يُلغى عند إخفاء الـ Fragment
    private var mItemAnimator: DefaultItemAnimator? = null

    private val ctx: Context
        get() = mContext!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context

        mUIManager = UiStateManager(context)
        mUIManager.setDefaultEmptyMessage(R.string.trash_empty_description)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        mMainHandler = Handler(Looper.getMainLooper())
        mViewModel = ViewModelProvider(this).get(TrashViewModel::class.java)
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_trash_more, menu)

        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val emptyTrashItem = menu.findItem(R.id.action_empty_trash)
        if (emptyTrashItem != null) {
            val shouldShow = (mTrashCount > 0) && !mIsBatchOperationRunning

            emptyTrashItem.isVisible = shouldShow
            emptyTrashItem.isEnabled = shouldShow
        }
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_empty_trash) {
            handleEmptyTrash()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_trash, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupDrawerLayout()
        setupRecyclerView()
        initSelectionManager()
        setupViewModelObservers()
    }

    private fun initViews(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.trash_recycler_view)
        mRecyclerView = recyclerView
        mMainContentLayout = view.findViewById(R.id.main_content_layout)
        val emptyView = view.findViewById<View>(R.id.empty_view)
        mEmptyView = emptyView

        val emptyTitleView = view.findViewById<TextView>(R.id.empty_title)
        val emptyTextView = view.findViewById<TextView>(R.id.empty_text)

        mUIManager.setViews(emptyView, emptyTextView, recyclerView)
        mUIManager.setEmptyTitleView(emptyTitleView)
    }

    private fun setupDrawerLayout() {
        val act = activity ?: return
        val drawerView = act.findViewById<View>(R.id.drawer_layout)
        if (drawerView is OneUiDrawerLayout) {
            mDrawerLayout = drawerView
            val appBar = drawerView.appBarLayout
            mAppBarLayout = appBar
            appBar.addOnOffsetChangedListener(this)

            mUIManager.setAppBarLayout(appBar)
        }
    }

    private fun setupRecyclerView() {
        val adapter = TrashListAdapter(ctx)
        mAdapter = adapter
        val recyclerView = mRecyclerView!!

        recyclerView.layoutManager = LinearLayoutManager(ctx)
        recyclerView.adapter = adapter

        // الفواصل وزوايا القائمة تتولاها المكتبة عبر ItemDecoration،
        // حيث يُعامل رأس القائمة والـ Bottom space كـ Separator (أول خط، وآخر خط، والخط الوحيد، وعناصر المنتصف)
        recyclerView.addItemDecoration(ItemDecoration(ctx, TrashListAdapter.VIEW_TYPE_ITEM))

        // fillBottom يملأ الفراغ تحت آخر عنصر وأثناء الأنيميشن بلون oui_round_and_bgcolor،
        // وزوايا آخر خط لا ترسمها المكتبة هنا لأن آخر عنصر عندها هو الـ Bottom space
        recyclerView.seslSetFillBottomEnabled(true)
        recyclerView.seslSetLastRoundedCorner(false)
        recyclerView.seslSetFastScrollerEnabled(false)
        recyclerView.seslSetGoToTopEnabled(true)
        recyclerView.seslSetSmoothScrollEnabled(true)

        setupRecyclerViewAnimator()
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

    private fun initSelectionManager() {
        val drawerLayout = mDrawerLayout
        val adapter = mAdapter
        val recyclerView = mRecyclerView
        if (drawerLayout == null || adapter == null || recyclerView == null) return

        val selectionManager = TrashSelectionManager(
            requireActivity(),
            drawerLayout,
            adapter,
            recyclerView
        )
        mSelectionManager = selectionManager

        adapter.setSelectionListener(object : TrashListAdapter.OnSelectionListener {
            override fun onStartSelection(adapterPosition: Int) {
                selectionManager.isSelecting = true
                selectionManager.toggleSelection(adapterPosition)
            }

            override fun onToggleSelection(adapterPosition: Int) {
                selectionManager.toggleSelection(adapterPosition)
            }
        })

        selectionManager.setActionListener(object : TrashSelectionManager.SelectionActionListener {

            override fun onRestoreRequested(fonts: List<FontEntity>) {
                handleRestoreAction(fonts)
            }

            override fun onDeletePermanentlyRequested(fonts: List<FontEntity>) {
                handleDeletePermanentlyAction(fonts)
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

    private fun setupViewModelObservers() {

        mViewModel.trashedFontsLiveData.observe(viewLifecycleOwner) { fonts ->
            if (fonts == null) return@observe

            // عند فتح التطبيق من إشعار التقدم بعد حذفه من التطبيقات الأخيرة تُنشأ الواجهة من جديد والعملية ما زالت جارية،
            // فلا نؤجّل أول قائمة، وإلا لا يظهر شيء طوال العملية. نؤجّل ما يصل بعدها فقط
            if (mIsBatchOperationRunning && mHasAppliedTrash) {
                mPendingTrashUpdate = ArrayList(fonts)
                return@observe
            }

            mHasAppliedTrash = true
            val isEmpty = fonts.isEmpty()
            updateEmptyState(isEmpty)
            mAdapter?.submitList(if (isEmpty) null else fonts)

            Log.d(TAG, "Trash list updated: " + fonts.size + " items")
        }

        mViewModel.trashedFontsCountLiveData.observe(viewLifecycleOwner) { count ->
            if (count != null) {
                mTrashCount = count

                activity?.invalidateOptionsMenu()

                if (mIsBatchOperationRunning) {
                    mPendingTrashCount = count
                    return@observe
                }
                updateSubtitle(count)
            }
        }

        mViewModel.operationProgressLiveData.observe(viewLifecycleOwner) { progress ->
            if (progress == null) return@observe

            val dialog = mCurrentProgressDialog
            if (dialog != null && dialog.isShowing) {
                dialog.setProgress(progress.current)

            } else if (mIsDialogHidden) {
                updateNotificationForOperation(
                    progress.operationType, progress.current, progress.total
                )
            }
        }

        mViewModel.operationResultLiveData.observe(viewLifecycleOwner) { result ->
            if (result == null) return@observe

            val dialog = mCurrentProgressDialog
            if (dialog != null && dialog.isShowing) {
                dialog.dismiss()
            }
            mCurrentProgressDialog = null

            dismissNotificationForOperation(result.operationType)

            mIsDialogHidden = false

            mViewModel.clearOperationResult()

            Log.d(
                TAG, "Operation complete: " + result.operationType
                        + " | succeeded=" + result.succeeded
                        + " | failed=" + result.failed
                        + " | cancelled=" + result.wasCancelled
            )
        }

        BatchOperationState.getIsProcessing().observe(viewLifecycleOwner) { isProcessing ->
            mIsBatchOperationRunning = isProcessing

            activity?.invalidateOptionsMenu()

            val dialog = mCurrentProgressDialog
            if (!isProcessing && dialog != null && dialog.isShowing) {
                dialog.dismiss()
                mCurrentProgressDialog = null
            }

            val pendingUpdate = mPendingTrashUpdate
            if (!isProcessing && pendingUpdate != null) {
                val isEmpty = pendingUpdate.isEmpty()
                updateEmptyState(isEmpty)
                mAdapter?.submitList(if (isEmpty) null else pendingUpdate)
                mPendingTrashUpdate = null
            }

            val pendingCount = mPendingTrashCount
            if (!isProcessing && pendingCount != null) {
                updateSubtitle(pendingCount)
                mPendingTrashCount = null
            }
        }

        BatchOperationState.getProgress().observe(viewLifecycleOwner) { progressData ->
            val dialog = mCurrentProgressDialog
            if (dialog != null && dialog.isShowing && progressData != null) {
                dialog.setMax(progressData.total)
                dialog.setProgress(progressData.current)
            }
        }
    }

    fun handleEmptyTrash() {
        val allFonts = mAdapter?.allFonts ?: return
        if (allFonts.isEmpty()) return

        ActionDialogs.showDeletePermanentlyDialog(
            ctx,
            allFonts.size
        ) { showDeleteProgressAndExecute(allFonts, true) }
    }

    private fun handleRestoreAction(fonts: List<FontEntity>) {
        if (fonts.isEmpty()) return

        mSelectionManager?.isSelecting = false

        showRestoreProgressAndExecute(fonts)
    }

    private fun handleDeletePermanentlyAction(fonts: List<FontEntity>) {
        if (fonts.isEmpty()) return

        ActionDialogs.showDeletePermanentlyDialog(
            ctx,
            fonts.size
        ) {
            mSelectionManager?.isSelecting = false
            showDeleteProgressAndExecute(fonts, false)
        }
    }

    private fun showRestoreProgressAndExecute(fonts: List<FontEntity>) {
        mIsDialogHidden = false

        val dialog = ActionDialogs.createRestoreProgressDialog(
            ctx,
            fonts.size,

            ActionDialogs.OnProgressCancelListener {
                mViewModel.cancelCurrentOperation()
                mIsDialogHidden = false
                Log.d(TAG, "Restore operation cancelled by user")
            },

            ActionDialogs.OnProgressHideListener {
                mIsDialogHidden = true
                OperationNotifications.showRestoreNotification(ctx, fonts.size)
                Log.d(TAG, "Restore dialog hidden, notification continues")
            }
        )
        mCurrentProgressDialog = dialog

        OperationNotifications.showRestoreNotification(ctx, fonts.size)

        dialog.show()

        mViewModel.restoreFonts(fonts)
    }

    private fun showDeleteProgressAndExecute(
        fonts: List<FontEntity>,
        isEmptyAll: Boolean
    ) {
        mIsDialogHidden = false

        val dialog = ActionDialogs.createDeleteProgressDialog(
            ctx,
            fonts.size,

            ActionDialogs.OnProgressCancelListener {
                mViewModel.cancelCurrentOperation()
                mIsDialogHidden = false
                Log.d(TAG, "Delete operation cancelled by user")
            },

            ActionDialogs.OnProgressHideListener {
                mIsDialogHidden = true
                OperationNotifications.showDeleteNotification(ctx, fonts.size)
                Log.d(TAG, "Delete dialog hidden, notification continues")
            }
        )
        mCurrentProgressDialog = dialog

        OperationNotifications.showDeleteNotification(ctx, fonts.size)

        dialog.show()

        if (isEmptyAll) {
            mViewModel.emptyTrash()
        } else {
            mViewModel.deletePermanently(fonts)
        }
    }

    private fun updateNotificationForOperation(type: OperationType, current: Int, total: Int) {
        when (type) {
            OperationType.RESTORE ->
                OperationNotifications.updateRestoreNotification(ctx, current, total)
            OperationType.DELETE_PERMANENTLY,
            OperationType.EMPTY_TRASH ->
                OperationNotifications.updateDeleteNotification(ctx, current, total)
            OperationType.MOVE_TO_TRASH ->
                OperationNotifications.updateMoveToTrashNotification(ctx, current, total)
        }
    }

    private fun dismissNotificationForOperation(type: OperationType) {
        when (type) {
            OperationType.RESTORE ->
                OperationNotifications.dismissRestoreNotification(ctx)
            OperationType.DELETE_PERMANENTLY,
            OperationType.EMPTY_TRASH ->
                OperationNotifications.dismissDeleteNotification(ctx)
            OperationType.MOVE_TO_TRASH ->
                OperationNotifications.dismissMoveToTrashNotification(ctx)
        }
    }

    fun checkAndReopenProgressDialogPublic() {
        if (isHidden || !isAdded || mContext == null || !::mViewModel.isInitialized) return

        val isProcessing = BatchOperationState.getIsProcessing().value
        if (java.lang.Boolean.TRUE != isProcessing) return

        if (BatchOperationState.getSourceDrawerListItem() != DrawerListItem.TRASH) return

        if (!BatchOperationState.consumeShouldReopenDialog()) return

        val dialog = mCurrentProgressDialog
        if (dialog != null && dialog.isShowing) return

        reconnectToProgressDialog()
    }

    private fun reconnectToProgressDialog() {
        if (!isAdded || mContext == null) return

        mIsDialogHidden = false

        val lastProgress = BatchOperationState.getProgress().value ?: return

        val dialog = ProgressDialog(ctx)
        mCurrentProgressDialog = dialog
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        dialog.setCancelable(false)

        val localTitle: String = if (lastProgress.operationCode == 2) {
            resources.getQuantityString(R.plurals.progress_restoring, lastProgress.total)
        } else if (lastProgress.operationCode == 3) {
            resources.getQuantityString(R.plurals.progress_deleting, lastProgress.total)
        } else {
            resources.getQuantityString(R.plurals.progress_moving_to_trash, lastProgress.total)
        }

        dialog.setTitle(localTitle)
        dialog.setMax(lastProgress.total)
        dialog.setProgress(lastProgress.current)

        dialog.setButton(
            ProgressDialog.BUTTON_NEGATIVE,
            getString(android.R.string.cancel)
        ) { d, _ ->
            BatchOperationState.requestCancel()
            mViewModel.cancelCurrentOperation()
            d.dismiss()
        }

        dialog.setButton(
            ProgressDialog.BUTTON_POSITIVE,
            getString(R.string.action_hide_dialog)
        ) { d, _ ->
            mIsDialogHidden = true
            d.dismiss()
        }

        dialog.show()
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully from global state")
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        mMainContentLayout?.visibility = if (isEmpty) View.GONE else View.VISIBLE

        mUIManager.updateEmptyView(isEmpty)
    }

    private fun updateSubtitle(count: Int) {
        val selectionManager = mSelectionManager
        if (selectionManager != null && selectionManager.isSelecting) return

        val act = activity
        if (act is MainActivity) {
            act.updateFontsCount(DrawerListItem.TRASH, count)
        }
    }

    override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        mUIManager.updateEmptyViewPosition(verticalOffset)
    }

    fun handleBackPressed(): Boolean {
        val selectionManager = mSelectionManager
        if (selectionManager != null) return selectionManager.handleBackPress()
        return false
    }

    override fun onResume() {
        super.onResume()

        checkAndReopenProgressDialogPublic()

        if (!isHidden) {
            val count = mViewModel.trashedFontsCountLiveData.value
            if (count != null) updateSubtitle(count)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        setMenuVisibility(!hidden)
        if (!hidden && activity != null) {
            activity?.invalidateOptionsMenu()
        }

        if (!hidden) {
            checkAndReopenProgressDialogPublic()
            val count = mViewModel.trashedFontsCountLiveData.value
            if (count != null) updateSubtitle(count)
        } else {
            // إعادة القائمة إلى الأعلى وهي مخفية، حتى تبدأ من الأعلى فعلياً
            // في المرة القادمة التي تُعرض فيها، دون أي قفزة مرئية
            mUIManager.scrollRecyclerViewToTop()

            val selectionManager = mSelectionManager
            if (selectionManager != null && selectionManager.isSelecting) {
                selectionManager.isSelecting = false
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mSelectionManager?.refreshActionMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        mSelectionManager?.cleanup()
        mSelectionManager = null

        mAppBarLayout?.removeOnOffsetChangedListener(this)
        mAppBarLayout = null

        val dialog = mCurrentProgressDialog
        if (dialog != null && dialog.isShowing) {
            dialog.dismiss()
        }
        mCurrentProgressDialog = null

        mPendingTrashUpdate = null
        mPendingTrashCount = null
        mIsBatchOperationRunning = false
        mHasAppliedTrash = false

        mDrawerLayout = null
        mRecyclerView = null
        mAdapter = null
    }

    override fun onDetach() {
        super.onDetach()
        mContext = null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mMainHandler.isInitialized) mMainHandler.removeCallbacksAndMessages(null)
    }

    companion object {
        private const val TAG = "TrashFragment"
    }
}
