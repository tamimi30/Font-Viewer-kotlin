package com.oneui.fontviewer.ui.fontviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast

import androidx.appcompat.app.AlertDialog

import com.google.android.material.floatingactionbutton.FloatingActionButton

import dev.oneuiproject.oneui.dialog.ProgressDialog
import dev.oneuiproject.oneui.layout.ToolbarLayout

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.base.BaseActivity
import com.oneui.fontviewer.ui.dialog.FontInfoDialog
import com.oneui.fontviewer.ui.dialog.FontErrorDialog
import com.oneui.fontviewer.data.translation.TranslationRepository
import com.oneui.fontviewer.utils.FileUtils
import com.oneui.fontviewer.ui.utils.FormatBarAnimator
import com.oneui.fontviewer.ui.widget.drawable.TextDrawable

class FontViewerActivity : BaseActivity(), FontViewerFragment.OnFontChangedListener {

    companion object {
        const val EXTRA_FONT_PATH = "extra_font_path"
        const val EXTRA_FONT_REAL_NAME = "extra_font_real_name"
        const val EXTRA_FONT_FILE_NAME = "extra_font_file_name"
        const val EXTRA_TTC_INDEX = "extra_ttc_index"
        const val EXTRA_IS_SYSTEM_FONT = "extra_is_system_font"
        const val EXTRA_WEIGHT_WIDTH_LABEL = "extra_weight_width_label"

        private const val MIN_LOADING_DIALOG_MS = 500L
    }

    private var mToolbarLayout: ToolbarLayout? = null
    private var mFontViewerFragment: FontViewerFragment? = null

    private var fabFontSize: FloatingActionButton? = null
    private var formatBar: View? = null

    // NOTE(kotlin-migration): Java getters getBtnBold()/getBtnItalic() became read-only properties (convention 1.4).
    var btnBold: View? = null
        private set
    var btnItalic: View? = null
        private set

    // أيقونتا bold/italic نفسهما (ImageView) بداخل حاوياتهما، نحتاجهما منفصلتين عن الحاويات
    // لتمريرهما إلى FormatBarAnimator كي يطبّق عليهما أنيميشن iconScale المتأخر فقط.
    private var iconBold: View? = null
    private var iconItalic: View? = null

    private var currentFontRealName: String? = null
    private var currentFontFileName: String? = null

    private var loadingDialog: ProgressDialog? = null
    private var loadingDialogShownAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // التحقق مما إذا كان الفتح من درج التنقل
        val isFromDrawer = intent.getBooleanExtra("is_from_drawer", false)

