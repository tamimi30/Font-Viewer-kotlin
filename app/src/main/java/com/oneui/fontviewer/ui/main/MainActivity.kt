package com.oneui.fontviewer.ui.main

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.fontviewer.FontPrefetcher
import com.oneui.fontviewer.data.update.UpdateRepository
import com.oneui.fontviewer.service.BatchOperationState
import com.oneui.fontviewer.ui.base.BaseActivity
import com.oneui.fontviewer.ui.fontviewer.FontViewerActivity
import com.oneui.fontviewer.ui.home.HomeActivity
import com.oneui.fontviewer.ui.main.fragment.favorite.FavoriteFontListFragment
import com.oneui.fontviewer.ui.main.fragment.localfont.LocalFontListFragment
import com.oneui.fontviewer.ui.main.fragment.systemfont.SystemFontListFragment
import com.oneui.fontviewer.ui.main.fragment.trash.TrashFragment
import com.oneui.fontviewer.ui.main.list.search.SearchCoordinator
import com.oneui.fontviewer.ui.main.navigation.NavManager
import com.oneui.fontviewer.ui.main.navigation.UpdateBadgeController
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListAdapter
import com.oneui.fontviewer.ui.main.navigation.drawer.DrawerListItem
import com.oneui.fontviewer.ui.settings.SettingsActivity
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout

import java.util.EnumMap

