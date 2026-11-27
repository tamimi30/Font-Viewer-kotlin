package com.oneui.fontviewer.ui.fontviewer

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import com.oneui.fontviewer.R
import com.oneui.fontviewer.data.common.metadata.FontMetadataExtractor
import com.oneui.fontviewer.data.common.metadata.FontWeightWidthExtractor
import com.oneui.fontviewer.data.common.variable.VariableFontAxisExtractor
import com.oneui.fontviewer.data.common.variable.VariableTypefaceFactory
import com.oneui.fontviewer.data.fontviewer.FontPrefetcher
import com.oneui.fontviewer.data.fontviewer.FontViewerPreferenceManager
import com.oneui.fontviewer.data.fontviewer.FontViewerStorageManager
import com.oneui.fontviewer.data.settings.SettingsRepository
import com.oneui.fontviewer.data.systemfont.SystemFontCache
import com.oneui.fontviewer.ui.dialog.AxisInfoDialog
import com.oneui.fontviewer.ui.dialog.FontSizeDialog
import com.oneui.fontviewer.ui.fontviewer.utils.BoldItalicFormatting
import com.oneui.fontviewer.ui.settings.SettingsViewModel

class FontViewerFragment : Fragment() {

    private var previewSentence: TextView? = null
    private var weightLabelText: TextView? = null

    // ★ بيانات خط جاهزة مسبقاً (FontPrefetcher) طُبِّقت قبل إنشاء الواجهة، وتنتظر إنشاءها لتُعرض (انظر applyLoadedFontUi) ★
    private var prefetchedUiPending = false
    private var prefetchedAxisInstancesMap: Map<String, List<VariableFontAxisExtractor.VariableInstance>>? = null

    private var variableAxesContainer: View? = null
    private var weightAxisUi: AxisSpinnerUi? = null
    private var widthAxisUi: AxisSpinnerUi? = null

    // محور الميل (ital) ثنائي القيمة، لذلك يُعرض بمفتاح SwitchCompat بدل Spinner (ضغطة واحدة بدل ضغطتين)
    private var italicAxisUi: AxisSwitchUi? = null
    private var gradeAxisUi: AxisSpinnerUi? = null
    private var roundnessAxisUi: AxisSpinnerUi? = null
    private var monoAxisUi: AxisSpinnerUi? = null

    // قائمة المحاور التي تُعرض بـ Spinner فقط (محور الميل مستثنى لأنه يُعرض بمفتاح)
    private var allAxisUis: MutableList<AxisSpinnerUi?>? = null

    private var currentFontPath: String? = null
    var currentFontFileName: String? = null
        private set
    private var currentFontRealName: String? = null
    var originalFontPath: String? = null
    private var currentTypeface: Typeface? = null
    private var currentFontSize: Float = DEFAULT_FONT_SIZE
    private var isVariableFont = false
    private var currentTtcIndex = 0
    private var isSystemFont = false

    // القيم الحالية المُطبّقة فعلياً لكل محور مدعوم (المفتاح هو وسم المحور مثل wght أو GRAD)
    private val currentAxisValues: MutableMap<String, Float> = LinkedHashMap()

    // أنيميشن منفصل لكل محور، حتى يمكن تحريك أكثر من محور في آنٍ واحد دون أن يتعارض أحدهما مع الآخر
    private val axisAnimators: MutableMap<String, ValueAnimator> = HashMap()

    private var currentWeightWidthLabel: String? = null

    private var fontChangedListener: OnFontChangedListener? = null

    private val bgExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    private lateinit var storageManager: FontViewerStorageManager
    private lateinit var preferenceManager: FontViewerPreferenceManager
    private lateinit var settingsViewModel: SettingsViewModel
    private val formattingHelper = BoldItalicFormatting()

    /**
     * تمثل ربط عنصر واجهة واحد بمحور من محاور الخط المتغير (الحاوية + Spinner).
     * الحاوية نفسها هي جذر vf_axis_spinner_item.xml المنفوخ لهذا المحور.
     */
    private class AxisSpinnerUi(
        val tag: String,
        val container: View,
        val spinner: AppCompatSpinner?
    ) {
        var instances: List<VariableFontAxisExtractor.VariableInstance> = ArrayList()
    }

    /**
     * تمثل ربط عنصر واجهة واحد بمحور ثنائي القيمة (الحاوية + SwitchCompat).
     * الحاوية نفسها هي جذر vf_axis_switch_item.xml المنفوخ لهذا المحور.
     */
    private class AxisSwitchUi(
        val tag: String,
        val container: View,
        val switchView: SwitchCompat?
    )

    interface OnFontChangedListener {
        fun onFontChanged(fontRealName: String?, fontFileName: String?)
        fun onFontCleared()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFontChangedListener) {
            fontChangedListener = context
        }

