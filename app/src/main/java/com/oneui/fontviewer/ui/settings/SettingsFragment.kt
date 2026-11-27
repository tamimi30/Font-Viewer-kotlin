package com.oneui.fontviewer.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.preference.EditTextPreference
import androidx.preference.DropDownPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

import dev.oneuiproject.oneui.preference.HorizontalRadioPreference
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard
import dev.oneuiproject.oneui.utils.PreferenceUtils

import com.oneui.fontviewer.R
import com.oneui.fontviewer.FontViewerApp
import com.oneui.fontviewer.ui.about.AboutActivity
import com.oneui.fontviewer.ui.settings.update.UpdateCardController
import com.oneui.fontviewer.data.settings.SettingsRepository
import com.oneui.fontviewer.ui.utils.TransitionUtils

class SettingsFragment : PreferenceFragmentCompat() {

    private var mContext: Context? = null
    private lateinit var viewModel: SettingsViewModel

    private var languagePreference: DropDownPreference? = null

    // لمزامنة أول قيمة تصل من DataStore مع لغة النظام الفعلية (مرة واحدة فقط)
    private var mLanguageSyncedAfterLoad = false
    private var themePreference: HorizontalRadioPreference? = null
    private var themeAutoPreference: SwitchPreferenceCompat? = null

    private var fontPreviewPreference: SwitchPreferenceCompat? = null
    private var translationPreference: SwitchPreferenceCompat? = null
    private var previewTextPreference: EditTextPreference? = null
    private var reportIssuePreference: Preference? = null

    private var mRelatedCard: PreferenceRelatedCard? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        mContext = context
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // بطاقة التحديث (أعلى القائمة) تُحذف هنا إذا لم يتوفر تحديث، لنفس سبب بطاقة الروابط أدناه:
        // أي تغيير في عناصر القائمة يجب أن يحدث قبل ربطها بالمحوّل وليس بعده.
        UpdateCardController(this).attach()

        // بطاقة الروابط تُضاف هنا (قبل ربط القائمة بالمحوّل) وليس في onStart أو onResume. إضافتها بعد الربط
        // تجعل المحوّل يضيفها لاحقاً في رسالة مؤجلة، أي بعد أول رسم للقائمة، فتطول القائمة بعد أن استعاد
        // RecyclerView موضع التمرير مرة واحدة، فيضيع الموضع عندما تكون القائمة قصيرة (حجم خط صغير).
        setupRelatedCard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(SettingsViewModel::class.java)

