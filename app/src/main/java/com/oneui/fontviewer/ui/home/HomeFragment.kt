package com.oneui.fontviewer.ui.home

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

import dev.oneuiproject.oneui.utils.internal.ToolbarLayoutUtils

import com.oneui.fontviewer.R

class HomeFragment : Fragment() {

    private var mEnableBackToHeader = false
    private var mAppBarLayout: AppBarLayout? = null
    private var mCollapsingToolbar: CollapsingToolbarLayout? = null
    private var mToolbar: Toolbar? = null
    private var mSwipeUpContainer: View? = null
    private var mBottomContainer: ViewGroup? = null
    private val mHandler = Handler(Looper.getMainLooper())

    private var mOffsetListener: AppBarOffsetListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)
        setupToolbar()
        setupAppBar(resources.configuration)
        initContent(view)
    }

    @Suppress("DEPRECATION")
    private fun initContent(view: View) {
        val headerIcon = view.findViewById<ImageView?>(R.id.header_app_icon)
        if (headerIcon != null) {
            headerIcon.setImageResource(R.drawable.ic_launcher)
        }

        val bottomIcon = view.findViewById<ImageView?>(R.id.bottom_app_icon)
        if (bottomIcon != null) {
            bottomIcon.setImageResource(R.drawable.ic_launcher)
        }

        try {
            val versionName = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
            val versionText = "Version $versionName"

            val headerVersion = view.findViewById<TextView?>(R.id.about_header_app_version)
            if (headerVersion != null) {
                headerVersion.text = versionText
            }

            val bottomVersion = view.findViewById<TextView?>(R.id.about_bottom_app_version)
            if (bottomVersion != null) {
                bottomVersion.text = versionText
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initViews(view: View) {
        mAppBarLayout = view.findViewById(R.id.app_bar)
        mCollapsingToolbar = view.findViewById(R.id.collapsing_toolbar)
        mToolbar = view.findViewById(R.id.toolbar)
        mSwipeUpContainer = view.findViewById(R.id.swipe_up_container)
        mBottomContainer = view.findViewById(R.id.bottom_container)

        mOffsetListener = AppBarOffsetListener()
    }

    @Suppress("DEPRECATION")
    private fun setupToolbar() {
        if (activity is AppCompatActivity) {
            val activity = requireActivity() as AppCompatActivity
            activity.setSupportActionBar(mToolbar)
            val actionBar = activity.supportActionBar
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true)
                actionBar.setDisplayShowTitleEnabled(false)
            }
            mToolbar!!.setNavigationOnClickListener { activity.onBackPressed() }
        }
    }


    private fun setupAppBar(config: Configuration) {
        ToolbarLayoutUtils.hideStatusBarForLandscape(requireActivity(), config.orientation)
        ToolbarLayoutUtils.updateListBothSideMargin(requireActivity(), mBottomContainer!!)

        val appBarLayout = mAppBarLayout!!
        if (config.orientation != Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode()) {
            appBarLayout.seslSetCustomHeightProportion(true, 0.5f)
            mEnableBackToHeader = true

            val offsetListener = mOffsetListener
            if (offsetListener != null) {
                appBarLayout.removeOnOffsetChangedListener(offsetListener)
                appBarLayout.addOnOffsetChangedListener(offsetListener)
            }

            appBarLayout.setExpanded(true, false)

            val swipeUpContainer = mSwipeUpContainer
            if (swipeUpContainer != null) {
                swipeUpContainer.visibility = View.VISIBLE
                val lp = swipeUpContainer.layoutParams
                lp.height = resources.displayMetrics.heightPixels / 2
            }
        } else {
            val offsetListener = mOffsetListener
            if (offsetListener != null) {
                appBarLayout.removeOnOffsetChangedListener(offsetListener)
            }

            appBarLayout.setExpanded(false, false)
            mEnableBackToHeader = false
            appBarLayout.seslSetCustomHeightProportion(true, 0f)

            mBottomContainer?.alpha = 1f
            mSwipeUpContainer?.visibility = View.GONE
        }
    }


    fun handleBackPressed(): Boolean {
        val appBarLayout = mAppBarLayout
        if (mEnableBackToHeader && appBarLayout != null && appBarLayout.seslIsCollapsed()) {
            appBarLayout.setExpanded(true, true)
            return true
        }
        return false
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupAppBar(newConfig)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val appBarLayout = mAppBarLayout
        val offsetListener = mOffsetListener
        if (appBarLayout != null && offsetListener != null) {
            appBarLayout.removeOnOffsetChangedListener(offsetListener)
        }
        mHandler.removeCallbacksAndMessages(null)
    }

    private fun isInMultiWindowMode(): Boolean {
        return requireActivity().isInMultiWindowMode
    }

    private inner class AppBarOffsetListener : AppBarLayout.OnOffsetChangedListener {
        override fun onOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
            val totalScrollRange = appBarLayout.totalScrollRange
            val abs = Math.abs(verticalOffset)

            val swipeUpContainer = mSwipeUpContainer
            if (swipeUpContainer != null) {
                if (abs >= totalScrollRange / 2) {
                    swipeUpContainer.alpha = 0f
                } else if (abs == 0) {
                    swipeUpContainer.alpha = 1f
                } else {
                    val offsetAlpha = (appBarLayout.y / totalScrollRange)
                    var arrowAlpha = 1 - (offsetAlpha * -3)

                    if (arrowAlpha < 0) {
                        arrowAlpha = 0f
                    } else if (arrowAlpha > 1) {
                        arrowAlpha = 1f
                    }
                    swipeUpContainer.alpha = arrowAlpha
                }
            }

            val bottomContainer = mBottomContainer
            if (bottomContainer != null) {
                val collapsingToolbarHeight = mCollapsingToolbar!!.height
                val alphaRange = collapsingToolbarHeight * 0.143f
                val layoutPosition = Math.abs(appBarLayout.top).toFloat()
                var bottomAlpha = (150.0f / alphaRange) * (layoutPosition - (collapsingToolbarHeight * 0.35f))

                if (bottomAlpha < 0) {
                    bottomAlpha = 0f
                } else if (bottomAlpha >= 255) {
                    bottomAlpha = 255f
                }

                bottomContainer.alpha = bottomAlpha / 255
            }
        }
    }
}