        storageManager = FontViewerStorageManager(context)
        preferenceManager = FontViewerPreferenceManager(context)
    }

    override fun onDetach() {
        super.onDetach()
        fontChangedListener = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        setHasOptionsMenu(true)

        settingsViewModel = ViewModelProvider(requireActivity()).get(SettingsViewModel::class.java)

        currentFontSize = if (sSessionFontSize > 0f) sSessionFontSize else DEFAULT_FONT_SIZE
    }

    @Suppress("DEPRECATION")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_main_font_meta, menu)

        super.onCreateOptionsMenu(menu, inflater)
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_font_meta) {
            val act = activity
            if (act is FontViewerActivity) {
                act.showFontMetaFromFragment()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)

        setMenuVisibility(!hidden)
        if (!hidden && activity != null) {
            activity?.invalidateOptionsMenu()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_font_viewer, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews(view)

        // ★ اذا وصلت بيانات الخط الجاهزة مسبقاً قبل إنشاء الواجهة، نعرضها الآن قبل رسم الإطار الأول ★
        if (prefetchedUiPending) {
            applyLoadedFontUi()
        }

        settingsViewModel.previewText.observe(viewLifecycleOwner) { previewText ->
            val sentence = previewSentence
            if (sentence != null && previewText != null) {
                sentence.text = previewText
                if (currentTypeface != null) {
                    applyFontToPreviewTexts()
                }
                Log.d(TAG, "Preview text updated from settings")
            }
        }

        if (savedInstanceState != null) {
            currentFontPath = savedInstanceState.getString(KEY_FONT_PATH)
            currentFontFileName = savedInstanceState.getString(KEY_FONT_FILE_NAME)
            currentFontRealName = savedInstanceState.getString(KEY_FONT_REAL_NAME)
            originalFontPath = savedInstanceState.getString(KEY_ORIGINAL_FONT_PATH)
            currentFontSize = savedInstanceState.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
            isVariableFont = savedInstanceState.getBoolean(KEY_IS_VARIABLE_FONT, false)
            currentTtcIndex = savedInstanceState.getInt(KEY_TTC_INDEX, 0)
            isSystemFont = savedInstanceState.getBoolean(KEY_IS_SYSTEM_FONT, false)
            currentWeightWidthLabel = savedInstanceState.getString(KEY_WEIGHT_WIDTH_LABEL)

            @Suppress("DEPRECATION", "UNCHECKED_CAST")
            val savedAxisValues = savedInstanceState.getSerializable(KEY_AXIS_VALUES) as HashMap<String, Float>?
            currentAxisValues.clear()
            if (savedAxisValues != null) {
                currentAxisValues.putAll(savedAxisValues)
            }

            val restoredPath = currentFontPath
            if (restoredPath != null && !restoredPath.isEmpty()) {
                notifyFontChangedImmediate()
                loadFontFromPathWithAxes(restoredPath, currentAxisValues, false)
            }
        } else {
            val hostIntent = requireActivity().intent
            val hasFontFromIntent = hostIntent != null
                    && hostIntent.getStringExtra(FontViewerActivity.EXTRA_FONT_PATH) != null
            if (!hasFontFromIntent) {
                loadLastUsedFont()
            }
        }

        updatePreviewTexts()

        val act = activity
        if (act is FontViewerActivity) {
            act.updateFabFontSizeText(currentFontSize)
        }

        if (act is FontViewerActivity) {
            formattingHelper.setup(act.btnBold, act.btnItalic) { isFakeBold, isFakeItalic ->
                val sentence = previewSentence
                if (sentence != null) {
                    sentence.paint.isFakeBoldText = isFakeBold
                    sentence.paint.textSkewX = if (isFakeItalic) -0.25f else 0f
                    sentence.invalidate()
                }
            }
            formattingHelper.restoreState(savedInstanceState)
            formattingHelper.syncViewState()
        }
    }

    override fun onDestroyView() {
        // إيقاف كل أنيميشن محاور الخط المتغير الجارية لمنع تسريب الذاكرة (Memory Leak)
        for (animator in axisAnimators.values) {
            if (animator.isRunning) {
                animator.cancel()
            }
        }
        axisAnimators.clear()

        formattingHelper.unbind()
        super.onDestroyView()
        previewSentence = null
        weightLabelText = null
        variableAxesContainer = null
        weightAxisUi = null
        widthAxisUi = null
        italicAxisUi = null
        gradeAxisUi = null
        roundnessAxisUi = null
        monoAxisUi = null
        allAxisUis = null
    }

    override fun onDestroy() {
        super.onDestroy()
        bgExecutor.shutdownNow()
    }

    private fun onFontSizeChanged(newSize: Float) {
        currentFontSize = newSize
        applyFontSize()
        sSessionFontSize = newSize
        val act = activity
        if (act is FontViewerActivity) {
            act.updateFabFontSizeText(newSize)
        }
    }

    /**
     * يتم استدعاؤها عند تغيير قيمة أي محور من محاور الخط المتغير عبر Spinner الخاص به
     * (أو عبر المفتاح في حالة محور الميل).
     * تحرّك المعاينة مباشرة (لأداء سلس)، ثم تعيد بناء الـ Typeface في الخلفية بعد انتهاء الحركة
     * لضمان استقرار خصائص الخط عند أي تعديل لاحق (نفس فلسفة التعامل مع الوزن سابقاً).
     */
    private fun onAxisValueChanged(axisTag: String, instance: VariableFontAxisExtractor.VariableInstance?) {
        val fontPath = currentFontPath
        if (instance == null || fontPath == null) {
            return
        }

        val fontFile = File(fontPath)
        if (!fontFile.exists()) {
            return
        }

        val storedOldValue = currentAxisValues[axisTag]
        var oldValue = storedOldValue ?: instance.value
        val newValue = instance.value

        if (oldValue == newValue) {
            return
        }

        preferenceManager.saveFontAxisValue(axisTag, newValue)

        val runningAnimator = axisAnimators[axisTag]
        if (runningAnimator != null && runningAnimator.isRunning) {
            // إزالة المستمعين قبل الإلغاء: cancel() يستدعي onAnimationEnd القديم الذي يقفز بالقيمة الى هدفها السابق
            runningAnimator.removeAllListeners()
            runningAnimator.cancel()
            val refreshedOld = currentAxisValues[axisTag]
            if (refreshedOld != null) {
                oldValue = refreshedOld
            }
        }

        val animator = ValueAnimator.ofFloat(oldValue, newValue)
        animator.duration = 800
        animator.interpolator = AccelerateDecelerateInterpolator()

        animator.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            currentAxisValues[axisTag] = animatedValue

            previewSentence?.setFontVariationSettings(
                VariableTypefaceFactory.buildVariationSettingsString(currentAxisValues)
            )
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                currentAxisValues[axisTag] = newValue

                // بناء Typeface النهائي في الخلفية لضمان استقرار خصائص الخط عند تغيير حجمه أو محاوره لاحقاً
                val snapshot: Map<String, Float> = LinkedHashMap(currentAxisValues)
                bgExecutor.execute {
                    val finalTypeface: Typeface? = if (isSystemFont) {
                        SystemFontCache.getInstance()
                            .getTypefaceWithAxes(currentFontPath, snapshot, currentTtcIndex)
                    } else {
                        VariableTypefaceFactory.createTypefaceWithAxes(fontFile, snapshot, currentTtcIndex)
                    }
                    if (finalTypeface != null) {
                        mainHandler.post { currentTypeface = finalTypeface }
                    }
                }
            }
        })

        axisAnimators[axisTag] = animator
        animator.start()
    }

    private fun updatePreviewTexts() {
        val sentence = previewSentence ?: return

        val previewText = SettingsRepository.getPreviewText(requireContext())
        sentence.text = previewText

        if (currentTypeface != null) {
            applyFontToPreviewTexts()
        }

        applyFontSize()
    }

    private fun initViews(view: View) {
        previewSentence = view.findViewById(R.id.preview_sentence)
        weightLabelText = view.findViewById(R.id.weight_label_text)

        variableAxesContainer = view.findViewById(R.id.variable_axes_container)
        val axesRow = view.findViewById<ViewGroup>(R.id.variable_axes_row)

        val weightUi = inflateAxisItem(
            axesRow, VariableFontAxisExtractor.AXIS_WGHT,
            R.string.font_info_axis_weight, R.string.axis_info_weight_dialog_title, R.drawable.axis_weight_info_img, R.string.axis_info_weight_description
        )
        val widthUi = inflateAxisItem(
            axesRow, VariableFontAxisExtractor.AXIS_WDTH,
            R.string.font_info_axis_width, R.string.axis_info_width_dialog_title, R.drawable.axis_width_info_img, R.string.axis_info_width_description
        )
        italicAxisUi = inflateAxisSwitchItem(
            axesRow, VariableFontAxisExtractor.AXIS_ITAL,
            R.string.font_info_axis_italic, R.string.axis_info_italic_dialog_title, R.drawable.axis_italic_info_img, R.string.axis_info_italic_description
        )
        val gradeUi = inflateAxisItem(
            axesRow, VariableFontAxisExtractor.AXIS_GRAD,
            R.string.font_info_axis_grade, R.string.axis_info_grade_dialog_title, R.drawable.axis_grade_info_img, R.string.axis_info_grade_description
        )
        val roundnessUi = inflateAxisItem(
            axesRow, VariableFontAxisExtractor.AXIS_ROND,
            R.string.font_info_axis_roundness, R.string.axis_info_roundness_dialog_title, R.drawable.axis_roundness_info_img, R.string.axis_info_roundness_description
        )
        val monoUi = inflateAxisItem(
            axesRow, VariableFontAxisExtractor.AXIS_MONO,
            R.string.font_info_axis_monospace, R.string.axis_info_monospace_dialog_title, R.drawable.axis_monospace_info_img, R.string.axis_info_monospace_description
        )

        weightAxisUi = weightUi
        widthAxisUi = widthUi
        gradeAxisUi = gradeUi
        roundnessAxisUi = roundnessUi
        monoAxisUi = monoUi

        val list: MutableList<AxisSpinnerUi?> = ArrayList()
        list.add(weightUi)
        list.add(widthUi)
        list.add(gradeUi)
        list.add(roundnessUi)
        list.add(monoUi)
        allAxisUis = list
    }

    /**
     * ★ ينفخ (Inflate) نسخة واحدة من قالب البطاقة الموحّد vf_axis_spinner_item لهذا المحور، ويربط
     *   اسم المحور (الذي يفتح ديالوج معلومات المحور عند الضغط عليه)، ثم يضيفها الى صف المحاور. هذا يحدث مرة واحدة فقط
     *   عند إنشاء الـ Fragment (initViews)، وليس عند كل فتح خط، فلا توجد أي كلفة أداء إضافية
     *   عند فتح الخطوط لاحقاً — نفس عدد الـ Views ونفس منطق الإظهار/الإخفاء كما كان سابقاً ★
     */
    private fun inflateAxisItem(
        parent: ViewGroup, axisTag: String, @StringRes labelRes: Int,
        @StringRes infoTitleRes: Int, @DrawableRes infoImageRes: Int,
        @StringRes infoDescriptionRes: Int
    ): AxisSpinnerUi {
        val item = LayoutInflater.from(parent.context)
            .inflate(R.layout.vf_axis_spinner_item, parent, false)

        val nameText = item.findViewById<TextView?>(R.id.axis_name_text)
        if (nameText != null) {
            nameText.setText(labelRes)
            nameText.setOnClickListener {
                AxisInfoDialog.show(requireContext(), infoTitleRes, infoImageRes, infoDescriptionRes)
            }
        }

        val spinner = item.findViewById<AppCompatSpinner?>(R.id.axis_spinner)

        parent.addView(item)

        return AxisSpinnerUi(axisTag, item, spinner)
    }

    /**
     * نفس فكرة inflateAxisItem تماماً، لكن للمحاور الثنائية القيمة: ينفخ بطاقة vf_axis_switch_item
     * (الاسم القابل للضغط لفتح معلومات المحور في الأعلى والمفتاح تحته) ويضيفها الى صف المحاور مرة واحدة فقط عند الإنشاء.
     */
    private fun inflateAxisSwitchItem(
        parent: ViewGroup, axisTag: String, @StringRes labelRes: Int,
        @StringRes infoTitleRes: Int, @DrawableRes infoImageRes: Int,
        @StringRes infoDescriptionRes: Int
    ): AxisSwitchUi {
        val item = LayoutInflater.from(parent.context)
            .inflate(R.layout.vf_axis_switch_item, parent, false)

        val nameText = item.findViewById<TextView?>(R.id.axis_name_text)
        if (nameText != null) {
            nameText.setText(labelRes)
            nameText.setOnClickListener {
                AxisInfoDialog.show(requireContext(), infoTitleRes, infoImageRes, infoDescriptionRes)
            }
        }

        val switchView = item.findViewById<SwitchCompat?>(R.id.axis_switch)

        parent.addView(item)

        return AxisSwitchUi(axisTag, item, switchView)
    }

    fun loadFontFromPath(path: String, fileName: String?, realName: String?, ttcIndex: Int, isSystemFont: Boolean) {
        Log.d(TAG, "loadFontFromPath - Received data:")
        Log.d(TAG, "  realName: $realName")
        Log.d(TAG, "  fileName: $fileName")
        Log.d(TAG, "  ttcIndex: $ttcIndex")
        Log.d(TAG, "  isSystemFont: $isSystemFont")

        currentFontPath = path
        currentFontFileName = fileName
        currentFontRealName = realName
        currentTtcIndex = ttcIndex
        this.isSystemFont = isSystemFont

        val existingOriginal = originalFontPath
        if (existingOriginal == null || existingOriginal.isEmpty()) {
            originalFontPath = extractRealPathFromUri(path)
        }

        preferenceManager.saveLastViewedFont(path, fileName, realName)
        preferenceManager.saveLastViewedFontOriginalPath(originalFontPath)

        notifyFontChangedImmediate()

        formattingHelper.reset()

        // ★ البيانات المجهّزة مسبقاً (FontPrefetcher): اذا جُهّز الخط وتحليل محاوره أثناء فتح الشاشة، نطبّق كل شيء فوراً
        //   (الخط + بطاقات المحاور أو تسمية الوزن) قبل رسم الإطار الأول، ولا نحتاج الى التحميل الخلفي إطلاقاً ★
        if (applyPrefetchedFont(path, ttcIndex, isSystemFont)) {
            return
        }

        // ★ لم تكن البيانات جاهزة: نطبّق الخط فوراً من الذاكرة المؤقتة (Instant Cache Hit) إن وُجد، فلا يظهر الخط الافتراضي،
        //   ثم نتابع التحميل الكامل في الخلفية كالمعتاد. اذا لم تُنشأ الواجهة بعد يُطبَّق في onViewCreated عبر updatePreviewTexts ★
        val cachedTypeface = FontPrefetcher.getCachedTypeface(path, ttcIndex, isSystemFont)
        if (cachedTypeface != null) {
            currentTypeface = cachedTypeface
            if (previewSentence != null) {
                applyFontToPreviewTexts()
            }
        }

        // خط جديد تم اختياره: نعيد كل محاوره الى قيمها الافتراضية بدل حمل قيم الخط السابق
        loadFontFromPathWithAxes(path, null, true)
    }

    /**
     * يطبّق نتيجة التجهيز المسبق (إن وُجدت وصالحة) بنفس ما كان سيفعله التحميل الخلفي عند النجاح، لكن فوراً وعلى الخيط الرئيسي
     * ودون أي قراءة ملفات (كل ما فيها محسوب مسبقاً). يعيد false اذا لم تكن جاهزة، فيستمر المسار القديم كما هو.
     */
    private fun applyPrefetchedFont(path: String, ttcIndex: Int, systemFont: Boolean): Boolean {
        val context = getContext() ?: return false

        val data = FontPrefetcher.take(context, path, ttcIndex, systemFont)
        if (data == null) {
            Log.d(TAG, "Prefetch miss - falling back to background load: $path")
            return false
        }
        Log.d(TAG, "Prefetch hit - applying font synchronously: $path")

        // خط جديد: كل محاوره تعود الى قيمها الافتراضية ونحفظها (نفس ما يفعله التحميل الخلفي عند resetToDefaults)
        for (entry in data.axisDefaults.entries) {
            preferenceManager.saveFontAxisValue(entry.key, entry.value)
        }

        currentTypeface = data.typeface
        isVariableFont = data.isVariable
        currentAxisValues.clear()
        currentAxisValues.putAll(data.axisDefaults)
        prefetchedAxisInstancesMap = data.axisInstancesMap
        prefetchedUiPending = true

        // اذا لم تُنشأ الواجهة بعد، تُطبَّق الحالة في onViewCreated قبل رسم الإطار الأول
        applyLoadedFontUi()
        return true
    }

    /**
     * يعرض حالة الخط المحمّل (بطاقات المحاور للخط المتغير أو تسمية الوزن للثابت + الخط على المعاينة) على الواجهة،
     * بنفس منطق النجاح في loadFontFromPathWithAxes. لا تفعل شيئاً اذا لم تُنشأ الواجهة بعد (يبقى prefetchedUiPending).
     */
    private fun applyLoadedFontUi() {
        if (variableAxesContainer == null || previewSentence == null) {
            return
        }

        val instancesMap = prefetchedAxisInstancesMap
        if (isVariableFont && !currentAxisValues.isEmpty() && instancesMap != null) {
            setupAxisSpinners(instancesMap)
        } else {
            showWeightLabel(currentWeightWidthLabel)
        }

        applyFontToPreviewTexts()
        prefetchedUiPending = false
    }

    fun loadFontFromPath(
        path: String, fileName: String?, realName: String?,
        ttcIndex: Int, isSystemFont: Boolean, weightWidthLabel: String?
    ) {
        currentWeightWidthLabel = weightWidthLabel
        loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont)
    }

    private fun extractRealPathFromUri(pathOrUri: String?): String? {
        if (pathOrUri == null) return null

        if (pathOrUri.startsWith("content://")) {
            val uri = Uri.parse(pathOrUri)
            val realPath = storageManager.getRealPathFromUri(uri)
            if (realPath != null && !realPath.isEmpty()) {
                return realPath
            }
        }

        return pathOrUri
    }

    private fun notifyFontChangedImmediate() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performNotification()
        } else {
            mainHandler.post { performNotification() }
        }
    }

    private fun performNotification() {
        val listener = fontChangedListener
        if (listener != null && currentFontFileName != null) {
            listener.onFontChanged(currentFontRealName, currentFontFileName)
            Log.d(TAG, "MainActivity notified with realName: $currentFontRealName, fileName: $currentFontFileName")
        }
    }

    /**
     * يحمّل الخط من مساره، ويحدد لكل محور مدعوم (wght, wdth, ital, GRAD, ROND, MONO) القيمة التي
     * سيُفتح بها، وفق ثلاث حالات:
     * 1) restoreAxisValues غير فارغة لمحور معيّن: يُستخدم بعد تدوير الشاشة، نستعيد القيمة كما كانت
     *    معروضة تماماً قبل التدوير.
     * 2) resetToDefaults = true: خط جديد تم اختياره، فنعيد هذا المحور الى قيمته الافتراضية الفعلية
     *    في الخط ونحفظها.
     * 3) غير ذلك (فتح آخر خط تم عرضه عند فتح التطبيق): نستخدم آخر قيمة محفوظة لهذا المحور.
     */
    private fun loadFontFromPathWithAxes(path: String, restoreAxisValues: Map<String, Float>?, resetToDefaults: Boolean) {
        bgExecutor.execute {
            try {
                val fontFile = File(path)
                if (!fontFile.exists()) {
                    mainHandler.post { resetFontDisplay() }
                    return@execute
                }

                val isVar = VariableFontAxisExtractor.isVariableFont(fontFile, currentTtcIndex)
                // Context يُستخدم لقراءة أسماء قيم المحاور من strings.xml حسب لغة التطبيق
                val axisValuesContext = requireContext()

                // خريطة وسم المحور الى قائمة القيم المسمّاة المتاحة له
                val axisInstancesMap: MutableMap<String, List<VariableFontAxisExtractor.VariableInstance>> = LinkedHashMap()
                // القيم المختارة فعلياً لكل محور مدعوم
                val resolvedAxisValues: MutableMap<String, Float> = LinkedHashMap()

                if (isVar) {
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_WGHT] = VariableFontAxisExtractor.extractVariableInstances(axisValuesContext, fontFile, currentTtcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_WDTH] = VariableFontAxisExtractor.extractWidthInstances(axisValuesContext, fontFile, currentTtcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_ITAL] = VariableFontAxisExtractor.extractItalicInstances(axisValuesContext, fontFile, currentTtcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_GRAD] = VariableFontAxisExtractor.extractGradeInstances(axisValuesContext, fontFile, currentTtcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_ROND] = VariableFontAxisExtractor.extractRoundnessInstances(axisValuesContext, fontFile, currentTtcIndex)
                    axisInstancesMap[VariableFontAxisExtractor.AXIS_MONO] = VariableFontAxisExtractor.extractMonoInstances(axisValuesContext, fontFile, currentTtcIndex)

                    for (entry in axisInstancesMap.entries) {
                        val axisTag = entry.key
                        val instances = entry.value

                        if (instances.isEmpty()) {
                            continue
                        }

                        val fallbackBoxed = AXIS_FALLBACK_DEFAULTS[axisTag]
                        val fallback = fallbackBoxed ?: 0f
                        val fontDefault = VariableFontAxisExtractor.readAxisDefaultValue(fontFile, currentTtcIndex, axisTag, fallback)

                        val resolvedValue: Float
                        if (restoreAxisValues != null && restoreAxisValues.containsKey(axisTag)) {
                            resolvedValue = restoreAxisValues[axisTag]!!
                        } else if (resetToDefaults) {
                            resolvedValue = fontDefault
                            preferenceManager.saveFontAxisValue(axisTag, resolvedValue)
                        } else {
                            resolvedValue = preferenceManager.getFontAxisValue(axisTag, fontDefault)
                        }

                        resolvedAxisValues[axisTag] = resolvedValue
                    }
                }

                val typeface: Typeface? = try {
                    if (isSystemFont) {
                        val cache = SystemFontCache.getInstance()
                        cache.getTypefaceWithAxes(path, resolvedAxisValues, currentTtcIndex)
                    } else {
                        VariableTypefaceFactory.createTypefaceWithAxes(fontFile, resolvedAxisValues, currentTtcIndex)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "★ Typeface creation failed - font might be corrupted", e)
                    mainHandler.post {
                        currentFontRealName = null
                        currentTypeface = null

                        fontChangedListener?.let {
                            it.onFontChanged(currentFontRealName, currentFontFileName)
                            Log.d(TAG, "★ Updated title to 'Unknown Font' for corrupted font")
                        }

                        hideAxisUi()

                        val defaultTypeface = Typeface.DEFAULT
                        previewSentence?.typeface = defaultTypeface

                        Toast.makeText(
                            requireContext(),
                            getString(R.string.font_viewer_error_loading_font) +
                                    " (" + getString(R.string.unknown_font) + ")",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@execute
                }

                if (typeface != null) {
                    val finalTypeface: Typeface = typeface
                    val finalIsVariable = isVar
                    val finalInstancesMap: Map<String, List<VariableFontAxisExtractor.VariableInstance>> = axisInstancesMap
                    val finalResolvedValues: Map<String, Float> = resolvedAxisValues

                    mainHandler.post {
                        currentTypeface = finalTypeface
                        isVariableFont = finalIsVariable

                        currentAxisValues.clear()
                        currentAxisValues.putAll(finalResolvedValues)

                        if (finalIsVariable && !finalResolvedValues.isEmpty()) {
                            setupAxisSpinners(finalInstancesMap)
                        } else {
                            showWeightLabel(currentWeightWidthLabel)
                        }

                        applyFontToPreviewTexts()
                        Log.d(TAG, "Font typeface loaded and applied successfully")
                    }
                } else {
                    throw Exception("Failed to create Typeface - returned null")
                }

            } catch (e: Exception) {
                mainHandler.post {
                    currentFontRealName = null

                    fontChangedListener?.let {
                        it.onFontChanged(currentFontRealName, currentFontFileName)
                        Log.d(TAG, "★ Updated title to 'Unknown Font' after general error")
                    }

                    hideAxisUi()

                    currentTypeface = null

                    val defaultTypeface = Typeface.DEFAULT
                    previewSentence?.typeface = defaultTypeface

                    Toast.makeText(
                        requireContext(),
                        getString(R.string.font_viewer_error_loading_font) +
                                " (" + getString(R.string.unknown_font) + ")",
                        Toast.LENGTH_SHORT
                    ).show()

                    Log.e(TAG, "Error creating typeface from path: $path", e)
                }
            }
        }
    }

    private fun setupAxisSpinners(axisInstancesMap: Map<String, List<VariableFontAxisExtractor.VariableInstance>>) {
        val container = variableAxesContainer
        val weightLabel = weightLabelText
        val axisUis = allAxisUis
        if (!isAdded || container == null || weightLabel == null || axisUis == null) return

        weightLabel.visibility = View.GONE
        container.visibility = View.VISIBLE

        for (ui in axisUis) {
            if (ui == null) continue
            val instances = axisInstancesMap[ui.tag]
            setupSingleAxisSpinner(ui, instances)
        }

        // محور الميل له بطاقة مفتاح مستقلة، ويُخفى تلقائياً اذا لم يدعمه الخط
        setupItalicSwitch(italicAxisUi, axisInstancesMap[VariableFontAxisExtractor.AXIS_ITAL])
    }

    private fun setupSingleAxisSpinner(ui: AxisSpinnerUi?, instances: List<VariableFontAxisExtractor.VariableInstance>?) {
        if (ui == null) return
        val spinner = ui.spinner ?: return

        if (instances == null || instances.isEmpty()) {
            ui.container.visibility = View.GONE
            ui.instances = ArrayList()
            return
        }

        ui.instances = instances
        ui.container.visibility = View.VISIBLE

        val instanceNames: MutableList<String> = ArrayList()
        for (inst in instances) {
            instanceNames.add(inst.name)
        }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.vf_axis_spinner_selected,
            instanceNames
        )
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item)

        spinner.adapter = adapter

        val currentValue = currentAxisValues[ui.tag]
        var selectedIndex = 0
        if (currentValue != null) {
            var closestDiff = Float.MAX_VALUE
            for (i in instances.indices) {
                val diff = Math.abs(instances[i].value - currentValue)
                if (diff < closestDiff) {
                    closestDiff = diff
                    selectedIndex = i
                }
            }
        }
        spinner.setSelection(selectedIndex)

        val finalInstances = instances
        spinner.post {
            if (!isAdded) return@post
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (position >= 0 && position < finalInstances.size) {
                        onAxisValueChanged(ui.tag, finalInstances[position])
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    /**
     * يضبط بطاقة مفتاح محور الميل: تُخفى اذا لم يدعم الخط المحور،
     * وإلا يُضبط المفتاح على أقرب قيمة للقيمة الحالية دون إطلاق المستمع، ثم يُربط المستمع الذي يحرّك المحور.
     * نص المفتاح يعرض اسم القيمة الحالية (Upright / Italic) من نفس أسماء القيم المستخرجة.
     */
    private fun setupItalicSwitch(ui: AxisSwitchUi?, instances: List<VariableFontAxisExtractor.VariableInstance>?) {
        if (ui == null) return
        val switchView = ui.switchView ?: return

        if (instances == null || instances.isEmpty()) {
            switchView.setOnCheckedChangeListener(null)
            ui.container.visibility = View.GONE
            return
        }

        val offInstance = instances[0]
        val onInstance = instances[instances.size - 1]
        ui.container.visibility = View.VISIBLE

        val currentValue = currentAxisValues[ui.tag]
        val checked = currentValue != null
                && Math.abs(onInstance.value - currentValue) < Math.abs(offInstance.value - currentValue)

        // نزيل المستمع أولاً حتى لا يطلق ضبط الحالة الابتدائية حركة المحور
        switchView.setOnCheckedChangeListener(null)
        switchView.isChecked = checked
        switchView.jumpDrawablesToCurrentState()
        switchView.text = if (checked) onInstance.name else offInstance.name

        switchView.setOnCheckedChangeListener { button, isChecked ->
            val target = if (isChecked) onInstance else offInstance
            button.text = target.name
            onAxisValueChanged(ui.tag, target)
        }
    }

    private fun showWeightLabel(label: String?) {
        val weightLabel = weightLabelText
        val container = variableAxesContainer
        if (weightLabel == null || container == null) return

        container.visibility = View.GONE

        val displayLabel = FontWeightWidthExtractor.formatLabel(weightLabel.context, label)

        if (displayLabel != null && !displayLabel.isEmpty()) {
            weightLabel.text = displayLabel
            weightLabel.visibility = View.VISIBLE
        } else {
            weightLabel.visibility = View.GONE
        }
    }

    private fun hideAxisUi() {
        weightLabelText?.visibility = View.GONE
        variableAxesContainer?.visibility = View.GONE
    }

    fun loadFontFromUri(uri: Uri, fileName: String?) {
        originalFontPath = storageManager.getRealPathFromUri(uri)
        val resolvedOriginal = originalFontPath
        if (resolvedOriginal == null || resolvedOriginal.isEmpty()) {
            originalFontPath = Uri.decode(uri.toString())
        }
        isSystemFont = false

        bgExecutor.execute {
            val copiedFont = storageManager.copyFontForViewing(uri, fileName)

            if (copiedFont != null && copiedFont.exists()) {
                var realName: String? = null

                try {
                    realName = FontMetadataExtractor.extractFontName(copiedFont, 0)
                    currentWeightWidthLabel = FontWeightWidthExtractor.extract(copiedFont, 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract font metadata from URI", e)
                }

                if (realName == null || realName.isEmpty() || "Unknown Font" == realName || getString(R.string.unknown_font) == realName) {
                    val finalFileName = fileName ?: copiedFont.name

                    mainHandler.post {
                        loadFontFromPath(copiedFont.absolutePath, finalFileName, null, 0, false)

                        Toast.makeText(
                            requireContext(),
                            R.string.toast_font_name_extraction_warning,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val finalFileName = fileName ?: copiedFont.name
                    val finalRealName: String = realName

                    mainHandler.post {
                        loadFontFromPath(copiedFont.absolutePath, finalFileName, finalRealName, 0, false)
                    }
                }
            } else {
                mainHandler.post {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.font_viewer_error_loading_font),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun applyFontToPreviewTexts() {
        val typeface = currentTypeface
        val sentence = previewSentence
        if (typeface != null && sentence != null) {
            sentence.typeface = typeface
            sentence.paint.isFakeBoldText = formattingHelper.isBoldActive
            sentence.paint.textSkewX = if (formattingHelper.isItalicActive) -0.25f else 0f
        }
        applyFontSize()
    }

    private fun applyFontSize() {
        previewSentence?.setTextSize(TypedValue.COMPLEX_UNIT_DIP, currentFontSize)
    }

    fun showFontSizeDialogPublic() {
        val originalSize = currentFontSize

        val fontSizeDialog = FontSizeDialog(
            requireContext(),
            currentFontSize,
            MIN_FONT_SIZE,
            MAX_FONT_SIZE
        )

        fontSizeDialog.setOnFontSizeChangedListener { newSize -> onFontSizeChanged(newSize) }

        fontSizeDialog.setOnDialogCancelledListener {
            // الحوار يغيّر الحجم فقط، فلا نستعيد Typeface محفوظاً قبل فتحه: قد يكون قديماً إن فُتح الحوار أثناء حركة المحور
            currentFontSize = originalSize
            applyFontToPreviewTexts()
            applyFontSize()

            val act = activity
            if (act is FontViewerActivity) {
                act.updateFabFontSizeText(originalSize)
            }
        }

        fontSizeDialog.show()
    }

    private fun resetFontDisplay() {
        currentTypeface = null
        currentFontPath = null
        currentFontFileName = null
        currentFontRealName = null
        originalFontPath = null
        isVariableFont = false
        currentAxisValues.clear()
        currentTtcIndex = 0
        isSystemFont = false
        currentWeightWidthLabel = null

        val defaultTypeface = Typeface.DEFAULT
        previewSentence?.typeface = defaultTypeface

        hideAxisUi()

        fontChangedListener?.onFontCleared()

        formattingHelper.reset()
    }

    private fun loadLastUsedFont() {
        // قراءة آخر خط عُرض (الإعدادات + فحص الملف + استخراج وزنه وعرضه من الملف) كانت تتم على الخيط الرئيسي أثناء
        // إنشاء الشاشة. بعد إعادة تشغيل الجهاز تكون ملفات الخطوط خارج ذاكرة النظام المؤقتة، فتتأخر القراءة وتتجمد
        // الواجهة. لذلك تُنفَّذ الآن في الخلفية، ثم تُطبَّق النتيجة على الخيط الرئيسي.
        bgExecutor.execute {
            val lastPath = preferenceManager.lastViewedFontPath
            val lastFileName = preferenceManager.lastViewedFontFileName
            val lastRealName = preferenceManager.lastViewedFontRealName

            if (lastPath != null && !lastPath.isEmpty()) {
                val localFile = File(lastPath)
                if (localFile.exists()) {
                    val weightWidthLabel = FontWeightWidthExtractor.extract(localFile, 0)

                    val lastOriginalPath = preferenceManager.lastViewedFontOriginalPath
                    val resolvedOriginalPath = if (lastOriginalPath != null && !lastOriginalPath.isEmpty())
                        lastOriginalPath
                    else
                        extractRealPathFromUri(lastPath)

                    mainHandler.post {
                        // الشاشة أُغلقت، أو اختار المستخدم خطاً آخر أثناء القراءة في الخلفية: لا نكتب فوق اختياره
                        val existingPath = currentFontPath
                        if (!isAdded || (existingPath != null && !existingPath.isEmpty())) {
                            return@post
                        }

                        currentFontPath = lastPath
                        currentFontFileName = lastFileName
                        currentFontRealName = lastRealName
                        currentTtcIndex = 0
                        isSystemFont = false
                        currentWeightWidthLabel = weightWidthLabel

                        val realNameNow = currentFontRealName
                        if (realNameNow != null && (realNameNow.isEmpty() || realNameNow == getString(R.string.unknown_font))) {
                            currentFontRealName = null
                        }

                        val existingOriginal = originalFontPath
                        if (existingOriginal == null || existingOriginal.isEmpty()) {
                            originalFontPath = resolvedOriginalPath
                        }

                        notifyFontChangedImmediate()
                        // اعادة فتح آخر خط تم عرضه: نستخدم آخر قيمة محفوظة لكل محور من محاوره
                        loadFontFromPathWithAxes(lastPath, null, false)
                    }
                } else {
                    preferenceManager.clearLastViewedFont()
                }
            }
        }
    }

    val fontMetadata: Map<String, String>
        get() {
            val path = currentFontPath ?: return HashMap()

            val fontFile = File(path)

            val metadata: MutableMap<String, String> =
                HashMap(FontMetadataExtractor.extractMetadataWithTtcIndex(fontFile, currentTtcIndex))

            val original = originalFontPath
            val displayPath = if (original != null && !original.isEmpty())
                original
            else
                path

            metadata["Path"] = displayPath
            metadata["FileName"] = currentFontFileName ?: ""

            if (!metadata.containsKey("FullName")) {
                val realName = currentFontRealName
                if (realName == null || realName.isEmpty() || realName == getString(R.string.unknown_font)) {
                    metadata["FullName"] = getString(R.string.unknown_font)
                    metadata["Warning"] = "Font metadata could not be extracted - file may be corrupted"
                } else {
                    metadata["FullName"] = realName
                }
            }

            if (currentTtcIndex > 0) {
                metadata["TTC Index"] = currentTtcIndex.toString()
            }

            return metadata
        }

    override fun onSaveInstanceState(outState: Bundle) {
        formattingHelper.saveState(outState)
        super.onSaveInstanceState(outState)
        if (currentFontPath != null) {
            outState.putString(KEY_FONT_PATH, currentFontPath)
        }
        if (currentFontFileName != null) {
            outState.putString(KEY_FONT_FILE_NAME, currentFontFileName)
        }
        if (currentFontRealName != null) {
            outState.putString(KEY_FONT_REAL_NAME, currentFontRealName)
        }
        if (originalFontPath != null) {
            outState.putString(KEY_ORIGINAL_FONT_PATH, originalFontPath)
        }
        if (currentWeightWidthLabel != null) {
            outState.putString(KEY_WEIGHT_WIDTH_LABEL, currentWeightWidthLabel)
        }
        outState.putFloat(KEY_FONT_SIZE, currentFontSize)
        // حفظ قيم كل محاور الخط المتغير الحالية (الوزن، العرض، الميل، التدرج، الاستدارة، والتباعد الأحادي) دفعة واحدة
        outState.putSerializable(KEY_AXIS_VALUES, HashMap(currentAxisValues))
        outState.putBoolean(KEY_IS_VARIABLE_FONT, isVariableFont)
        outState.putInt(KEY_TTC_INDEX, currentTtcIndex)
        outState.putBoolean(KEY_IS_SYSTEM_FONT, isSystemFont)
    }

    fun hasFontSelected(): Boolean {
        val path = currentFontPath
        return path != null && !path.isEmpty()
    }

    companion object {
        private const val KEY_FONT_PATH = "font_path"
        private const val KEY_FONT_FILE_NAME = "font_file_name"
        private const val KEY_FONT_REAL_NAME = "font_real_name"
        private const val KEY_ORIGINAL_FONT_PATH = "original_font_path"
        private const val KEY_FONT_SIZE = "font_size"

        // خريطة قيم كل محاور الخط المتغير الحالية (wght, wdth, ital, GRAD, ROND, MONO) تُحفظ دفعة واحدة
        private const val KEY_AXIS_VALUES = "axis_values"
        private const val KEY_IS_VARIABLE_FONT = "is_variable_font"
        private const val KEY_TTC_INDEX = "ttc_index"
        private const val KEY_IS_SYSTEM_FONT = "is_system_font"
        private const val KEY_WEIGHT_WIDTH_LABEL = "weight_width_label"
        private const val TAG = "FontViewerFragment"

        private const val DEFAULT_FONT_SIZE = 34f
        private const val MIN_FONT_SIZE = 12f
        private const val MAX_FONT_SIZE = 520f
        private const val DEFAULT_FONT_WEIGHT = 400f

        // القيم الافتراضية الاحتياطية لكل محور، تُستخدم فقط اذا تعذّرت قراءة القيمة الافتراضية الفعلية
        // من جدول fvar الخاص بالخط نفسه
        private val AXIS_FALLBACK_DEFAULTS: MutableMap<String, Float> = HashMap<String, Float>().apply {
            put(VariableFontAxisExtractor.AXIS_WGHT, DEFAULT_FONT_WEIGHT)
            put(VariableFontAxisExtractor.AXIS_WDTH, 100f)
            put(VariableFontAxisExtractor.AXIS_ITAL, 0f)
            put(VariableFontAxisExtractor.AXIS_GRAD, 0f)
            put(VariableFontAxisExtractor.AXIS_ROND, 0f)
            put(VariableFontAxisExtractor.AXIS_MONO, 0f)
        }

        private var sSessionFontSize = -1f
    }
}
