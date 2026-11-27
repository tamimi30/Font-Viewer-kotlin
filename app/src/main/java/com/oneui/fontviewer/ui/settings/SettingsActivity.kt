package com.oneui.fontviewer.ui.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.base.BaseActivity
import com.oneui.fontviewer.data.settings.SettingsRepository
import com.oneui.fontviewer.data.update.UpdateRepository
import com.oneui.fontviewer.ui.utils.TransitionUtils

import dev.oneuiproject.oneui.layout.ToolbarLayout

class SettingsActivity : BaseActivity() {

    private var mToolbarLayout: ToolbarLayout? = null

    // اللغة التي تعرضها هذه الشاشة حالياً
    // نخزّن الوسم الكامل (مثل ar أو ar-MA) وليس رمز اللغة فقط، لأن مقارنة رمز اللغة وحده تعتبر
    // ar و ar-MA لغة واحدة فلا تُعاد بناء الشاشة عند التبديل بينهما
    private var mDisplayedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // فتح الإعدادات من جديد يبدأ جلسة جديدة: تعود بطاقة التحديث إذا كان المستخدم أخفاها بـ "ليس الآن"،
        // ويعود مؤشر التحميل في صفحة "حول التطبيق" عند أول فتح لها. إعادة البناء بسبب تغيير اللغة أو الثيم
        // (savedInstanceState != null) لا تنهي الجلسة. يجب أن يتم هذا قبل إنشاء SettingsFragment.
        if (savedInstanceState == null) {
            UpdateRepository.getInstance(this).beginSettingsSession()
        }

        mDisplayedLanguage = resources.configuration.locales[0].toLanguageTag()
        setContentView(R.layout.activity_settings)

        initToolbar()

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    private fun initToolbar() {
        val toolbarLayout = findViewById<ToolbarLayout?>(R.id.toolbar_layout)
        mToolbarLayout = toolbarLayout
        if (toolbarLayout != null) {
            toolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description))
            toolbarLayout.setNavigationButtonOnClickListener { finish() }
        }
    }


    /**
     * locale|layoutDirection في configChanges (المانيفيست) يمنع النظام من إعادة بناء هذه الشاشة
     * بنفسه (وهو ما كان يسبب الشاشة السوداء)، فنعيد بناءها نحن عند تغيّر اللغة.
     * اتجاه الواجهة بعد إعادة البناء يضمنه android:layoutDirection="locale" في activity_settings.xml.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (SettingsRepository.getLocale(this).toLanguageTag() != mDisplayedLanguage) {
            // بين وصول التغيير وإعادة بناء الشاشة (~150ms) تتفاعل عناصر الشاشة الحيّة القديمة معه
            // (عناوين الأقسام تقفز، الـ App bar ينطوي ويتوسع...)، فنغطيها بصورة اللقطة حتى تبدأ
            // الشاشة الجديدة.
            TransitionUtils.freezeScreen(this)
            recreate()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        super.onBackPressed()
    }
}
