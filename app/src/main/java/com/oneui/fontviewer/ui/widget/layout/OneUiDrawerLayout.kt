package com.oneui.fontviewer.ui.widget.layout

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import androidx.activity.OnBackPressedCallback
import androidx.annotation.Dimension
import androidx.annotation.Px
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout

import java.text.NumberFormat
import java.util.Locale

import dev.oneuiproject.oneui.design.R
import dev.oneuiproject.oneui.utils.ViewUtils

/**
 * Custom DrawerLayout extending [OneUiToolBarLayout]. Looks and behaves the same as the one in Apps from Samsung.
 */
class OneUiDrawerLayout(context: Context, attrs: AttributeSet?) : OneUiToolBarLayout(context, attrs) {

    private val mNumberFormat: NumberFormat = NumberFormat.getInstance(Locale.getDefault())
    private val mOnBackPressedCallback: OnBackPressedCallback =
        object : OnBackPressedCallback(true) {
            @Suppress("DEPRECATION")
            override fun handleOnBackPressed() {
                if (mDrawer.isDrawerOpen(mDrawerContent)) {
                    mDrawer.closeDrawer(mDrawerContent, true)
                    return
                }
                this.isEnabled = false
                mActivity.onBackPressed()
                this.isEnabled = true
            }
        }
    private val mDrawerListener = DrawerListener()

    private var mIsRtl = false

    private lateinit var mDrawer: DrawerLayout
    private lateinit var mToolbarContent: LinearLayout
    private lateinit var mDrawerContent: LinearLayout
    private var mHeaderView: View? = null
    private var mHeaderButton: AppCompatImageButton? = null
    private var mHeaderBadge: TextView? = null
    private lateinit var mDrawerContainer: FrameLayout
    private var scrimAlpha = 0f
    private var systemBarsColor = 0

    init {
        initDrawer()

        if (!isInEditMode) {
            mActivity.onBackPressedDispatcher.addCallback(mOnBackPressedCallback)
            ViewUtils.semSetRoundedCorners(mActivity.window.decorView, ViewUtils.SEM_ROUNDED_CORNER_NONE)
        }
    }

    override fun initLayoutAttrs(attrs: AttributeSet?) {
        super.initLayoutAttrs(attrs)

        val a = mContext.theme
            .obtainStyledAttributes(
                attrs, R.styleable.ToolbarLayout, 0, 0
            )
        try {
            mLayout = a.getResourceId(
                R.styleable.ToolbarLayout_android_layout,
                R.layout.oui_layout_drawerlayout
            )
        } finally {
            a.recycle()
        }
    }

    override fun inflateChildren() {
        if (mLayout != R.layout.oui_layout_drawerlayout) {
            Log.w(TAG, "Inflating custom $TAG")
        }
        LayoutInflater.from(mContext)
            .inflate(mLayout, this, true)
    }

    private fun initDrawer() {
        mIsRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

        setNavigationButtonIcon(ContextCompat.getDrawable(mContext, R.drawable.oui_ic_ab_drawer))
        setNavigationButtonTooltip(resources.getText(R.string.oui_navigation_drawer))

        mDrawer = findViewById(R.id.drawerlayout_drawer)
        mToolbarContent = findViewById(R.id.drawerlayout_toolbar_content)
        mDrawerContent = findViewById(R.id.drawerlayout_drawer_content)

        val headerView = mDrawerContent.findViewById<View>(R.id.drawerlayout_default_header)
        mHeaderView = headerView
        mHeaderButton = headerView.findViewById(R.id.drawerlayout_header_button)
        mHeaderBadge = headerView.findViewById(R.id.drawerlayout_header_badge)

        mDrawerContainer = mDrawerContent.findViewById(R.id.drawerlayout_drawer_container)
        val scrimColor = mContext.getColor(R.color.oui_drawerlayout_drawer_dim_color)
        mDrawer.setScrimColor(scrimColor)
        scrimAlpha = ((scrimColor shr 24) and 0xFF) / 255f

        val sbTypedValue = TypedValue()
        if (mContext.theme.resolveAttribute(R.attr.roundedCornerColor, sbTypedValue, true)) {
            systemBarsColor = sbTypedValue.data
        } else {
            systemBarsColor = ContextCompat.getColor(mContext, R.color.oui_round_and_bgcolor)
        }

        mDrawer.setDrawerElevation(0f)
        setDrawerWidth()
        setDrawerCornerRadius(DEFAULT_DRAWER_RADIUS)
        setNavigationButtonOnClickListener { mDrawer.openDrawer(mDrawerContent) }

        if (!isInEditMode) {
            mDrawer.addDrawerListener(mDrawerListener)
            if (sIsDrawerOpened) {
                mDrawer.post {
                    mDrawerListener.onDrawerSlide(
                        mDrawerContent, 1f
                    )
                }
            }
        }
    }