        // تشغيل الأنيميشن فقط إذا لم يكن الفتح من درج التنقل
        if (!isFromDrawer) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                overridePendingTransition(
                    R.anim.sesl_fragment_open_enter,
                    R.anim.sesl_fragment_open_exit,
                    getColor(R.color.oui_round_and_bgcolor)
                )
            } else {
                overridePendingTransition(R.anim.sesl_fragment_open_enter, R.anim.sesl_fragment_open_exit)
            }
        }

        setContentView(R.layout.activity_font_viewer)

        mToolbarLayout = findViewById<ToolbarLayout>(R.id.toolbar_layout)
        fabFontSize = findViewById<FloatingActionButton>(R.id.fab_font_size)
        formatBar = findViewById<View>(R.id.format_bar)
        btnBold = findViewById<View>(R.id.btn_bold_container)
        btnItalic = findViewById<View>(R.id.btn_italic_container)
        iconBold = findViewById<View>(R.id.btn_bold)
        iconItalic = findViewById<View>(R.id.btn_italic)

        fabFontSize?.visibility = View.INVISIBLE
        formatBar?.visibility = View.INVISIBLE

        mToolbarLayout?.let { toolbarLayout ->
            toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description))
            toolbarLayout.setNavigationButtonOnClickListener { onBackPressed() }
        }

        if (savedInstanceState == null) {
            val fragment = FontViewerFragment()
            mFontViewerFragment = fragment
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.font_viewer_container, fragment)
                .commitNow()
            loadFontFromIntent(intent)
        } else {
            mFontViewerFragment = supportFragmentManager
                .findFragmentById(R.id.font_viewer_container) as FontViewerFragment?
        }

        Handler(Looper.getMainLooper()).postDelayed({ setupFab() }, 400)
    }

    private fun setupFab() {
        val fab = fabFontSize
        if (fab != null) {
            // FabStyle يضبط showMotionSpec على mtrl_fab_show_motion_spec، لذلك show() تشغّلها تلقائيًا.
            fab.show()
            fab.setOnClickListener {
                mFontViewerFragment?.showFontSizeDialogPublic()
            }
        }
        val bar = formatBar
        if (bar != null) {
            // منطق إعادة بناء حركة ظهور الـ FAB (opacity + scale + iconScale المتأخر) انتقل
            // بالكامل إلى FormatBarAnimator لإعادة استخدامه ولتقليل حجم هذا الملف.
            // NOTE(kotlin-migration): null icons are filtered out (Java FormatBarAnimator skips null icons).
            FormatBarAnimator.showWithFabMotion(this, bar, *listOfNotNull(iconBold, iconItalic).toTypedArray())
        }
    }

    private fun loadFontFromIntent(intent: Intent?) {
        val fragment = mFontViewerFragment
        if (intent == null || fragment == null) return

        val path = intent.getStringExtra(EXTRA_FONT_PATH)
        if (path == null || path.isEmpty()) return

        if (path.startsWith("content://")) {
            val fileName = intent.getStringExtra(EXTRA_FONT_FILE_NAME)
            fragment.loadFontFromUri(Uri.parse(path), fileName)
        } else {
            val realName = intent.getStringExtra(EXTRA_FONT_REAL_NAME)
            val fileName = intent.getStringExtra(EXTRA_FONT_FILE_NAME)
            val ttcIndex = intent.getIntExtra(EXTRA_TTC_INDEX, 0)
            val isSystemFont = intent.getBooleanExtra(EXTRA_IS_SYSTEM_FONT, false)
            val weightWidthLabel = intent.getStringExtra(EXTRA_WEIGHT_WIDTH_LABEL)
            fragment.loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont, weightWidthLabel)
        }
    }

    fun updateFabFontSizeText(size: Float) {
        val fab = fabFontSize
        if (fab != null) {
            val textColor = getColor(R.color.drawer_icon_color)
            val sizeText = Math.round(size).toString()
            val fabTextSizeDp = if (sizeText.length >= 3) 19f else 24f
            fab.setImageDrawable(
                TextDrawable(
                    this,
                    sizeText,
                    fabTextSizeDp,
                    textColor
                )
            )
        }
    }

    override fun onFontChanged(fontRealName: String?, fontFileName: String?) {
        this.currentFontRealName = fontRealName
        this.currentFontFileName = fontFileName
        updateTitle()
    }

    override fun onFontCleared() {
        this.currentFontRealName = null
        this.currentFontFileName = null
        updateTitle()
    }

    private fun updateTitle() {
        val toolbarLayout = mToolbarLayout ?: return

        val realName = currentFontRealName
        val fileName = currentFontFileName

        val title: String = if (realName != null && realName.isNotEmpty()) {
            realName
        } else if (fileName != null && fileName.isNotEmpty()) {
            getString(R.string.unknown_font)
        } else {
            getString(R.string.drawer_font_viewer)
        }

        val subtitle: String = if (fileName != null && fileName.isNotEmpty()) {
            FileUtils.removeExtension(fileName)
        } else {
            getString(R.string.font_viewer_select_description)
        }

        toolbarLayout.setTitle(title)
        toolbarLayout.setExpandedSubtitle(subtitle)
    }

    fun showFontMetaFromFragment() {
        val fragment = mFontViewerFragment
        if (fragment == null || !fragment.hasFontSelected()) {
            showNoFontDialog()
            return
        }

        val meta: Map<String, String>? = fragment.fontMetadata

        val realName = currentFontRealName
        if (realName == null || realName.isEmpty() || meta == null || meta.isEmpty()) {
            showFontInfoDialog(meta)
            return
        }

        val translationRepository = TranslationRepository(this)
        if (translationRepository.isTranslationEnabled) {
            val isFinished = booleanArrayOf(false)

            Handler(Looper.getMainLooper()).postDelayed({
                if (!isFinished[0]) showLoadingDialog()
            }, 250)

            translationRepository.translateMetadata(meta, object : TranslationRepository.TranslationCallback {
                override fun onTranslationComplete(translatedData: Map<String, String>) {
                    runOnUiThread {
                        isFinished[0] = true
                        dismissLoadingDialogThenRun { showFontInfoDialog(translatedData) }
                    }
                }

                override fun onTranslationFailed(error: String?) {
                    runOnUiThread {
                        isFinished[0] = true
                        dismissLoadingDialogThenRun {
                            if ("NO_INTERNET" == error) {
                                Toast.makeText(
                                    this@FontViewerActivity,
                                    R.string.toast_no_internet_connection,
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if ("API_ERROR" == error) {
                                Toast.makeText(
                                    this@FontViewerActivity,
                                    R.string.toast_translation_api_error,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            showFontInfoDialog(meta)
                        }
                    }
                }
            })
        } else {
            showFontInfoDialog(meta)
        }
    }

    private fun showFontInfoDialog(metadata: Map<String, String>?) {
        val fragment = mFontViewerFragment ?: return

        val realName = currentFontRealName
        if (realName == null || realName.isEmpty()
            || metadata == null || metadata.isEmpty()
        ) {
            FontErrorDialog.show(this)
            return
        }

        val fileName = fragment.currentFontFileName
        val path = fragment.originalFontPath

        val dialog = FontInfoDialog(this, metadata, fileName, path)
        dialog.show()
    }

    private fun showNoFontDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.font_viewer_select_font))
            .setMessage(getString(R.string.font_viewer_no_font_selected))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showLoadingDialog() {
        dismissLoadingDialog()
        try {
            val dialog = ProgressDialog(this)
            loadingDialog = dialog
            dialog.setMessage(getString(R.string.translating))
            dialog.setCancelable(false)
            dialog.show()
            loadingDialogShownAt = System.currentTimeMillis()
        } catch (ignored: Exception) {
        }
    }

    private fun dismissLoadingDialog() {
        val dialog = loadingDialog
        if (dialog != null && dialog.isShowing) {
            try {
                dialog.dismiss()
            } catch (ignored: Exception) {
            }
            loadingDialog = null
        }
    }

    private fun dismissLoadingDialogThenRun(action: () -> Unit) {
        val dialog = loadingDialog
        if (dialog != null && dialog.isShowing) {
            val elapsed = System.currentTimeMillis() - loadingDialogShownAt
            val remaining = MIN_LOADING_DIALOG_MS - elapsed
            if (remaining > 0) {
                Handler(Looper.getMainLooper()).postDelayed({
                    dismissLoadingDialog()
                    action()
                }, remaining)
                return
            }
        }
        dismissLoadingDialog()
        action()
    }

    override fun finish() {
        super.finish()

        // التحقق مما إذا كان الفتح من درج التنقل
        val isFromDrawer = intent.getBooleanExtra("is_from_drawer", false)

        // تشغيل أنيميشن الخروج فقط إذا لم يكن الفتح من درج التنقل
        if (!isFromDrawer) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                overridePendingTransition(
                    R.anim.sesl_fragment_close_enter,
                    R.anim.sesl_fragment_close_exit,
                    getColor(R.color.oui_round_and_bgcolor)
                )
            } else {
                overridePendingTransition(R.anim.sesl_fragment_close_enter, R.anim.sesl_fragment_close_exit)
            }
        }
    }

    override fun onDestroy() {
        dismissLoadingDialog()
        super.onDestroy()
    }
}