class MainActivity : BaseActivity(),
    LocalFontListFragment.OnFontSelectedListener,
    SystemFontListFragment.OnFontSelectedListener,
    FavoriteFontListFragment.OnFontSelectedListener,
    NavManager.Host {

    private var isUIReady = false
    private var mIsMinSplashTimeElapsed = false
    private var mIsInitialDataReady = false
    private var mSplashStartTime = 0L
    private lateinit var mDrawerLayout: OneUiDrawerLayout
    private lateinit var mDrawerListView: RecyclerView
    private lateinit var mDrawerAdapter: DrawerListAdapter

    private val mFragmentsMap: MutableMap<DrawerListItem, Fragment> = EnumMap(DrawerListItem::class.java)

    override var currentDrawerListItem: DrawerListItem? = DrawerListItem.LOCAL_FONTS

    private val mCurrentDrawerListItem: DrawerListItem
        get() = currentDrawerListItem ?: DrawerListItem.LOCAL_FONTS

    private var mLocalFontsCount = 0
    private var mSystemFontsCount = 0
    private var mFavoriteFontsCount = 0
    private var mTrashFontsCount = 0

    private lateinit var mNavManager: NavManager

    private lateinit var mSearchCoordinator: SearchCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        mSplashStartTime = System.currentTimeMillis()

        super.onCreate(savedInstanceState)

        splashScreen.setKeepOnScreenCondition { !isUIReady }

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                android.R.anim.fade_in,
                android.R.anim.fade_out
            )
        }

        setContentView(R.layout.activity_main)

        // التحقق مما إذا كان هذا فتحاً جديداً للتطبيق (Cold Start) أم إعادة بناء بسبب تغيير اللغة أو الثيم (Hot Start).
        // عند إعادة البناء، البيانات والواجهات محفوظة مسبقًا، لذا نتخطى الانتظار الاصطناعي فورًا
        // لتفادي التأخير الملحوظ عند الضغط على زر الرجوع بعد تغيير اللغة.
        if (savedInstanceState != null) {
            mIsMinSplashTimeElapsed = true
        }

        mNavManager = NavManager(this)

        initViews()
        initFragmentsList()

        setupSearchCoordinator()

        setupDrawerButton()

        setupUpdateBadge()

        if (savedInstanceState != null) {
            mLocalFontsCount = savedInstanceState.getInt(KEY_LOCAL_FONTS_COUNT, 0)
            mSystemFontsCount = savedInstanceState.getInt(KEY_SYSTEM_FONTS_COUNT, 0)
            mFavoriteFontsCount = savedInstanceState.getInt(KEY_FAVORITE_FONTS_COUNT, 0)
            mTrashFontsCount = savedInstanceState.getInt(KEY_TRASH_FONTS_COUNT, 0)
            restoreFragmentsState(savedInstanceState)
            mSearchCoordinator.restoreState(savedInstanceState)
        } else {
            addAllFragments()
            currentDrawerListItem = DrawerListItem.LOCAL_FONTS
            mNavManager.showFragmentFast(DrawerListItem.LOCAL_FONTS)
            warmUpOtherFragments()
        }

        setupDrawer()
        updateDrawerTitle(mCurrentDrawerListItem)

        // لا نعالج توجيه الإشعار (الشاشة المستهدفة) إلا عند فتح جديد للنشاط (savedInstanceState == null).
        // عند تفعيل "عدم الاحتفاظ بالأنشطة" يُهدم النشاط ويُعاد بناؤه، ويعيد getIntent() عندها الـ Intent الأصلي الذي
        // أُنشئ به السجل في النظام (وهو Intent الإشعار الذي ضُغط عليه سابقاً ويحمل EXTRA_TARGET_FRAGMENT).
        // أما حذف الـ extras في handleIntent فيمسّ نسخة العملية فقط ولا يمسّ نسخة النظام. لذلك كان كل إعادة بناء
        // لاحقة تعيد التنقل إلى شاشة الإشعار القديمة بدلاً من آخر شاشة دخل إليها المستخدم.
        // أما الإشعار الذي يصل والنشاط حيّ أو مُعاد بناؤه فيصل عبر onNewIntent وتُعالجه بشكل صحيح.
        handleIntent(intent, savedInstanceState == null)

        requestNotificationPermissionIfNeeded()

        // التحقق من التحديث عند فتح التطبيق فقط (وليس عند إعادة البناء بسبب تغيير اللغة أو الثيم)
        if (savedInstanceState == null) {
            UpdateRepository.getInstance(this).checkForUpdateOnLaunch()
        }

        val elapsedTime = System.currentTimeMillis() - mSplashStartTime
        val remainingDelay = Math.max(0L, 4500L - elapsedTime)

        val splashHandler = Handler(mainLooper)

        splashHandler.postDelayed({
            mIsMinSplashTimeElapsed = true
            checkSplashReadyState()
        }, remainingDelay)

        // شبكة أمان: لا تُبقي الشاشة أكثر من هذه المدة حتى لو لم تصل إشارة الجاهزية
        splashHandler.postDelayed({
            mIsInitialDataReady = true
            checkSplashReadyState()
        }, 60000L)
    }

    private fun checkSplashReadyState() {
        if (mIsMinSplashTimeElapsed && mIsInitialDataReady) {
            isUIReady = true
        }
    }

    fun setAppReady() {
        mIsInitialDataReady = true
        checkSplashReadyState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, true)
    }

    /**
     * @param shouldHandleNavigation هل يُسمح بمعالجة توجيه الإشعار (الشاشة المستهدفة وإعادة فتح ديالوج التقدم).
     *                               يكون false عند إعادة بناء النشاط (savedInstanceState != null) كي لا يُعاد
     *                               تطبيق Intent قديم على شاشة اختارها المستخدم بعده.
     */
    private fun handleIntent(intent: Intent?, shouldHandleNavigation: Boolean) {
        mSearchCoordinator.handleSearchIntent(intent)

        if (intent != null && shouldHandleNavigation) {

            val fromNotif = intent.getBooleanExtra("from_notification", false)
            if (fromNotif) {
                BatchOperationState.setShouldReopenDialog(true)
                intent.removeExtra("from_notification")
            }

            val targetDrawerListItemName = intent.getStringExtra(EXTRA_TARGET_FRAGMENT)
            if (targetDrawerListItemName != null) {
                intent.removeExtra(EXTRA_TARGET_FRAGMENT)
                try {
                    val targetDrawerListItem = DrawerListItem.valueOf(targetDrawerListItemName)
                    if (targetDrawerListItem != DrawerListItem.HOME) {
                        if (::mNavManager.isInitialized && mCurrentDrawerListItem != targetDrawerListItem) {
                            mNavManager.navigateFromDrawer(targetDrawerListItem)
                        } else if (fromNotif) {
                            val frag = mFragmentsMap[targetDrawerListItem]
                            if (frag is LocalFontListFragment) {
                                frag.checkAndReopenProgressDialogPublic()
                            } else if (frag is TrashFragment) {
                                frag.checkAndReopenProgressDialogPublic()
                            } else if (frag is FavoriteFontListFragment) {
                                frag.checkAndReopenProgressDialogPublic()
                            }
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    Log.w("MainActivity", "Unknown DrawerListItem name: $targetDrawerListItemName")
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    PERM_REQUEST_POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun initViews() {
        mDrawerLayout = findViewById(R.id.drawer_layout)
        mDrawerListView = findViewById(R.id.drawer_list_view)
    }

    private fun initFragmentsList() {
        if (mFragmentsMap.isEmpty()) {
            mFragmentsMap[DrawerListItem.LOCAL_FONTS] = LocalFontListFragment()
            mFragmentsMap[DrawerListItem.SYSTEM_FONTS] = SystemFontListFragment()
            mFragmentsMap[DrawerListItem.FAVORITES] = FavoriteFontListFragment()
            mFragmentsMap[DrawerListItem.TRASH] = TrashFragment()
        }
    }

    private fun setupSearchCoordinator() {
        mSearchCoordinator = SearchCoordinator(this, mDrawerLayout)

        mSearchCoordinator.setProviders(
            SearchCoordinator.DrawerListItemProvider { mCurrentDrawerListItem },
            SearchCoordinator.FragmentProvider { drawerListItem ->
                if (drawerListItem == null) null else mFragmentsMap[drawerListItem]
            }
        )

        mSearchCoordinator.setSearchStateListener(object : SearchCoordinator.SearchStateListener {
            override fun onSearchExpanded() {
                notifyFragmentsSearchState(true)
            }

            override fun onSearchCollapsed() {
                updateDrawerTitle(mCurrentDrawerListItem)
                notifyFragmentsSearchState(false)
            }

            override fun onSearchQueryChanged(query: String?) {
            }
        })
    }

    private fun notifyFragmentsSearchState(isExpanded: Boolean) {
        val localFrag = mFragmentsMap[DrawerListItem.LOCAL_FONTS]
        if (localFrag is LocalFontListFragment) {
            localFrag.onSearchStateChanged(isExpanded)
        }

        val sysFrag = mFragmentsMap[DrawerListItem.SYSTEM_FONTS]
        if (sysFrag is SystemFontListFragment) {
            sysFrag.onSearchStateChanged(isExpanded)
        }

        val favFrag = mFragmentsMap[DrawerListItem.FAVORITES]
        if (favFrag is FavoriteFontListFragment) {
            favFrag.onSearchStateChanged(isExpanded)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        return true
    }

    fun setLocalFolderSelected(selected: Boolean) {
    }

    private fun setupDrawerButton() {
        mDrawerLayout.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_settings_outline))
        mDrawerLayout.setDrawerButtonTooltip(getText(R.string.drawer_settings))
        mDrawerLayout.setDrawerButtonOnClickListener { openSettingsActivity() }
    }

    /**
     * المؤشر البرتقالي (N / ج) على أيقونة الدرج وأيقونة الإعدادات يظهر عند توفر تحديث ويختفي بزواله.
     */
    private fun setupUpdateBadge() {
        UpdateBadgeController(mDrawerLayout)
            .bind(this, UpdateRepository.getInstance(this).availableUpdate)
    }

    private fun setDrawerOpen(open: Boolean, animate: Boolean) {
        mDrawerLayout.setDrawerOpen(open, animate)
    }

    private fun openSettingsActivity() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun restoreFragmentsState(savedInstanceState: Bundle) {
        val drawerListItemName = savedInstanceState.getString(
            KEY_CURRENT_DRAWER_LIST_ITEM, DrawerListItem.FONT_VIEWER.name
        ) ?: DrawerListItem.FONT_VIEWER.name
        currentDrawerListItem = try {
            DrawerListItem.valueOf(drawerListItemName)
        } catch (e: IllegalArgumentException) {
            DrawerListItem.FONT_VIEWER
        }
        if (mCurrentDrawerListItem == DrawerListItem.HOME || mCurrentDrawerListItem == DrawerListItem.FONT_VIEWER) {
            currentDrawerListItem = DrawerListItem.LOCAL_FONTS
        }

        val fm = supportFragmentManager
        for (drawerListItem in DrawerListItem.values()) {
            if (drawerListItem == DrawerListItem.HOME || drawerListItem == DrawerListItem.FONT_VIEWER) continue
            val f = fm.findFragmentByTag(drawerListItem.name)
            if (f != null) {
                mFragmentsMap[drawerListItem] = f
            }
        }

        mNavManager.showFragmentFast(mCurrentDrawerListItem)

        if (::mDrawerAdapter.isInitialized) {
            mDrawerAdapter.setSelectedItem(mCurrentDrawerListItem)
        }
    }

    private fun addAllFragments() {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()

        for ((drawerListItem, fragment) in mFragmentsMap) {
            transaction.add(R.id.main_content, fragment, drawerListItem.name)
            if (drawerListItem != mCurrentDrawerListItem) {
                transaction.hide(fragment)
            }
        }

        transaction.commitNow()
    }

    private fun warmUpOtherFragments() {
        val warmupHandler = Handler(mainLooper)
        warmupHandler.postDelayed({
            if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@postDelayed
            warmUpFragmentSilently(DrawerListItem.SYSTEM_FONTS)
            warmupHandler.postDelayed({
                if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@postDelayed
                warmUpFragmentSilently(DrawerListItem.FAVORITES)
                warmupHandler.postDelayed({
                    if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@postDelayed
                    warmUpFragmentSilently(DrawerListItem.TRASH)
                    warmupHandler.postDelayed({
                        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@postDelayed
                        warmUpFragmentSilently(null)
                    }, 120)
                }, 120)
            }, 120)
        }, 120)
    }

    private fun warmUpFragmentSilently(requestedItemToShow: DrawerListItem?) {
        // إن كانت الشاشة المراد تسخينها هي الشاشة الحالية نفسها فلا نسخّنها. يحدث هذا عند فتح التطبيق من إشعار التقدم
        // بعد حذفه من التطبيقات الأخيرة: handleIntent تنقل التطبيق مباشرة إلى شاشة العملية (المفضلة أو سلة المحذوفات)،
        // ثم يصل دورها في التسخين فتُجعل واجهتها INVISIBLE وهي معروضة. وبما أن show() لا يعيد الظهور لفرجمنت ظاهر أصلاً،
        // تبقى الواجهة غير مرئية (شاشة سوداء) حتى يخفيها التنقل لقائمة أخرى ثم يُظهرها من جديد.
        val drawerListItemToShow: DrawerListItem? =
            if (requestedItemToShow == mCurrentDrawerListItem) null else requestedItemToShow

        val transaction = supportFragmentManager.beginTransaction()

        for (s in DrawerListItem.values()) {
            if (s == mCurrentDrawerListItem) continue

            val frag = mFragmentsMap[s]
            if (frag == null || !frag.isAdded) continue

            if (s == drawerListItemToShow) {
                transaction.show(frag)
            } else {
                transaction.hide(frag)
            }
            frag.setMenuVisibility(false)
        }

        transaction.commitNow()

        if (drawerListItemToShow != null) {
            val warmedFrag = mFragmentsMap[drawerListItemToShow]
            warmedFrag?.view?.visibility = View.INVISIBLE
        }
    }

    private fun setupDrawer() {
        mDrawerListView.layoutManager = LinearLayoutManager(this)

        val drawerListItems: List<DrawerListItem?> = listOf(
            DrawerListItem.HOME,
            DrawerListItem.FONT_VIEWER,
            null,
            DrawerListItem.LOCAL_FONTS,
            DrawerListItem.SYSTEM_FONTS,
            DrawerListItem.FAVORITES,
            null,
            DrawerListItem.TRASH
        )

        mDrawerAdapter = DrawerListAdapter(
            this,
            drawerListItems,
            DrawerListAdapter.DrawerListener { drawerListItem ->
                if (drawerListItem == DrawerListItem.HOME) {
                    val intent = Intent(this@MainActivity, HomeActivity::class.java)
                    startActivity(intent)
                    return@DrawerListener false
                }

                if (drawerListItem == DrawerListItem.FONT_VIEWER) {
                    val intent = Intent(this@MainActivity, FontViewerActivity::class.java)
                    // إضافة هذه العلامة لإخبار الشاشة بأن الفتح تم من درج التنقل
                    intent.putExtra("is_from_drawer", true)
                    startActivity(intent)
                    return@DrawerListener false
                }

                setDrawerOpen(false, true)

                if (drawerListItem != mCurrentDrawerListItem) {
                    mNavManager.navigateFromDrawer(drawerListItem)
                    return@DrawerListener true
                }
                false
            })
        mDrawerListView.adapter = mDrawerAdapter

        mDrawerAdapter.setSelectedItem(mCurrentDrawerListItem)

        mNavManager.setup(mDrawerLayout)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        mDrawerListView.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0f
            private var startY = 0f

            override fun onTouch(v: View, ev: MotionEvent): Boolean {
                val action = ev.actionMasked
                if (action == MotionEvent.ACTION_DOWN) {
                    startX = ev.x
                    startY = ev.y
                    v.parent.requestDisallowInterceptTouchEvent(true)
                } else if (action == MotionEvent.ACTION_MOVE) {
                    val dx = Math.abs(ev.x - startX)
                    val dy = Math.abs(ev.y - startY)
                    if (dx > touchSlop && dx > dy) {
                        v.parent.requestDisallowInterceptTouchEvent(false)
                    } else {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
                return false
            }
        })
    }

    override fun updateDrawerTitle(drawerListItem: DrawerListItem) {
        if (mSearchCoordinator.isSearchExpanded && drawerListItem != DrawerListItem.FONT_VIEWER) {
            return
        }

        val title: String
        val subtitle: String

        when (drawerListItem) {
            DrawerListItem.HOME -> {
                title = getString(R.string.app_name)
                subtitle = getString(R.string.app_subtitle)
            }

            DrawerListItem.FONT_VIEWER -> {
                title = getString(R.string.drawer_font_viewer)
                subtitle = getString(R.string.font_viewer_select_description)
            }

            DrawerListItem.LOCAL_FONTS -> {
                title = getString(R.string.drawer_local_fonts)
                subtitle = getFontsCountString(mLocalFontsCount)
            }

            DrawerListItem.SYSTEM_FONTS -> {
                title = getString(R.string.drawer_system_fonts)
                subtitle = getFontsCountString(mSystemFontsCount)
            }

            DrawerListItem.FAVORITES -> {
                title = getString(R.string.drawer_favorites)
                subtitle = getFontsCountString(mFavoriteFontsCount)
            }

            DrawerListItem.TRASH -> {
                title = getString(R.string.drawer_trash)
                subtitle = getFontsCountString(mTrashFontsCount)
            }

            else -> {
                title = getString(R.string.app_name)
                subtitle = getString(R.string.app_subtitle)
            }
        }

        mDrawerLayout.setTitle(title)
        mDrawerLayout.setExpandedSubtitle(subtitle)
    }

    private fun getFontsCountString(count: Int): String {
        if (count == 0) {
            return getString(R.string.no_fonts_subtitle)
        }
        return resources.getQuantityString(R.plurals.font_count_subtitle, count, count)
    }

    fun updateFontsCount(drawerListItem: DrawerListItem, count: Int) {
        if (drawerListItem == DrawerListItem.LOCAL_FONTS) {
            mLocalFontsCount = count
        } else if (drawerListItem == DrawerListItem.SYSTEM_FONTS) {
            mSystemFontsCount = count
        } else if (drawerListItem == DrawerListItem.FAVORITES) {
            mFavoriteFontsCount = count
        } else if (drawerListItem == DrawerListItem.TRASH) {
            mTrashFontsCount = count
            if (mCurrentDrawerListItem == DrawerListItem.TRASH) {
                invalidateOptionsMenu()
            }
        }

        if (drawerListItem == mCurrentDrawerListItem && !mSearchCoordinator.isSearchExpanded) {
            runOnUiThread {
                mDrawerLayout.setExpandedSubtitle(getFontsCountString(count))
            }
        }
    }

    override fun onFontSelected(
        fontPath: String, realName: String?, fileName: String?,
        ttcIndex: Int, weightWidthLabel: String?
    ) {
        val isSystemFont = mCurrentDrawerListItem == DrawerListItem.SYSTEM_FONTS

        mSearchCoordinator.clearSearchFocus()

        // ★ تجهيز الخط وتحليل محاوره في خيط خلفي قبل فتح الشاشة، ليكون جاهزاً عند رسم أول إطار ولا يحجب الخيط الرئيسي ★
        FontPrefetcher.prefetch(this, fontPath, ttcIndex, isSystemFont)

        val intent = Intent(this, FontViewerActivity::class.java)
        intent.putExtra(FontViewerActivity.EXTRA_FONT_PATH, fontPath)
        intent.putExtra(FontViewerActivity.EXTRA_FONT_REAL_NAME, realName)
        intent.putExtra(FontViewerActivity.EXTRA_FONT_FILE_NAME, fileName)
        intent.putExtra(FontViewerActivity.EXTRA_TTC_INDEX, ttcIndex)
        intent.putExtra(FontViewerActivity.EXTRA_IS_SYSTEM_FONT, isSystemFont)
        intent.putExtra(FontViewerActivity.EXTRA_WEIGHT_WIDTH_LABEL, weightWidthLabel)
        startActivity(intent)
    }

    override val appFragmentManager: FragmentManager
        get() = supportFragmentManager

    override fun getFragment(drawerListItem: DrawerListItem?): Fragment? {
        return if (drawerListItem == null) null else mFragmentsMap[drawerListItem]
    }

    override val drawerLayout: OneUiDrawerLayout
        get() = mDrawerLayout

    override val drawerAdapter: DrawerListAdapter
        get() = mDrawerAdapter

    override val searchCoordinator: SearchCoordinator
        get() = mSearchCoordinator

    override fun performExit() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot) {
            finishAfterTransition()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_CURRENT_DRAWER_LIST_ITEM, mCurrentDrawerListItem.name)
        outState.putInt(KEY_LOCAL_FONTS_COUNT, mLocalFontsCount)
        outState.putInt(KEY_SYSTEM_FONTS_COUNT, mSystemFontsCount)
        outState.putInt(KEY_FAVORITE_FONTS_COUNT, mFavoriteFontsCount)
        outState.putInt(KEY_TRASH_FONTS_COUNT, mTrashFontsCount)
        mSearchCoordinator.saveState(outState)
    }

    override fun onDestroy() {
        mSearchCoordinator.cleanup()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        mNavManager.handleBackPressed()
    }

    companion object {
        private const val KEY_CURRENT_DRAWER_LIST_ITEM = "current_screen"
        private const val KEY_LOCAL_FONTS_COUNT = "local_fonts_count"
        private const val KEY_SYSTEM_FONTS_COUNT = "system_fonts_count"
        private const val KEY_FAVORITE_FONTS_COUNT = "favorite_fonts_count"
        private const val KEY_TRASH_FONTS_COUNT = "trash_fonts_count"

        private const val PERM_REQUEST_POST_NOTIFICATIONS = 1001

        const val EXTRA_TARGET_FRAGMENT = "target_fragment"
    }
}
