package com.oneui.fontviewer.ui.license

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.MenuItem
import android.widget.TextView

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.base.BaseActivity

import java.io.BufferedReader
import java.io.InputStreamReader

import dev.oneuiproject.oneui.layout.ToolbarLayout

class LicensesActivity : BaseActivity() {

    private var mToolbarLayout: ToolbarLayout? = null
    private var mLicenseTextView: TextView? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_licenses)

        initViews()
        loadLicenseText()
    }

    @Suppress("DEPRECATION")
    private fun initViews() {
        val toolbarLayout = findViewById<ToolbarLayout?>(R.id.toolbar_layout)
        val licenseTextView = findViewById<TextView?>(R.id.license_text_view)
        mToolbarLayout = toolbarLayout
        mLicenseTextView = licenseTextView

        if (toolbarLayout != null) {
            toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description))
            toolbarLayout.setNavigationButtonOnClickListener { onBackPressed() }
        }

        // تفعيل استجابة الروابط عند الضغط عليها
        if (licenseTextView != null) {
            licenseTextView.movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun loadLicenseText() {
        val licenseTextView = mLicenseTextView ?: return

        val text = StringBuilder()
        try {
            // قراءة الملف من مجلد raw
            val `is` = resources.openRawResource(R.raw.license)
            val br = BufferedReader(InputStreamReader(`is`, "UTF-8"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                text.append(line).append("\n")
            }
            br.close()
            `is`.close()

            licenseTextView.text = text.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading license file", e)
            licenseTextView.text = "Error loading licenses."
        }
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        private const val TAG = "LicensesActivity"
    }
}
