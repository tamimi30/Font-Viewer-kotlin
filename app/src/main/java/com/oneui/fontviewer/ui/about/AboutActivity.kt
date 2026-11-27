package com.oneui.fontviewer.ui.about

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View

import androidx.appcompat.app.AppCompatActivity

import dev.oneuiproject.oneui.layout.AppInfoLayout
import dev.oneuiproject.oneui.widget.Toast

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.update.UpdateRepository
import com.oneui.fontviewer.data.update.model.UpdateCheckState
import com.oneui.fontviewer.ui.license.LicensesActivity
import com.oneui.fontviewer.ui.utils.UpdateLauncher
import com.oneui.fontviewer.utils.update.AppLinks

class AboutActivity : AppCompatActivity() {

    private lateinit var appInfoLayout: AppInfoLayout
    private lateinit var updateRepository: UpdateRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        appInfoLayout = findViewById(R.id.appInfoLayout)

        setButtonsWidth(
            0.61f,
            R.id.button_github,
            R.id.button_licenses,
            R.id.app_info_update
        )

        // لإضافة نص إضافي تحت رقم الإصدار استخدم:
        // appInfoLayout.addOptionalText("النص هنا");
        // (حُذف النصان التجريبيان Extra 1 و Extra 2 فقط، والمكتبة ما زالت تدعم النصوص الإضافية)

        updateRepository = UpdateRepository.getInstance(this)

        appInfoLayout.setMainButtonClickListener(object : AppInfoLayout.OnClickListener {
            override fun onUpdateClicked(v: View) {
                // نفس تطبيقات سامسونج: ننتقل إلى المتجر ونغلق صفحة "حول التطبيق"
                val opened = UpdateLauncher.open(
                    this@AboutActivity, updateRepository.availableUpdateNow
                )
                if (opened) {
                    finish()
                } else {
                    Toast.makeText(
                        this@AboutActivity,
                        R.string.update_open_store_failed, Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onRetryClicked(v: View) {
                updateRepository.retryCheck()
            }
        })

        // يعرض مؤشر التحميل ثم النتيجة. أول فتح للصفحة في جلسة الإعدادات يتحقق من التحديث،
        // والفتح التالي في نفس الجلسة يعرض النتيجة المحفوظة فوراً بدون مؤشر تحميل.
        updateRepository.checkState.observe(this) { state -> showCheckState(state) }
        updateRepository.requestAboutPageCheck()
    }

    private fun showCheckState(state: UpdateCheckState) {
        when (state) {
            UpdateCheckState.UPDATE_AVAILABLE ->
                appInfoLayout.setStatus(AppInfoLayout.UPDATE_AVAILABLE)
            UpdateCheckState.UP_TO_DATE ->
                appInfoLayout.setStatus(AppInfoLayout.NO_UPDATE)
            UpdateCheckState.NO_CONNECTION ->
                appInfoLayout.setStatus(AppInfoLayout.NO_CONNECTION)
            UpdateCheckState.CHECKING,
            UpdateCheckState.IDLE ->
                appInfoLayout.setStatus(AppInfoLayout.LOADING)
        }
    }

    private fun setButtonsWidth(ratio: Float, vararg buttonIds: Int) {
        val res = resources
        val w = res.displayMetrics.widthPixels / res.configuration.orientation
        val width = (w * ratio).toInt()

        for (id in buttonIds) {
            val button = findViewById<View?>(id)
            if (button != null) {
                val lp = button.layoutParams
                lp.width = width
                button.layoutParams = lp
            }
        }
    }

    fun openGithubPage(v: View) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_PROFILE_URL))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app can open the GitHub page: " + e.message)
        }
    }

    fun openLicensesPage(v: View) {
        val intent = Intent(this, LicensesActivity::class.java)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "AboutActivity"
    }
}