    override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams) {
        if (!::mToolbarContent.isInitialized || !::mDrawerContainer.isInitialized) {
            super.addView(child, index, params)
        } else {
            when ((params as OneUiToolBarLayout.ToolbarLayoutParams).layout_location) {
                DRAWER_HEADER -> {
                    mDrawerContent.removeView(mHeaderView)
                    mHeaderButton = null
                    mHeaderBadge = null
                    mDrawerContent.addView(child, 0, params)
                    mHeaderView = mDrawerContent.getChildAt(0)
                }

                DRAWER_PANEL ->
                    mDrawerContainer.addView(child, params)

                else ->
                    super.addView(child, index, params)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mIsRtl = newConfig.layoutDirection == View.LAYOUT_DIRECTION_RTL
        setDrawerWidth()
        if (sIsDrawerOpened) {
            mDrawer.post {
                mDrawerListener.onDrawerSlide(
                    mDrawerContent, 1f
                )
            }
        }
    }

    private fun lockDrawerIfAvailable(lock: Boolean) {
        if (::mDrawer.isInitialized) {
            if (lock) {
                mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            } else {
                mDrawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
            }
        }
    }

    /**
     * يحدّد حالة القفل بناءً على الأوضاع النشطة حالياً: الدرج يبقى مقفلاً
     * طالما أي من وضع البحث أو وضع التحديد المتعدد (ActionMode) فعّال.
     * هذا يمنع فتح الدرج بالخطأ عند إغلاق أحد الوضعين بينما الآخر ما زال فعّالاً.
     */
    private fun updateDrawerLock() {
        lockDrawerIfAvailable(isActionMode || isSearchMode)
    }

    @Suppress("DEPRECATION")
    private fun setDrawerWidth() {
        val lp = mDrawerContent.layoutParams

        val wm = mContext
            .getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = wm.defaultDisplay

        val size = Point()
        display.getSize(size)

        val displayWidth = size.x
        val density = resources.displayMetrics.density
        val dpi = displayWidth.toFloat() / density

        val widthRate: Double
        if (dpi >= 1920.0f) {
            widthRate = 0.22
        } else if (dpi >= 960.0f && dpi < 1920.0f) {
            widthRate = 0.2734
        } else if (dpi >= 600.0f && dpi < 960.0f) {
            widthRate = 0.46
        } else if (dpi >= 480.0f && dpi < 600.0f) {
            widthRate = 0.5983
        } else {
            widthRate = 0.844
        }

        lp.width = (displayWidth.toDouble() * widthRate).toInt()
    }

    override fun showActionMode() {
        lockDrawerIfAvailable(true)
        super.showActionMode()
    }

    override fun dismissActionMode() {
        super.dismissActionMode()
        updateDrawerLock()
    }

    override fun showSearchMode() {
        lockDrawerIfAvailable(true)
        super.showSearchMode()
    }

    override fun dismissSearchMode() {
        super.dismissSearchMode()
        updateDrawerLock()
    }

    //
    // Drawer methods
    //

    /**
     * Show a margin at the top of the drawer panel. Some Apps from Samsung do have this.
     */
    fun showDrawerTopMargin(show: Boolean) {
        val lp = mDrawerContent.layoutParams as ViewGroup.MarginLayoutParams
        lp.topMargin = if (show)
            resources.getDimensionPixelSize(R.dimen.oui_drawerlayout_drawer_top_margin)
        else
            0
        mDrawerContent.layoutParams = lp
    }

    /**
     * Set a custom radius for the drawer panel's edges.
     */
    fun setDrawerCornerRadius(@Dimension dp: Float) {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics
        ).toInt()
        setDrawerCornerRadius(px)
    }

    /**
     * Set a custom radius for the drawer panel's edges.
     */
    fun setDrawerCornerRadius(@Px px: Int) {
        mDrawerContent.outlineProvider = DrawerOutlineProvider(px)
        mDrawerContent.clipToOutline = true
    }

    /**
     * Set the icon of the drawer button.
     * The drawer button is the button in the top right corner of the drawer panel.
     */
    fun setDrawerButtonIcon(icon: Drawable?) {
        val headerButton = mHeaderButton
        if (headerButton != null) {
            headerButton.setImageDrawable(icon)
            headerButton.imageTintList = ColorStateList.valueOf(
                mContext.getColor(R.color.oui_drawerlayout_header_icon_color)
            )
            mHeaderView!!.visibility = if (icon != null) View.VISIBLE else View.GONE
        } else {
            Log.e(
                TAG, "setDrawerButtonIcon: this method can be used " +
                        "only with the default header view"
            )
        }
    }

    /**
     * Set the tooltip of the drawer button.
     * The drawer button is the button in the top right corner of the drawer panel.
     */
    fun setDrawerButtonTooltip(tooltipText: CharSequence?) {
        val headerButton = mHeaderButton
        if (headerButton != null) {
            TooltipCompat.setTooltipText(headerButton, tooltipText)
        } else {
            Log.e(
                TAG, "setDrawerButtonTooltip: this method can be used " +
                        "only with the default header view"
            )
        }
    }

    /**
     * Set the click listener of the drawer button.
     * The drawer button is the button in the top right corner of the drawer panel.
     */
    fun setDrawerButtonOnClickListener(listener: View.OnClickListener?) {
        val headerButton = mHeaderButton
        if (headerButton != null) {
            headerButton.setOnClickListener(listener)
        } else {
            Log.e(
                TAG, "setDrawerButtonOnClickListener: this method can be used " +
                        "only with the default header view"
            )
        }
    }

    /**
     * Set the badges of the navigation button and drawer button.
     * The drawer button is the button in the top right corner of the drawer panel.
     * The badge is small orange circle in the top right of the icon which contains text.
     * It can either be a 'N' or a number up to 99.
     *
     * @param navigationIcon [N_BADGE] to show a 'N', 0 to hide the badge or any number up to 99.
     * @param drawerIcon     [N_BADGE] to show a 'N', 0 to hide the badge or any number up to 99.
     */
    fun setButtonBadges(navigationIcon: Int, drawerIcon: Int) {
        setNavigationButtonBadge(navigationIcon)
        setDrawerButtonBadge(drawerIcon)
    }

    /**
     * Set the badge of the drawer button.
     * The drawer button is the button in the top right corner of the drawer panel.
     * The badge is small orange circle in the top right of the icon which contains text.
     * It can either be a 'N' or a number up to 99.
     *
     * @param count [N_BADGE] to show a 'N', 0 to hide the badge or any number up to 99.
     */
    fun setDrawerButtonBadge(count: Int) {
        val headerBadge = mHeaderBadge
        if (headerBadge != null) {
            if (count > 0) {
                val shown = if (count > 99) 99 else count

                val badgeText = mNumberFormat.format(shown.toLong())
                headerBadge.text = badgeText

                val lp = headerBadge.layoutParams
                lp.width = (resources.getDimension(R.dimen.oui_n_badge_default_width) +
                        (badgeText.length.toFloat() * resources.getDimension(R.dimen.oui_n_badge_additional_width))).toInt()
                lp.height = resources.getDimensionPixelSize(R.dimen.oui_n_badge_view_size)
                headerBadge.layoutParams = lp

                headerBadge.visibility = View.VISIBLE
            } else if (count == OneUiToolBarLayout.N_BADGE) {
                headerBadge.text = resources.getString(R.string.oui_new_badge_text)
                headerBadge.visibility = View.VISIBLE
            } else {
                headerBadge.visibility = View.GONE
            }
        } else {
            Log.e(
                TAG, "setDrawerButtonBadge: this method can be used " +
                        "only with the default header view"
            )
        }
    }

    /**
     * Open or close the drawer panel with an optional animation.
     *
     * @param animate whether or not to animate the opening and closing
     */
    fun setDrawerOpen(open: Boolean, animate: Boolean) {
        if (open) {
            mDrawer.openDrawer(mDrawerContent, animate)
        } else {
            mDrawer.closeDrawer(mDrawerContent, animate)
        }
    }

    private inner class DrawerOutlineProvider(@Px private val mCornerRadius: Int) : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                if (mIsRtl)
                    0
                else
                    -mCornerRadius,
                0,
                if (mIsRtl)
                    view.width + mCornerRadius
                else
                    view.width, view.height,
                mCornerRadius.toFloat()
            )
        }
    }

    private inner class DrawerListener : DrawerLayout.SimpleDrawerListener() {
        @Suppress("DEPRECATION")
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
            super.onDrawerSlide(drawerView, slideOffset)

            val translationView = findViewById<View?>(R.id.drawer_custom_translation)
            val window = mActivity.window

            var slideX = drawerView.width * slideOffset
            if (mIsRtl) slideX *= -1f
            if (translationView != null) translationView.translationX = slideX
            else mToolbarContent.translationX = slideX

            val hsv = FloatArray(3)
            Color.colorToHSV(systemBarsColor, hsv)
            hsv[2] *= 1f - (slideOffset * scrimAlpha)
            window.statusBarColor = Color.HSVToColor(hsv)
            window.navigationBarColor = Color.HSVToColor(hsv)
        }

        override fun onDrawerOpened(drawerView: View) {
            super.onDrawerOpened(drawerView)
            sIsDrawerOpened = true
        }

        override fun onDrawerClosed(drawerView: View) {
            super.onDrawerClosed(drawerView)
            sIsDrawerOpened = false
        }
    }

    companion object {
        private const val TAG = "OneUiDrawerLayout"

        private const val DEFAULT_DRAWER_RADIUS = 15f

        private const val DRAWER_HEADER = 4
        private const val DRAWER_PANEL = 5

        private var sIsDrawerOpened = false
    }
}