        initPreferences()
        setupPreferenceListeners()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        syncLanguageFromSystem()
    }

    /**
     * يزامن DataStore وقائمة اللغة مع لغة التطبيق الفعلية في النظام (من أندرويد 13).
     *
     * لغة التطبيق قد تتغير من إعدادات النظام دون علم DataStore، فنقارن قيمة DataStore نفسها
     * (وليس قيمة الـ preference، لأنها تبدأ بالقيمة الافتراضية "0" وهي نفس System default،
     * فتبدو متطابقة مع النظام قبل أن تصل قيمة DataStore القديمة وتُعرض خطأً).
     */
    private fun syncLanguageFromSystem() {
        val preference = languagePreference
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || preference == null) {
            return
        }

        val dataStoreLang: Int? = viewModel.languageMode.value
        if (dataStoreLang == null) {
            // لم تصل القيمة من DataStore بعد، سيستدعي المراقب هذه الدالة عند وصولها
            return
        }

        val actualLang = SettingsRepository.getSystemAssignedLanguage(requireContext())
        if (dataStoreLang != actualLang) {
            preference.value = actualLang.toString()
            viewModel.syncLanguageModeFromSystem(actualLang)
        }
    }


    private fun initPreferences() {

        languagePreference = findPreference<DropDownPreference>("language_mode")
        languagePreference?.let { preference ->
            preference.seslSetSummaryColor(getColoredSummaryColor(true))
            preference.setOnPreferenceChangeListener { _, newValue ->
                val mode = Integer.parseInt(newValue as String)
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({
                        // التقاط صورة للشاشة باللغة الحالية قبل تغييرها (لأنيميشن التغيير)
                        TransitionUtils.prepareForLanguageChange(activity)
                        viewModel.setLanguageMode(mode)
                    }, 250)
                true
            }
        }

        themePreference = findPreference<HorizontalRadioPreference>("theme_mode")
        themePreference?.let { preference ->
            preference.setDividerEnabled(false)
            preference.setTouchEffectEnabled(false)
        }

        themeAutoPreference = findPreference<SwitchPreferenceCompat>("theme_auto")


        fontPreviewPreference = findPreference<SwitchPreferenceCompat>("font_preview_enabled")
        fontPreviewPreference?.let { preference ->
            preference.seslSetSummaryColor(getColoredSummaryColor(false))
            preference.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                viewModel.setFontPreviewEnabled(enabled)
                true
            }
        }

        translationPreference = findPreference<SwitchPreferenceCompat>("enable_translation")
        translationPreference?.let { preference ->
            preference.seslSetSummaryColor(getColoredSummaryColor(false))
            preference.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                viewModel.setTranslationEnabled(enabled)
                true
            }
        }

        previewTextPreference = findPreference<EditTextPreference>("preview_text")
        previewTextPreference?.let { preference ->
            preference.seslSetSummaryColor(getColoredSummaryColor(true))
            preference.setOnPreferenceChangeListener { _, newValue ->
                val text = newValue as String
                viewModel.setPreviewText(text)
                true
            }
        }

        val aboutPreference = findPreference<Preference>("about_app")
        aboutPreference?.setOnPreferenceClickListener {
            val intent = Intent(mContext, AboutActivity::class.java)
            startActivity(intent)
            true
        }

        reportIssuePreference = findPreference<Preference>("report_issue")
        reportIssuePreference?.setOnPreferenceClickListener {
            val context = mContext!!
            val emailIntent = Intent(Intent.ACTION_SENDTO)
            emailIntent.data = Uri.parse(
                "mailto:" + context.getString(R.string.feedback_email)
            )
            emailIntent.putExtra(
                Intent.EXTRA_SUBJECT,
                context.getString(R.string.settings_report_issue_email_subject)
            )
            try {
                startActivity(Intent.createChooser(emailIntent, null))
            } catch (e: ActivityNotFoundException) {
            }
            true
        }
    }

    private fun setupPreferenceListeners() {
        themePreference?.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as String
            val mode = Integer.parseInt(value)

            // التقاط صورة للثيم الحالي قبل تغييره (لأنيميشن تغيير الثيم)
            TransitionUtils.prepareForThemeChange(requireActivity())

            if (mode == SettingsRepository.THEME_DARK) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }

            viewModel.setThemeMode(mode)

            true
        }

        themeAutoPreference?.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean

            themePreference?.isEnabled = !enabled

            // تأخير تطبيق الثيم حتى ينهي الـ switch حركته.
            // ملاحظة: viewModel.setThemeAuto() يستدعي applyTheme() بنفسه، لذلك يجب أن يكون
            // داخل التأخير أيضاً وإلا سيُطبَّق الثيم فوراً.
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                // التقاط صورة للثيم الحالي قبل تغييره (لأنيميشن تغيير الثيم)
                TransitionUtils.prepareForThemeChange(activity)

                if (enabled) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                } else {
                    val savedMode = SettingsRepository.getThemeMode(mContext!!)
                    if (savedMode == SettingsRepository.THEME_DARK) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                    }
                }

                viewModel.setThemeAuto(enabled)
            }, THEME_AUTO_SWITCH_DELAY_MS)

            true
        }
    }

    private fun observeViewModel() {
        viewModel.languageMode.observe(this) { mode ->
            val preference = languagePreference
            if (preference != null && mode != null) {
                preference.value = mode.toString()

                // أول قيمة تصل قد تكون قديمة (اللغة تغيّرت من إعدادات النظام)، نصححها مرة واحدة.
                // لا نكررها بعد ذلك حتى لا نتعارض مع تغيير اللغة من هذه الشاشة.
                if (!mLanguageSyncedAfterLoad) {
                    mLanguageSyncedAfterLoad = true
                    syncLanguageFromSystem()
                }
            }
        }

        viewModel.themeMode.observe(this) { mode ->
            val preference = themePreference
            if (preference != null && mode != null) {
                preference.value = mode.toString()
            }
        }

        viewModel.themeAuto.observe(this) { enabled ->
            val preference = themeAutoPreference
            if (preference != null && enabled != null) {
                preference.isChecked = enabled

                themePreference?.isEnabled = !enabled
            }
        }


        viewModel.fontPreviewEnabled.observe(this) { enabled ->
            val preference = fontPreviewPreference
            if (preference != null && enabled != null) {
                preference.isChecked = enabled
            }
        }

        viewModel.translationEnabled.observe(this) { enabled ->
            val preference = translationPreference
            if (preference != null && enabled != null) {
                preference.isChecked = enabled
            }
        }

        viewModel.previewText.observe(this) { text ->
            val preference = previewTextPreference
            if (preference != null && text != null) {
                preference.text = text
            }
        }

        viewModel.settingsEvent.observe(this) { event ->
            if (event != null && event.contentIfNotHandled) {
                handleSettingsEvent(event)
            }
        }
    }

    private fun handleSettingsEvent(event: SettingsViewModel.SettingsEvent) {
        val context = mContext!!
        when (event.type) {
            SettingsViewModel.SettingsEventType.SHOW_TOAST -> {
                val message = event.message
                if (event.messageResId != 0) {
                    Toast.makeText(context, event.messageResId, Toast.LENGTH_SHORT).show()
                } else if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            SettingsViewModel.SettingsEventType.RECREATE_ACTIVITY -> {
                if (activity != null) {
                    requireActivity().recreate()
                }
            }

            SettingsViewModel.SettingsEventType.RECREATE_ALL_ACTIVITIES -> {
                val app: FontViewerApp? = FontViewerApp.getInstance()
                if (app != null) {
                    app.recreateAllActivities()
                } else if (activity != null) {
                    requireActivity().recreate()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireView().setBackgroundColor(mContext!!.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color))
        listView.seslSetLastRoundedCorner(false)
    }

    private fun setupRelatedCard() {
        if (mRelatedCard == null) {
            val card = PreferenceUtils.createRelatedCard(requireContext())
            mRelatedCard = card

            updateRelatedCardText(mContext!!)

            card?.show(this)
        }
    }

    private fun updateRelatedCardText(context: Context) {
        val card = mRelatedCard
        if (card != null) {
            card.setTitleText(context.getString(R.string.oui_relative_description))

            card.removeCardButtons()

            card.addButton(context.getString(R.string.share_app)) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(
                    Intent.EXTRA_TEXT,
                    "https://play.google.com/store/apps/details?id="
                            + context.packageName
                )
                startActivity(Intent.createChooser(shareIntent, null))
            }
                .addButton(context.getString(R.string.rate_app)) {
                    try {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=" + context.packageName)
                            )
                        )
                    } catch (e: ActivityNotFoundException) {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(
                                    "https://play.google.com/store/apps/details?id="
                                            + context.packageName
                                )
                            )
                        )
                    }
                }
        }
    }

    private fun getColoredSummaryColor(enabled: Boolean): ColorStateList {
        val context = mContext!!
        if (enabled) {
            val colorPrimaryDark = TypedValue()
            context.theme.resolveAttribute(
                dev.oneuiproject.oneui.design.R.attr.colorPrimaryDark, colorPrimaryDark, true
            )

            val states = arrayOf(
                intArrayOf(android.R.attr.state_enabled),
                intArrayOf(-android.R.attr.state_enabled)
            )
            val colors = intArrayOf(
                Color.argb(
                    0xff,
                    Color.red(colorPrimaryDark.data),
                    Color.green(colorPrimaryDark.data),
                    Color.blue(colorPrimaryDark.data)
                ),
                Color.argb(
                    0x4d,
                    Color.red(colorPrimaryDark.data),
                    Color.green(colorPrimaryDark.data),
                    Color.blue(colorPrimaryDark.data)
                )
            )
            return ColorStateList(states, colors)
        } else {
            val outValue = TypedValue()
            context.theme.resolveAttribute(
                dev.oneuiproject.oneui.design.R.attr.isLightTheme, outValue, true
            )
            return context.getColorStateList(
                if (outValue.data != 0)
                    dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_light
                else
                    dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_dark
            )
        }
    }

    companion object {
        // مدة التأخير (بالمللي ثانية) قبل تطبيق الثيم عند الضغط على switch "ثيم النظام"،
        // حتى ينهي الـ switch حركته أولاً. غيّر القيمة لزيادة أو تقليل التأخير.
        private const val THEME_AUTO_SWITCH_DELAY_MS = 250L
    }
}
