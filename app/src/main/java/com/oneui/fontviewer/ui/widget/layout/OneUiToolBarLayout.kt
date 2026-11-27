package com.oneui.fontviewer.ui.widget.layout

import android.app.SearchManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.ActionMenuItemView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView

import dev.oneuiproject.oneui.design.R
import com.oneui.fontviewer.ui.widget.drawable.NavigationBadgeDrawable
import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils

/**
 * Custom collapsing Appbar like in any App from Samsung. Includes a [SearchView] and Samsung's ActionMode.
 */
open class OneUiToolBarLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    private var mAMTMenuShowAlwaysMax = 2
    private var switchActionModeMenu = false
    private var mSelectedItemsCount = 0

    interface ActionModeCallback {
        fun onShow(toolbarLayout: OneUiToolBarLayout)
        fun onDismiss(toolbarLayout: OneUiToolBarLayout)
    }

    private var mActionModeCallback: ActionModeCallback? = null

    protected lateinit var mActivity: AppCompatActivity
    protected var mContext: Context = context

    private val mOnBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (mIsActionMode) {
                    dismissActionMode()
                } else if (mIsSearchMode) {
                    dismissSearchMode()
                }
            }
        }
    private val mActionModeTitleFadeListener = AppBarOffsetListener()

    protected var mLayout: Int = 0
    protected var mExpandable: Boolean = false
    protected var mExpanded: Boolean = false
    protected var mNavigationIcon: Drawable? = null
    private var mNavigationBadgeIcon: LayerDrawable? = null
    protected var mTitleCollapsed: CharSequence? = null
    protected var mTitleExpanded: CharSequence? = null
    protected var mSubtitleCollapsed: CharSequence? = null
    protected var mSubtitleExpanded: CharSequence? = null

    private lateinit var mAppBarLayout: AppBarLayout
    private lateinit var mCollapsingToolbarLayout: CollapsingToolbarLayout
    private lateinit var mMainToolbar: Toolbar
    private lateinit var mSearchToolbar: Toolbar
    private lateinit var mActionModeToolbar: Toolbar
    private lateinit var mCoordinatorLayout: CoordinatorLayout
    protected lateinit var mMainContainer: FrameLayout
    private lateinit var mFooterContainer: FrameLayout
    private lateinit var mBottomActionModeBar: BottomNavigationView

    private lateinit var mSearchView: SearchView
    private lateinit var mActionModeSelectAll: LinearLayout
    private lateinit var mActionModeCheckBox: AppCompatCheckBox
    private lateinit var mActionModeTitleTextView: TextView

    private var mSearchModeListener: SearchModeListener? = null

    private var mIsSearchMode = false
    private var mIsActionMode = false

    /**
     * Callback for the Toolbar's SearchMode.
     * Notification that the [SearchView]'s text has been edited or it's visibility changed.
     *
     * @see showSearchMode
     * @see dismissSearchMode
     */
    interface SearchModeListener {
        fun onQueryTextSubmit(query: String?): Boolean

        fun onQueryTextChange(newText: String?): Boolean

        fun onSearchModeToggle(searchView: SearchView, visible: Boolean)
    }

    init {
        findActivity()?.let { mActivity = it }

        orientation = LinearLayout.VERTICAL

        initLayoutAttrs(attrs)
        inflateChildren()
        initAppBar()

        if (!isInEditMode) {
            mActivity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            mActivity.onBackPressedDispatcher.addCallback(mOnBackPressedCallback)
        }

        refreshLayout(resources.configuration)
    }

    protected open fun initLayoutAttrs(attrs: AttributeSet?) {
        val a = mContext.theme
            .obtainStyledAttributes(
                attrs, R.styleable.ToolbarLayout, 0, 0
            )
        try {
            mLayout = a.getResourceId(
                R.styleable.ToolbarLayout_android_layout,
                R.layout.oui_layout_toolbarlayout_appbar
            )
            mExpandable = a.getBoolean(R.styleable.ToolbarLayout_expandable, true)
            mExpanded = a.getBoolean(R.styleable.ToolbarLayout_expanded, mExpandable)
            mNavigationIcon = a.getDrawable(R.styleable.ToolbarLayout_navigationIcon)
            mTitleCollapsed = a.getString(R.styleable.ToolbarLayout_title)
            mTitleExpanded = mTitleCollapsed
            mSubtitleExpanded = a.getString(R.styleable.ToolbarLayout_subtitle)
        } finally {
            a.recycle()
        }
    }

    protected open fun inflateChildren() {
        if (mLayout != R.layout.oui_layout_toolbarlayout_appbar) {
            Log.w(TAG, "Inflating custom $TAG")
        }

        val inflater = LayoutInflater.from(mContext)
        inflater.inflate(mLayout, this, true)
        addView(
            inflater.inflate(
                R.layout.oui_layout_toolbarlayout_footer, this, false
            )
        )
    }

    private fun initAppBar() {
        mCoordinatorLayout = findViewById(R.id.toolbarlayout_coordinator_layout)
        mAppBarLayout = mCoordinatorLayout.findViewById(R.id.toolbarlayout_app_bar)
        mCollapsingToolbarLayout = mAppBarLayout.findViewById(R.id.toolbarlayout_collapsing_toolbar)
        mMainToolbar = mCollapsingToolbarLayout.findViewById(R.id.toolbarlayout_main_toolbar)
        mSearchToolbar = mCollapsingToolbarLayout.findViewById(R.id.toolbarlayout_search_toolbar)
        mActionModeToolbar = mCollapsingToolbarLayout.findViewById(R.id.toolbarlayout_action_mode_toolbar)

        mSearchView = mSearchToolbar.findViewById(R.id.toolbarlayout_search_view)
        mActionModeSelectAll = mActionModeToolbar.findViewById(R.id.toolbarlayout_selectall)
        mActionModeCheckBox = mActionModeSelectAll.findViewById(R.id.toolbarlayout_selectall_checkbox)
        mActionModeTitleTextView = mActionModeToolbar.findViewById(R.id.toolbar_layout_action_mode_title)

        mActionModeSelectAll.setOnClickListener {
            mActionModeCheckBox.isChecked = !mActionModeCheckBox.isChecked
        }

        mMainContainer = findViewById(R.id.toolbarlayout_main_container)
        mFooterContainer = findViewById(R.id.toolbarlayout_footer_container)
        mBottomActionModeBar = findViewById(R.id.toolbarlayout_bottom_nav_view)

        if (!isInEditMode) {
            mActivity.setSupportActionBar(mMainToolbar)
            mActivity.supportActionBar!!
                .setDisplayHomeAsUpEnabled(false)
            mActivity.supportActionBar!!
                .setDisplayShowTitleEnabled(false)

            mSearchView.setSearchableInfo(
                (mContext.getSystemService(Context.SEARCH_SERVICE) as SearchManager)
                    .getSearchableInfo(mActivity.componentName)
            )
        }

        mSearchView.seslSetUpButtonVisibility(View.VISIBLE)
        mSearchView.seslSetOnUpButtonClickListener { dismissSearchMode() }

        setNavigationButtonIcon(mNavigationIcon)
        setTitle(mTitleExpanded, mTitleCollapsed)
        setExpandedSubtitle(mSubtitleExpanded)

    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (!::mMainContainer.isInitialized || !::mFooterContainer.isInitialized) {
            super.addView(child, index, params)
        } else {
            when ((params as ToolbarLayoutParams).layout_location) {
                APPBAR_HEADER ->
                    setCustomTitleView(
                        child,
                        CollapsingToolbarLayout.LayoutParams(params)
                    )

                FOOTER ->
                    mFooterContainer.addView(child, params)

                ROOT ->
                    mCoordinatorLayout
                        .addView(child, CLLPWrapper(params))

                else ->
                    mMainContainer.addView(child, params)
            }
        }
    }

    public override fun generateDefaultLayoutParams(): LinearLayout.LayoutParams {
        return ToolbarLayoutParams(getContext(), null)
    }

    override fun generateLayoutParams(attrs: AttributeSet): LinearLayout.LayoutParams {
        return ToolbarLayoutParams(getContext(), attrs)
    }

    public override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetAppBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshLayout(newConfig)
        resetAppBar()
        updateActionModeMenuVisibility(newConfig)
    }

    private fun findActivity(): AppCompatActivity? {
        var context: Context? = getContext()
        while (context is ContextWrapper) {
            if (context is AppCompatActivity) {
                return context
            }
            context = context.baseContext
        }
        return null
    }

    private fun refreshLayout(newConfig: Configuration) {
        if (!isInEditMode)
            ToolbarLayoutUtils
                .hideStatusBarForLandscape(mActivity, newConfig.orientation)

        ToolbarLayoutUtils.updateListBothSideMargin(
            mActivity,
            mMainContainer
        )
        ToolbarLayoutUtils.updateListBothSideMargin(
            mActivity,
            findViewById<View>(R.id.toolbarlayout_bottom_corners)
        )
        ToolbarLayoutUtils.updateListBothSideMargin(
            mActivity,
            findViewById<View>(R.id.toolbarlayout_footer_content)
        )

        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        isExpanded = !isLandscape && mExpanded

        val badgeLayer = mNavigationBadgeIcon
        if (badgeLayer != null) {
            val badgeIcon = badgeLayer.getDrawable(1) as NavigationBadgeDrawable
            badgeIcon.setOrientation(isLandscape)
        }
    }

    private fun resetAppBar() {
        if (::mAppBarLayout.isInitialized) {
            if (mExpandable) {
                mAppBarLayout.isEnabled = true
                mAppBarLayout.seslSetCustomHeightProportion(false, 0f)
            } else {
                mAppBarLayout.isEnabled = false
                mAppBarLayout.seslSetCustomHeight(
                    mContext.resources
                        .getDimensionPixelSize(R.dimen.sesl_action_bar_height_with_padding)
                )
            }
        } else
            Log.w(TAG, "resetAppBar: mAppBarLayout is null.")
    }

    //
    // AppBar methods
    //

    /**
     * Returns the [AppBarLayout].
     */
    val appBarLayout: AppBarLayout
        get() = mAppBarLayout

    /**
     * Returns the [Toolbar].
     */
    val toolbar: Toolbar
        get() = mMainToolbar

    /**
     * Set the title of both the collapsed and expanded Toolbar.
     * The expanded title might not be visible in landscape or on devices with small dpi.
     */
    fun setTitle(title: CharSequence?) {
        setTitle(title, title)
    }

    /**
     * Set the title of the collapsed and expanded Toolbar independently.
     * The expanded title might not be visible in landscape or on devices with small dpi.
     */
    fun setTitle(
        expandedTitle: CharSequence?,
        collapsedTitle: CharSequence?
    ) {
        mTitleCollapsed = collapsedTitle
        mMainToolbar.title = collapsedTitle
        mTitleExpanded = expandedTitle
        mCollapsingToolbarLayout.title = expandedTitle
    }

    /**
     * Set the subtitle of the [CollapsingToolbarLayout].
     * This might not be visible in landscape or on devices with small dpi.
     */
    fun setExpandedSubtitle(expandedSubtitle: CharSequence?) {
        mSubtitleExpanded = expandedSubtitle
        mCollapsingToolbarLayout.seslSetSubtitle(expandedSubtitle)
    }

    /**
     * Set the subtitle of the collapsed Toolbar.
     */
    fun setCollapsedSubtitle(collapsedSubtitle: CharSequence?) {
        mSubtitleCollapsed = collapsedSubtitle
        mMainToolbar.subtitle = collapsedSubtitle
    }

    /**
     * Enable or disable the expanding Toolbar functionality.
     * If you simply want to programmatically expand or collapse the toolbar.
     *
     * @see isExpanded
     */
    var isExpandable: Boolean
        get() = mExpandable
        set(expandable) {
            if (mExpandable != expandable) {
                mExpandable = expandable
                resetAppBar()
            }
        }

    /**
     * Programmatically expand or collapse the Toolbar. The getter returns the current state of the toolbar.
     */
    var isExpanded: Boolean
        get() = mExpandable && !mAppBarLayout.seslIsCollapsed()
        set(expanded) {
            setExpanded(expanded, ViewCompat.isLaidOut(mAppBarLayout))
        }

    /**
     * Programmatically expand or collapse the Toolbar with an optional animation.
     *
     * @param animate whether or not to animate the expanding or collapsing.
     */
    fun setExpanded(expanded: Boolean, animate: Boolean) {
        if (mExpandable) {
            mExpanded = expanded
            mAppBarLayout.setExpanded(expanded, animate)
        } else
            Log.d(TAG, "setExpanded: mExpandable is $mExpandable")
    }

    /**
     * Replace the title of the expanded Toolbar with a custom View.
     * This might not be visible in landscape or on devices with small dpi.
     */
    fun setCustomTitleView(view: View) {
        setCustomTitleView(
            view,
            CollapsingToolbarLayout.LayoutParams(view.layoutParams)
        )
    }

    /**
     * Replace the title of the expanded Toolbar with a custom View including LayoutParams.
     * This might not be visible in landscape or on devices with small dpi.
     */
    fun setCustomTitleView(
        view: View,
        params: CollapsingToolbarLayout.LayoutParams?
    ) {
        val finalParams = params ?: CollapsingToolbarLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        finalParams.seslSetIsTitleCustom(true)
        mCollapsingToolbarLayout.seslSetCustomTitleView(view, finalParams)
    }

    /**
     * Replace the subtitle of the expanded Toolbar with a custom View.
     * This might not be visible in landscape or on devices with small dpi.
     */
    fun setCustomSubtitle(view: View) {
        mCollapsingToolbarLayout.seslSetCustomSubtitle(view)
    }

    /**
     * Enable or disable the immersive scroll of the Toolbar.
     * When this is enabled the Toolbar will completely hide when scrolling up.
     * The getter returns true if the immersive scroll is enabled.
     */
    var isImmersiveScroll: Boolean
        get() = mAppBarLayout.seslGetImmersiveScroll()
        set(activate) {
            if (Build.VERSION.SDK_INT >= 30) {
                mAppBarLayout.seslSetImmersiveScroll(activate)
            } else {
                Log.e(TAG, "setImmersiveScroll: immersive scroll is available only on api 30 and above")
            }
        }


    /**
     * Set the badge of a Toolbar MenuItem. Only use this for MenuItems which show as action! It won't work for overflow items.
     */
    fun setMenuItemBadgeText(@IdRes id: Int, text: String?) {
        for (i in 0 until mMainToolbar.childCount) {
            val v1 = mMainToolbar.getChildAt(i)
            if (v1 is ActionMenuView) {
                val menuView = v1
                for (j in 0 until menuView.childCount) {
                    val v2 = menuView.getChildAt(j)

                    if (v2 is ActionMenuItemView) {
                        val menuItemView = v2
                        if (menuItemView.itemData.itemId == id) {

                            menuView.removeView(menuItemView)
                            val fl = FrameLayout(mContext)
                            fl.addView(menuItemView)

                            val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                            val mBadgeBackground = inflater.inflate(androidx.appcompat.R.layout.sesl_action_menu_item_badge, fl, false) as ViewGroup
                            val mBadgeText = mBadgeBackground.getChildAt(0) as TextView
                            fl.addView(mBadgeBackground)

                            setMenuItemBadgeText(mBadgeBackground, mBadgeText, text)

                            menuView.addView(fl, j)
                            return
                        }
                    } else if (v2 is FrameLayout) {
                        val fl = v2
                        val v3 = fl.getChildAt(0)
                        if (v3 is ActionMenuItemView && v3.itemData.itemId == id) {
                            val mBadgeBackground = fl.getChildAt(1) as ViewGroup
                            val mBadgeText = mBadgeBackground.getChildAt(0) as TextView
                            setMenuItemBadgeText(mBadgeBackground, mBadgeText, text)
                            return
                        }
                    }
                }

                Log.e(TAG, "no MenuItem with id $id")
                return
            }
        }

        Log.e(TAG, "no ActionMenuView in Toolbar")
    }

    private fun setMenuItemBadgeText(mBadgeBackground: ViewGroup, mBadgeText: TextView, text: String?) {
        mBadgeText.text = text
        mBadgeBackground.visibility = if (text == null || text.isEmpty()) View.GONE else View.VISIBLE
        if (text == null) return
        val lp = mBadgeBackground.layoutParams as ViewGroup.MarginLayoutParams
        lp.marginEnd = 0
        lp.width = (resources.getDimension(androidx.appcompat.R.dimen.sesl_badge_default_width) + (text.length * resources.getDimension(androidx.appcompat.R.dimen.sesl_badge_additional_width))).toInt()
        mBadgeBackground.layoutParams = lp
    }


    //
    // Navigation Button methods
    //

    /**
     * Set the navigation icon of the Toolbar.
     * Don't forget to also set a Tooltip with [setNavigationButtonTooltip].
     */
    fun setNavigationButtonIcon(icon: Drawable?) {
        mNavigationIcon = icon
        val badgeLayer = mNavigationBadgeIcon
        if (badgeLayer != null) {
            badgeLayer.setDrawable(0, mNavigationIcon)
            badgeLayer.invalidateSelf()
            mMainToolbar.navigationIcon = badgeLayer
        } else {
            mMainToolbar.navigationIcon = mNavigationIcon
        }
    }

    /**
     * Change the visibility of the navigation button.
     */
    fun setNavigationButtonVisible(visible: Boolean) {
        val badgeLayer = mNavigationBadgeIcon
        val icon = mNavigationIcon
        if (badgeLayer != null) {
            mMainToolbar.navigationIcon = if (visible) badgeLayer else null
        } else if (icon != null) {
            mMainToolbar.navigationIcon = if (visible) icon else null
        } else {
            mActivity.supportActionBar!!.setDisplayHomeAsUpEnabled(visible)
        }
    }

    /**
     * Add a badge to the navigation button.
     * The badge is small orange circle in the top right of the icon which contains text.
     * It can either be a 'N' or a number up to 99.
     *
     * @param count [N_BADGE] to show a 'N', 0 to hide the badge or any number up to 99.
     */
    fun setNavigationButtonBadge(count: Int) {
        val icon = mNavigationIcon
        if (icon != null) {
            if (count != 0) {
                val badgeIcon: NavigationBadgeDrawable
                var badgeLayer = mNavigationBadgeIcon
                if (badgeLayer == null) {
                    badgeIcon = NavigationBadgeDrawable(mContext)
                    badgeLayer = LayerDrawable(arrayOf(icon, badgeIcon))
                    mNavigationBadgeIcon = badgeLayer
                } else {
                    badgeIcon = badgeLayer.getDrawable(1) as NavigationBadgeDrawable
                }

                badgeIcon.setOrientation(
                    resources.configuration
                        .orientation == Configuration.ORIENTATION_LANDSCAPE
                )

                if (count == N_BADGE) {
                    badgeIcon.setText(
                        mContext.resources
                            .getString(R.string.oui_new_badge_text)
                    )
                } else {
                    badgeIcon.setText(
                        if (count > 99)
                            "99" else count.toString()
                    )
                }

                badgeLayer.invalidateSelf()
                mMainToolbar.navigationIcon = badgeLayer
            } else {
                mNavigationBadgeIcon = null
                mMainToolbar.navigationIcon = mNavigationIcon
            }
        } else
            Log.d(
                TAG, "setNavigationButtonBadge: no navigation icon" +
                        " has been set"
            )
    }

    /**
     * Set the Tooltip of the navigation button.
     */
    fun setNavigationButtonTooltip(tooltipText: CharSequence?) {
        mMainToolbar.setNavigationContentDescription(tooltipText)
    }

    /**
     * Callback for the navigation button click event.
     */
    fun setNavigationButtonOnClickListener(listener: View.OnClickListener?) {
        mMainToolbar.setNavigationOnClickListener(listener)
    }

    /**
     * Sets the icon the a back icon, the tooltip to 'Navigate up' and calls [AppCompatActivity.onBackPressed] when clicked.
     *
     * @see setNavigationButtonIcon
     * @see setNavigationButtonTooltip
     */
    @Suppress("DEPRECATION")
    fun setNavigationButtonAsBack() {
        if (!isInEditMode) {
            mActivity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            setNavigationButtonOnClickListener { mActivity.onBackPressed() }
        }
    }

    //
    // Search Mode methods
    //

    /**
     * Show the [SearchView] in the Toolbar.
     * To enable the voice input icon in the SearchView, please refer to the project wiki.
     */
    open fun showSearchMode() {
        mIsSearchMode = true
        if (mIsActionMode) dismissActionMode()
        mOnBackPressedCallback.isEnabled = true
        animatedVisibility(mMainToolbar, View.GONE)
        animatedVisibility(mSearchToolbar, View.VISIBLE)
        mFooterContainer.visibility = View.GONE

        mCollapsingToolbarLayout.title = resources
            .getString(R.string.sesl_searchview_description_search)
        mCollapsingToolbarLayout.seslSetSubtitle(null)
        setExpanded(false, true)

        mSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                val listener = mSearchModeListener
                if (listener != null)
                    return listener.onQueryTextSubmit(query)
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val listener = mSearchModeListener
                if (listener != null)
                    return listener.onQueryTextChange(newText)
                return false
            }
        })
        mSearchView.isIconified = false

        mSearchModeListener?.onSearchModeToggle(mSearchView, true)
    }

    /**
     * Dismiss the [SearchView] in the Toolbar.
     *
     * @see showSearchMode
     */
    open fun dismissSearchMode() {
        mSearchModeListener?.onSearchModeToggle(mSearchView, false)
        mIsSearchMode = false

        mOnBackPressedCallback.isEnabled = false
        // ★ حل المشكلة 2: تأخير تصفير النص حتى ينتهي أنيميشن الإغلاق (200ms) لمنع وميض كلمة "بحث"
        mSearchView.postDelayed({ mSearchView.setQuery("", false) }, 200)
        animatedVisibility(mSearchToolbar, View.GONE)
        animatedVisibility(mMainToolbar, View.VISIBLE)
        mFooterContainer.visibility = View.VISIBLE

        setTitle(mTitleExpanded, mTitleCollapsed)
        mCollapsingToolbarLayout.seslSetSubtitle(mSubtitleExpanded)
    }

    /**
     * Check if SearchMode is enabled(=the [SearchView] in the Toolbar is visible).
     */
    val isSearchMode: Boolean
        get() = mIsSearchMode

    /**
     * Returns the [SearchView] of the Toolbar.
     */
    val searchView: SearchView
        get() = mSearchView

    /**
     * Set the [SearchModeListener] for the Toolbar's SearchMode.
     */
    fun setSearchModeListener(listener: SearchModeListener?) {
        mSearchModeListener = listener
    }

    /**
     * Forward the voice input result to the Toolbar.
     */
    fun onSearchModeVoiceInputResult(intent: Intent) {
        if (Intent.ACTION_SEARCH == intent.action) {
            mSearchView.setQuery(intent.getStringExtra(SearchManager.QUERY), true)
        }
    }


    fun setActionModeToolbarShowAlwaysMax(max: Int) {
        mAMTMenuShowAlwaysMax = max
    }


    fun setOnActionModeListener(callback: ActionModeCallback?) {
        mActionModeCallback = callback
    }


    //
    // Action Mode methods
    //

    /**
     * Show the Toolbar's ActionMode. This will show a 'All' Checkbox instead of the navigation button,
     * temporarily replace the Toolbar's title with a counter ('x selected')
     * and show a [BottomNavigationView] in the footer.
     * The ActionMode is useful when the user can select items in a list.
     *
     * @see setActionModeAllSelector
     * @see setActionModeCheckboxListener
     * @see setActionModeMenu
     * @see setActionModeMenuListener
     * @see setActionModeToolbarMenu
     */
    open fun showActionMode() {
        mIsActionMode = true
        if (mIsSearchMode) {
            animatedVisibility(mSearchToolbar, View.GONE)
        }
        mOnBackPressedCallback.isEnabled = true
        animatedVisibility(mMainToolbar, View.GONE)
        animatedVisibility(mActionModeToolbar, View.VISIBLE)
        mFooterContainer.visibility = View.GONE
        mBottomActionModeBar.visibility = View.VISIBLE

        // setActionModeCount(0, -1);
        mAppBarLayout.addOnOffsetChangedListener(mActionModeTitleFadeListener)
        mCollapsingToolbarLayout.seslSetSubtitle(null)
        mMainToolbar.subtitle = null

        updateActionModeMenuVisibility(mContext.resources.configuration)

        mActionModeCallback?.onShow(this)
    }


    private fun updateActionModeMenuVisibility(config: Configuration) {
        if (isActionMode) {
            if (mSelectedItemsCount > 0) {
                if (switchActionModeMenu && config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mBottomActionModeBar.visibility = View.GONE
                    mActionModeToolbar.menu.setGroupVisible(AMT_GROUP_MENU_ID, true)
                } else {
                    mBottomActionModeBar.visibility = View.VISIBLE
                    mActionModeToolbar.menu.setGroupVisible(AMT_GROUP_MENU_ID, false)
                }
            } else {
                mBottomActionModeBar.visibility = View.GONE
                mActionModeToolbar.menu.setGroupVisible(AMT_GROUP_MENU_ID, false)
            }
        }
    }


    /**
     * Dismiss the ActionMode.
     *
     * @see showActionMode
     */
    open fun dismissActionMode() {
        mIsActionMode = false
        animatedVisibility(mActionModeToolbar, View.GONE)
        mBottomActionModeBar.visibility = View.GONE

        if (mIsSearchMode) {
            mOnBackPressedCallback.isEnabled = true
            animatedVisibility(mSearchToolbar, View.VISIBLE)
            mFooterContainer.visibility = View.GONE
            mCollapsingToolbarLayout.title = resources.getString(R.string.sesl_searchview_description_search)
            mCollapsingToolbarLayout.seslSetSubtitle(null)
            // ★ الإصلاح: إزالة setIconified واستخدام clearFocus لمنع الكيبورد من القفز
            mSearchView.clearFocus()
        } else {
            mOnBackPressedCallback.isEnabled = false
            animatedVisibility(mMainToolbar, View.VISIBLE)
            mFooterContainer.visibility = View.VISIBLE
            setTitle(mTitleExpanded, mTitleCollapsed)
            mCollapsingToolbarLayout.seslSetSubtitle(mSubtitleExpanded)
            mMainToolbar.subtitle = mSubtitleCollapsed
        }

        mAppBarLayout.removeOnOffsetChangedListener(mActionModeTitleFadeListener)
        setActionModeAllSelector(0, true, false)
        mActionModeCallback?.onDismiss(this)
    }

    /**
     * Checks if the ActionMode is enabled.
     */
    val isActionMode: Boolean
        get() = mIsActionMode

    /**
     * Set the menu resource for the ActionMode's [BottomNavigationView]
     */
    @Deprecated("Use setActionModeMenu(int)")
    fun setActionModeBottomMenu(@MenuRes menuRes: Int) {
        mBottomActionModeBar.inflateMenu(menuRes)
    }


    /**
     * Set the menu resource for the ActionMode's [BottomNavigationView].
     * On landscape orientation where ActionMode's [BottomNavigationView] will be hidden,
     * the visible items from this menu resource we be shown to ActionMode's [Toolbar] [Menu]
     */
    fun setActionModeMenu(@MenuRes menuRes: Int) {
        actionModeBottomMenu.clear()
        actionModeToolbarMenu.removeGroup(AMT_GROUP_MENU_ID)
        mBottomActionModeBar.inflateMenu(menuRes)
        val AMToolbarMenu = mActionModeToolbar.menu
        AMToolbarMenu.removeGroup(AMT_GROUP_MENU_ID)
        val AMBottomMenu = mBottomActionModeBar.menu
        val size = AMBottomMenu.size()
        var menuItemsAdded = 0
        for (a in 0 until size) {
            val ambMenuItem = AMBottomMenu.getItem(a)
            if (ambMenuItem.isVisible) {
                menuItemsAdded++
                val amtMenuItem = AMToolbarMenu.add(AMT_GROUP_MENU_ID, ambMenuItem.itemId, Menu.NONE, ambMenuItem.title)
                if (menuItemsAdded <= mAMTMenuShowAlwaysMax) {
                    amtMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            }
        }
        switchActionModeMenu = true
    }

    /**
     * Returns the [Menu] of the ActionMode's [BottomNavigationView].
     */
    val actionModeBottomMenu: Menu
        get() = mBottomActionModeBar.menu

    /**
     * Set the listener for the ActionMode's [BottomNavigationView].
     */
    @Deprecated("See setActionModeMenuListener")
    fun setActionModeBottomMenuListener(listener: NavigationBarView.OnItemSelectedListener?) {
        mBottomActionModeBar.setOnItemSelectedListener(listener)
    }


    /**
     * Set the listener for the ActionMode's [BottomNavigationView].
     * On landscape orientation, the same listener will be invoke for ActionMode's [Toolbar] [MenuItem]s
     * which are copied from ActionMode's [BottomNavigationView]
     */
    fun setActionModeMenuListener(listener: NavigationBarView.OnItemSelectedListener) {
        mBottomActionModeBar.setOnItemSelectedListener(listener)
        mActionModeToolbar.setOnMenuItemClickListener { item ->
            listener.onNavigationItemSelected(mActionModeToolbar.menu.findItem(item.itemId))
        }
    }


    /**
     * Set the menu resource for the ActionMode's [Toolbar].
     */
    fun setActionModeToolbarMenu(@MenuRes menuRes: Int) {
        mActionModeToolbar.inflateMenu(menuRes)
    }


    /**
     * Set the listener for the ActionMode's [Toolbar].
     */
    fun setActionModeToolbarMenuListener(listener: Toolbar.OnMenuItemClickListener?) {
        mActionModeToolbar.setOnMenuItemClickListener(listener)
    }


    /**
     * Returns the [Menu] of the ActionMode's [Toolbar].
     */
    val actionModeToolbarMenu: Menu
        get() = mActionModeToolbar.menu


    /**
     * Set the ActionMode's count and  checkbox enabled state.
     * Check state will stay.
     *
     * @param count number of selected items in the list
     * @param enabled enabled click
     */
    fun setActionModeAllSelector(count: Int, enabled: Boolean) {
        setActionModeAllSelector(count, enabled, null)
    }


    /**
     * Set the ActionMode's count and Select all checkBox's enabled state and check state
     *
     * @param count number of selected items in the list
     * @param enabled enable or disable click
     * @param checked
     */
    fun setActionModeAllSelector(count: Int, enabled: Boolean, checked: Boolean?) {
        if (mSelectedItemsCount != count) {
            mSelectedItemsCount = count
            val title = if (count > 0)
                resources.getString(R.string.oui_action_mode_n_selected, count)
            else
                resources.getString(R.string.oui_action_mode_select_items)

            // ★ حل المشكلة 3: تحديث النص فقط إذا كان وضع التحديد نشطاً
            // هذا يمنع وميض كلمة "تحديد عناصر" أثناء إغلاق الوضع
            if (mIsActionMode) {
                mCollapsingToolbarLayout.title = title
                mActionModeTitleTextView.text = title
            }
            updateActionModeMenuVisibility(mContext.resources.configuration)
        }
        if (checked != null && checked != mActionModeCheckBox.isChecked) {
            mActionModeCheckBox.isChecked = checked
        }
        if (enabled != mActionModeSelectAll.isEnabled) {
            mActionModeSelectAll.isEnabled = enabled
        }
    }


    /**
     * Set the ActionMode's count. This will change the count in the Toolbar's title
     * and if count = total, the 'All' Checkbox will be checked.
     *
     * @param count number of selected items in the list
     * @param total number of total items in the list
     */
    @Deprecated("use setActionModeAllSelector(int, Boolean, Boolean)")
    fun setActionModeCount(count: Int, total: Int) {
        mSelectedItemsCount = count
        val title = if (count > 0)
            resources.getString(R.string.oui_action_mode_n_selected, count)
        else
            resources.getString(R.string.oui_action_mode_select_items)

        mCollapsingToolbarLayout.title = title
        mActionModeTitleTextView.text = title
        updateActionModeMenuVisibility(mContext.resources.configuration)
        mActionModeCheckBox.isChecked = count == total
    }

    /**
     * Set the listener for the 'All' Checkbox of the ActionMode.
     */
    fun setActionModeCheckboxListener(listener: CompoundButton.OnCheckedChangeListener?) {
        mActionModeCheckBox.setOnCheckedChangeListener(listener)
    }

    //
    // others
    //
    class ToolbarLayoutParams(c: Context?, attrs: AttributeSet?) : LinearLayout.LayoutParams(c, attrs) {
        @JvmField
        var layout_location: Int = 0

        init {
            if (c != null && attrs != null) {
                val a = c.obtainStyledAttributes(attrs, R.styleable.ToolbarLayoutParams)
                layout_location = a.getInteger(R.styleable.ToolbarLayoutParams_layout_location, 0)
                a.recycle()
            }
        }
    }

    private fun CLLPWrapper(oldLp: ViewGroup.LayoutParams): CoordinatorLayout.LayoutParams {
        val old = oldLp as LinearLayout.LayoutParams
        val newLp = CoordinatorLayout.LayoutParams(old)
        newLp.width = old.width
        newLp.height = old.height
        newLp.leftMargin = old.leftMargin
        newLp.topMargin = old.topMargin
        newLp.rightMargin = old.rightMargin
        newLp.bottomMargin = old.bottomMargin
        newLp.gravity = old.gravity
        return newLp
    }

    private fun animatedVisibility(view: View, visibility: Int) {
        view.visibility = View.VISIBLE
        view.animate()
            .alphaBy(1.0f)
            .alpha(if (visibility == View.VISIBLE) 1.0f else 0.0f)
            .setDuration(200)
            .setInterpolator(
                PathInterpolator(0.33f, 0.0f, 0.1f, 1.0f)
            )
            .withEndAction { view.visibility = visibility }
            .start()
    }

    private inner class AppBarOffsetListener : AppBarLayout.OnOffsetChangedListener {
        override fun onOffsetChanged(layout: AppBarLayout, verticalOffset: Int) {
            if (mActionModeToolbar.visibility == View.VISIBLE) {
                val layoutPosition = Math.abs(mAppBarLayout.top)
                val alphaRange = mCollapsingToolbarLayout.height.toFloat() * 0.17999999f
                val toolbarTitleAlphaStart = mCollapsingToolbarLayout.height.toFloat() * 0.35f

                if (mAppBarLayout.seslIsCollapsed()) {
                    mActionModeTitleTextView.alpha = 1.0f
                } else {
                    var collapsedTitleAlpha = ((150.0f / alphaRange)
                            * (layoutPosition.toFloat() - toolbarTitleAlphaStart))

                    if (collapsedTitleAlpha >= 0.0f && collapsedTitleAlpha <= 255.0f) {
                        collapsedTitleAlpha /= 255.0f
                        mActionModeTitleTextView.alpha = collapsedTitleAlpha
                    } else if (collapsedTitleAlpha < 0.0f)
                        mActionModeTitleTextView.alpha = 0.0f
                    else
                        mActionModeTitleTextView.alpha = 1.0f
                }
            }
        }
    }

    companion object {
        private const val TAG = "OneUiToolBarLayout"

        const val AMT_GROUP_MENU_ID = 9999

        private const val MAIN_CONTENT = 0
        private const val APPBAR_HEADER = 1
        private const val FOOTER = 2
        private const val ROOT = 3

        const val N_BADGE = -1
    }
}
