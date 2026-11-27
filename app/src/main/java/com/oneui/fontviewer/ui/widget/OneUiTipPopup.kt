package com.oneui.fontviewer.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.DisplayCutout
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView

import androidx.annotation.ColorInt
import androidx.appcompat.animation.SeslAnimationUtils
import androidx.reflect.content.res.SeslConfigurationReflector

import com.oneui.fontviewer.BuildConfig
import com.oneui.fontviewer.R

import kotlin.math.ceil
import kotlin.math.floor

/**
 * Kotlin port of the TipPopup class from OneUI Design 8.
 * Uses the resources already shipped with OneUI Design 4 (the sem_tip_popup_* files).
 */
class OneUiTipPopup(private val parentView: View, private val mode: Mode) {

    constructor(parentView: View) : this(parentView, Mode.NORMAL)

    enum class Mode {
        NORMAL,
        TRANSLUCENT
    }

    enum class Direction {
        DEFAULT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_LEFT,
        TOP_RIGHT
    }

    private enum class Type {
        BALLOON_SIMPLE,
        BALLOON_ACTION,
        BALLOON_CUSTOM
    }

    enum class State {
        DISMISSED,
        EXPANDED,
        HINT
    }

    fun interface OnDismissListener {
        fun onDismiss()
    }

    fun interface OnStateChangeListener {
        fun onStateChanged(state: State)
    }

    private val context: Context = parentView.context
    private val resources: Resources = context.resources
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val actionView: Button
    private var actionClickListener: View.OnClickListener? = null
    private var actionText: CharSequence? = null
    private var actionTextColor: Int? = null

    private var arrowDirection: Direction = Direction.DEFAULT
    private var arrowPositionX = -1
    private var arrowPositionY = -1
    private val arrowHeight: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_height)
    private val arrowWidth: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_width)

    private val messageView: TextView

    private var backgroundColor: Int = context.getColor(R.color.sem_tip_popup_background_color)
    private lateinit var balloonBg1: ImageView
    private lateinit var balloonBg2: ImageView
    private lateinit var balloonBubble: FrameLayout
    private lateinit var balloonBubbleHint: ImageView
    private lateinit var balloonBubbleIcon: ImageView
    private lateinit var balloonContent: FrameLayout
    private var balloonHeight = 0
    private lateinit var balloonPanel: FrameLayout
    private lateinit var balloonPopup: TipWindow
    private var balloonPopupX = 0
    private var balloonPopupY = 0
    private val balloonView: View
    private var balloonWidth = 0
    private var balloonX = -1
    private var balloonY = 0
    private var iconColor: Int? = null
    private lateinit var bubbleBackground: ImageView
    private var bubbleHeight = 0
    private lateinit var bubbleIcon: ImageView
    private lateinit var bubblePopup: TipWindow
    private var bubblePopupX = 0
    private var bubblePopupY = 0
    private val bubbleView: View
    private var bubbleWidth = 0
    private var bubbleX = 0
    private var bubbleY = 0

    private var displayMetrics: DisplayMetrics = resources.displayMetrics
    private var forceRealDisplay = false
    private var hintDescription: CharSequence? = null

    private var initialMessageViewWidth = 0
    private var isDefaultPosition = true
    private var isMessageViewMeasured = false
    private var messageText: CharSequence? = null
    private var messageTextColor: Int? = null

    private var needToCallParentViewsOnClick = false
    private var onDismissListener: OnDismissListener? = null
    private var onStateChangeListener: OnStateChangeListener? = null

    private var scaleMargin: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin)
    private var sideMargin: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin)

    private var state: State = State.HINT
    private var type: Type = Type.BALLOON_SIMPLE

    private val displayFrame = Rect()
    private val horizontalTextMargin: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_horizontal)
    private val verticalTextMargin: Int = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_vertical)
    private var handler: Handler? = null

    init {
        initInterpolators()

        val inflater = LayoutInflater.from(context)
        this.bubbleView = inflater.inflate(R.layout.sem_tip_popup_bubble, null)
        this.balloonView = inflater.inflate(R.layout.sem_tip_popup_balloon, null)
        this.messageView = balloonView.findViewById(R.id.sem_tip_popup_message)
        this.actionView = balloonView.findViewById(R.id.sem_tip_popup_action)
        messageView.visibility = View.GONE
        actionView.visibility = View.GONE

        initBubblePopup(mode)
        initBalloonPopup(mode)

        if (mode == Mode.TRANSLUCENT) {
            val translucentTextColor = context.getColor(R.color.sem_tip_popup_text_color_translucent)
            messageView.setTextColor(translucentTextColor)
            actionView.setTextColor(translucentTextColor)
        }

        bubblePopup.setOnDismissListener {
            if (state == State.HINT) {
                state = State.DISMISSED
                val listener = onStateChangeListener
                if (listener != null) {
                    listener.onStateChanged(state)
                    debugLog("isShowing : $isShowing")
                }
                handler?.removeCallbacksAndMessages(null)
                handler = null
                debugLog("onDismiss - BubblePopup")
            }
        }

        balloonPopup.setOnDismissListener {
            state = State.DISMISSED
            val listener = onStateChangeListener
            if (listener != null) {
                listener.onStateChanged(state)
                debugLog("isShowing : $isShowing")
            }
            debugLog("onDismiss - BalloonPopup")
            dismissBubble(false)
            handler?.removeCallbacksAndMessages(null)
            handler = null
        }

        balloonView.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(
                    AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK,
                        context.getString(R.string.oui_common_close)
                    )
                )
            }
        }
    }

    private fun initInterpolators() {
        if (interpolatorSineInOut33 == null) {
            interpolatorSineInOut33 = AnimationUtils.loadInterpolator(context, R.interpolator.sine_in_out_33)
        }
        if (interpolatorSineInOut70 == null) {
            interpolatorSineInOut70 = AnimationUtils.loadInterpolator(context, R.interpolator.sine_in_out_70)
        }
        if (interpolatorElastic50 == null) {
            interpolatorElastic50 = SeslAnimationUtils.ELASTIC_50
        }
        if (interpolatorElasticCustom == null) {
            interpolatorElasticCustom = createElasticInterpolator(1.0f, 1.3f)
        }
    }

    private fun initBubblePopup(mode: Mode) {
        bubbleBackground = bubbleView.findViewById(R.id.sem_tip_popup_bubble_bg)
        bubbleIcon = bubbleView.findViewById(R.id.sem_tip_popup_bubble_icon)

        if (mode == Mode.TRANSLUCENT) {
            bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_translucent)
            bubbleBackground.imageTintList = null
            bubbleIcon.setImageResource(hintIconTranslucentRes())
            bubbleIcon.imageTintList = null
            bubbleWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent)
            bubbleHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent)
        } else {
            bubbleWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width)
            bubbleHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height)
        }

        bubblePopup = TipWindowBubble(bubbleView, bubbleWidth, bubbleHeight, false)
        bubblePopup.isTouchable = true
        bubblePopup.isOutsideTouchable = true
        bubblePopup.setAttachedInDecor(false)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initBalloonPopup(mode: Mode) {
        balloonBubble = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble)
        balloonBubble.visibility = View.VISIBLE
        balloonBubbleHint = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble_hint)
        balloonBubbleIcon = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble_icon)
        balloonPanel = balloonView.findViewById(R.id.sem_tip_popup_balloon_panel)
        balloonPanel.visibility = View.GONE
        balloonContent = balloonView.findViewById(R.id.sem_tip_popup_balloon_content)
        balloonBg1 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_01)
        balloonBg2 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_02)

        if (mode == Mode.TRANSLUCENT) {
            balloonBg1.setBackgroundResource(R.drawable.sem_tip_popup_balloon_background_left_translucent)
            balloonBg1.backgroundTintList = null
            balloonBg2.setBackgroundResource(R.drawable.sem_tip_popup_balloon_background_right_translucent)
            balloonBg2.backgroundTintList = null
        }

        balloonPopup = TipWindowBalloon(balloonView, balloonWidth, balloonHeight, true)
        balloonPopup.isFocusable = true
        balloonPopup.isTouchable = true
        balloonPopup.isOutsideTouchable = true
        balloonPopup.setAttachedInDecor(false)
        balloonPopup.setTouchInterceptor { _, event ->
            if (needToCallParentViewsOnClick && parentView.hasOnClickListeners()
                && (event.action == MotionEvent.ACTION_DOWN
                        || event.action == MotionEvent.ACTION_OUTSIDE)
            ) {
                val outLocation = IntArray(2)
                parentView.getLocationOnScreen(outLocation)
                val parentViewBounds = Rect(
                    outLocation[0],
                    outLocation[1],
                    outLocation[0] + parentView.width,
                    outLocation[1] + parentView.height
                )
                if (parentViewBounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    debugLog("callOnClick for parent view")
                    parentView.callOnClick()
                }
            }
            false
        }
    }

    fun setOnStateChangeListener(changeListener: OnStateChangeListener?) {
        this.onStateChangeListener = changeListener
    }

    fun setOnDismissListener(onDismissListener: OnDismissListener?) {
        this.onDismissListener = onDismissListener
    }

    fun show() {
        show(Direction.DEFAULT, false)
    }

    fun show(direction: Direction) {
        show(direction, false)
    }

    fun show(direction: Direction, dismissOnTimeout: Boolean) {
        displayMetrics = resources.displayMetrics
        setInternal()
        setPositionsAndSizes(direction)
        setBubblePanel()
        setBalloonPanel()
        showInternal()
        if (state == State.HINT && dismissOnTimeout) {
            scheduleTimeout()
        }
    }

    private fun setPositionsAndSizes(direction: Direction) {
        if (arrowPositionX == -1 || arrowPositionY == -1) {
            calculateArrowPosition()
        }
        if (direction == Direction.DEFAULT) {
            calculateArrowDirection(arrowPositionX, arrowPositionY)
        } else {
            arrowDirection = direction
        }
        calculatePopupSize()
        calculatePopupPosition()
    }

    fun setMessage(message: CharSequence?) {
        this.messageText = message
    }

    fun setAction(actionText: CharSequence?, listener: View.OnClickListener?) {
        this.actionText = actionText
        this.actionClickListener = listener
    }

    fun semCallParentViewsOnClick(needToCall: Boolean) {
        this.needToCallParentViewsOnClick = needToCall
    }

    val isShowing: Boolean
        get() = bubblePopup.isShowing || balloonPopup.isShowing

    fun dismiss() {
        dismiss(true)
    }

    fun dismiss(withAnimation: Boolean) {
        bubblePopup.setUseDismissAnimation(withAnimation)
        debugLog("bubblePopup.isDismissing = " + bubblePopup.isDismissing)
        bubblePopup.dismiss()

        balloonPopup.setUseDismissAnimation(withAnimation)
        debugLog("balloonPopup.isDismissing = " + balloonPopup.isDismissing)
        balloonPopup.dismiss()

        onDismissListener?.onDismiss()
        handler?.removeCallbacksAndMessages(null)
        handler = null
    }

    fun setExpanded(expanded: Boolean) {
        if (expanded) {
            state = State.EXPANDED
            // scaleMargin is intentionally NOT zeroed here (the original library does zero it).
            // The expand animation uses an elastic interpolator that overshoots past 100%,
            // so the popup window needs spare room around the balloon; with a margin of 0
            // the window is exactly the balloon's size and the overshoot gets clipped.
            return
        }
        scaleMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin)
    }

    fun setTargetPosition(x: Int, y: Int) {
        if (x < 0 || y < 0) return

        isDefaultPosition = false
        arrowPositionX = x
        arrowPositionY = y
    }

    fun setHintDescription(hintDescription: CharSequence?) {
        this.hintDescription = hintDescription
    }

    fun update() {
        update(Direction.DEFAULT, false)
    }

    fun update(direction: Direction, dismissOnTimeout: Boolean) {
        if (!isShowing) {
            return
        }
        doOnNextLayout(parentView) {
            parentView.post {
                debugLog("update")
                resetBalloonProperties()
                displayMetrics = resources.displayMetrics
                setInternal()
                setPositionsAndSizes(direction)
                setBubblePanel()
                setBalloonPanel()
                when (state) {
                    State.EXPANDED ->
                        balloonPopup.update(
                            balloonPopupX, balloonPopupY,
                            balloonPopup.width, balloonPopup.height
                        )
                    State.HINT -> {
                        bubblePopup.update(
                            bubblePopupX, bubblePopupY,
                            bubblePopup.width, bubblePopup.height
                        )
                        if (dismissOnTimeout) {
                            debugLog("Timer Reset!")
                            scheduleTimeout()
                        }
                    }
                    State.DISMISSED -> {
                    }
                }
            }
        }
    }

    private fun resetBalloonProperties() {
        balloonX = -1
        balloonY = -1
        isMessageViewMeasured = false
        resetRotation(balloonBg1)
        resetRotation(balloonBg2)
        resetRotation(balloonBubbleHint)
        if (isDefaultPosition) {
            arrowPositionX = -1
            arrowPositionY = -1
        }
    }

    fun setMessageTextColor(@ColorInt color: Int) {
        messageTextColor = opaque(color)
    }

    fun setActionTextColor(@ColorInt color: Int) {
        actionTextColor = opaque(color)
    }

    fun setBackgroundColor(@ColorInt color: Int) {
        backgroundColor = opaque(color)
    }

    fun setBackgroundColorWithAlpha(@ColorInt color: Int) {
        backgroundColor = color
    }

    fun setIconTint(@ColorInt color: Int) {
        iconColor = opaque(color)
    }

    fun setOutsideTouchEnabled(enabled: Boolean) {
        bubblePopup.isFocusable = enabled
        bubblePopup.isOutsideTouchable = enabled
        balloonPopup.isFocusable = enabled
        balloonPopup.isOutsideTouchable = enabled
        debugLog("outside enabled : $enabled")
    }

    // Added: when enabled, the popup windows are flagged as not interacting with the input method, so
    // showing the popup over a window whose soft keyboard is open does not close the keyboard.
    fun setKeepSoftInputEnabled(enabled: Boolean) {
        val inputMethodMode = if (enabled) PopupWindow.INPUT_METHOD_NOT_NEEDED else PopupWindow.INPUT_METHOD_FROM_FOCUSABLE
        bubblePopup.inputMethodMode = inputMethodMode
        balloonPopup.inputMethodMode = inputMethodMode
    }

    fun setPopupWindowClippingEnabled(enabled: Boolean) {
        bubblePopup.isClippingEnabled = enabled
        balloonPopup.isClippingEnabled = enabled
        forceRealDisplay = !enabled
        sideMargin = if (enabled) resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin) else 0
        debugLog("clipping enabled : $enabled")
    }

    private fun setInternal() {
        if (handler == null) {
            handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(message: Message) {
                    when (message.what) {
                        MSG_TIMEOUT -> dismissBubble(true)
                        MSG_DISMISS -> dismissBubble(false)
                        MSG_SCALE_UP -> animateScaleUp()
                        else -> {
                        }
                    }
                }
            }
        }

        val currentFontScale = resources.configuration.fontScale
        val messageTextSize = resources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_message_text_size)
        val actionTextSize = resources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_action_text_size)

        if (currentFontScale > 1.2f) {
            messageView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                floor(ceil(messageTextSize / currentFontScale) * 1.2f)
            )
            actionView.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                floor(ceil(actionTextSize / currentFontScale) * 1.2f)
            )
        }

        messageView.text = messageText

        if (TextUtils.isEmpty(actionText) || actionClickListener == null) {
            actionView.visibility = View.GONE
            actionView.setOnClickListener(null)
            type = Type.BALLOON_SIMPLE
        } else {
            actionView.visibility = View.VISIBLE
            actionView.text = actionText
            actionView.setOnClickListener { view ->
                actionClickListener?.onClick(view)
                dismiss(true)
            }
            type = Type.BALLOON_ACTION
        }

        bubbleIcon.contentDescription = hintDescription

        if (mode == Mode.TRANSLUCENT) return

        val msgColor = messageTextColor
        if (msgColor != null) {
            messageView.setTextColor(msgColor)
        }

        val actColor = actionTextColor
        if (actColor != null) {
            actionView.setTextColor(actColor)
        }

        bubbleBackground.setColorFilter(backgroundColor)
        balloonBubbleHint.setColorFilter(backgroundColor)

        balloonBg1.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        balloonBg2.backgroundTintList = ColorStateList.valueOf(backgroundColor)

        val tint = iconColor
        if (tint != null) {
            bubbleIcon.setColorFilter(tint)
            balloonBubbleIcon.setColorFilter(tint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showInternal() {
        if (state != State.EXPANDED) {
            state = State.HINT
            val listener = onStateChangeListener
            if (listener != null) {
                listener.onStateChanged(State.HINT)
                debugLog("isShowing : $isShowing")
            }

            bubblePopup.showAtLocation(parentView, 0, bubblePopupX, bubblePopupY)
            animateViewIn()

            bubbleView.setOnTouchListener { _, _ ->
                state = State.EXPANDED
                onStateChangeListener?.onStateChanged(state)
                balloonPopup.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY)
                val h = handler
                if (h != null) {
                    h.removeMessages(0)
                    h.sendMessageDelayed(Message.obtain(h, MSG_DISMISS), 10L)
                    h.sendMessageDelayed(Message.obtain(h, MSG_SCALE_UP), 20L)
                }
                false
            }
        } else {
            balloonBubble.visibility = View.GONE
            balloonPanel.visibility = View.VISIBLE
            messageView.visibility = View.VISIBLE
            onStateChangeListener?.onStateChanged(state)
            balloonPopup.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY)
            animateBalloonScaleUp()
        }

        balloonView.setOnTouchListener { _, _ ->
            if (type == Type.BALLOON_SIMPLE) {
                dismiss(true)
                return@setOnTouchListener false
            }
            false
        }
    }

    private fun isRtlLocale(): Boolean {
        return isRTL() && "iw_IL" != getLocale()
    }

    private fun hintIconRes(): Int {
        return if (isRtlLocale())
            R.drawable.sem_tip_popup_hint_icon_rtl
        else
            R.drawable.sem_tip_popup_hint_icon
    }

    private fun hintIconTranslucentRes(): Int {
        return if (isRtlLocale())
            R.drawable.sem_tip_popup_hint_icon_translucent_rtl
        else
            R.drawable.sem_tip_popup_hint_icon_translucent
    }

    private fun setBubblePanel() {
        val paramBubblePanel = bubbleBackground.layoutParams as FrameLayout.LayoutParams
        if (mode == Mode.TRANSLUCENT) {
            paramBubblePanel.width = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent)
            paramBubblePanel.height = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent)
        }
        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                bubblePopup.setPivot(bubblePopup.width.toFloat(), bubblePopup.height.toFloat())
                paramBubblePanel.gravity = Gravity.BOTTOM or Gravity.END
                bubblePopupX = bubbleX - (scaleMargin * 2)
                bubblePopupY = bubbleY - (scaleMargin * 2)
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_03)
                    bubbleIcon.setImageResource(hintIconRes())
                } else {
                    bubbleBackground.rotationX = 180.0f
                }
            }

            Direction.TOP_RIGHT -> {
                bubblePopup.setPivot(0.0f, bubblePopup.height.toFloat())
                paramBubblePanel.gravity = Gravity.BOTTOM or Gravity.START
                bubblePopupX = bubbleX
                bubblePopupY = bubbleY - (scaleMargin * 2)
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_04)
                    bubbleIcon.setImageResource(hintIconRes())
                } else {
                    bubbleBackground.rotation = 180.0f
                }
            }

            Direction.BOTTOM_LEFT -> {
                bubblePopup.setPivot(bubblePopup.width.toFloat(), 0.0f)
                paramBubblePanel.gravity = Gravity.TOP or Gravity.END
                bubblePopupX = bubbleX - (scaleMargin * 2)
                bubblePopupY = bubbleY
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_01)
                    bubbleIcon.setImageResource(hintIconRes())
                }
            }

            Direction.BOTTOM_RIGHT -> {
                bubblePopup.setPivot(0.0f, 0.0f)
                paramBubblePanel.gravity = Gravity.TOP or Gravity.START
                bubblePopupX = bubbleX
                bubblePopupY = bubbleY
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_02)
                    bubbleIcon.setImageResource(hintIconRes())
                } else {
                    bubbleBackground.rotationY = 180.0f
                }
            }

            Direction.DEFAULT -> throw IllegalStateException()
        }
        bubbleBackground.layoutParams = paramBubblePanel
        bubbleIcon.layoutParams = paramBubblePanel
        bubblePopup.width = bubbleWidth + (scaleMargin * 2)
        bubblePopup.height = bubbleHeight + (scaleMargin * 2)
    }


    private fun setBalloonPanel() {
        debugLog("setBalloonPanel()")

        val leftMargin = bubbleX - balloonX
        val rightMargin = (balloonX + balloonWidth) - bubbleX
        val topMargin = bubbleY - balloonY
        val bottomMargin = (balloonY + balloonHeight) - (bubbleY + bubbleHeight)

        val minBackgroundWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_background_minwidth)

        debugLog("leftMargin[$leftMargin]")
        debugLog("rightMargin[$rightMargin] balloonWidth[$balloonWidth]")

        val horizontalContentMargin = horizontalTextMargin -
            resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal)
        val verticalButtonPadding = if (actionView.visibility == View.VISIBLE) {
            resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical)
        } else {
            0
        }
        val paramBalloonBubble = balloonBubble.layoutParams as FrameLayout.LayoutParams
        val paramBalloonPanel = balloonPanel.layoutParams as FrameLayout.LayoutParams
        val paramBalloonContent = balloonContent.layoutParams as FrameLayout.LayoutParams
        val paramBalloonBg1 = balloonBg1.layoutParams as FrameLayout.LayoutParams
        val paramBalloonBg2 = balloonBg2.layoutParams as FrameLayout.LayoutParams

        val scaleFactor: Int
        if (mode == Mode.TRANSLUCENT) {
            balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_translucent)
            balloonBubbleHint.imageTintList = null
            balloonBubbleIcon.setImageResource(hintIconTranslucentRes())
            balloonBubbleIcon.imageTintList = null
            paramBalloonBubble.width = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent)
            paramBalloonBubble.height = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent)
            scaleFactor = 0
        } else if (Color.alpha(backgroundColor) < 255) {
            debugLog("Updating scaleFactor to 0 because transparency is applied to background.")
            scaleFactor = 0
        } else {
            val realMetrics = DisplayMetrics()
            getRealMetrics(realMetrics)
            scaleFactor = ceil(realMetrics.density.toDouble()).toInt()
        }

        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                balloonPopup.setPivot(
                    ((arrowPositionX - balloonX) + scaleMargin).toFloat(),
                    (balloonHeight + scaleMargin).toFloat()
                )

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_03)
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon)
                } else {
                    balloonBubbleHint.rotationX = 180.0f
                }

                balloonBg1.rotationX = 180.0f
                balloonBg2.rotationX = 180.0f
                paramBalloonBg2.gravity = Gravity.BOTTOM or Gravity.END
                paramBalloonBg1.gravity = Gravity.BOTTOM or Gravity.END
                paramBalloonBubble.gravity = Gravity.BOTTOM or Gravity.END

                if (rightMargin - bubbleWidth < minBackgroundWidth) {
                    val scaledLeftMargin = balloonWidth - minBackgroundWidth
                    paramBalloonBg1.setMargins(0, 0, minBackgroundWidth, 0)
                    paramBalloonBg2.setMargins(scaledLeftMargin - scaleFactor, 0, 0, 0)
                    debugLog("Right Margin is less then minimum background width!")
                    debugLog("updated !! leftMargin[$scaledLeftMargin],  rightMargin[$minBackgroundWidth]")
                } else {
                    paramBalloonBg1.setMargins(0, 0, rightMargin - bubbleWidth, 0)
                    paramBalloonBg2.setMargins(bubbleWidth + leftMargin, 0, 0, 0)
                }

                paramBalloonContent.setMargins(
                    horizontalContentMargin,
                    verticalTextMargin,
                    horizontalContentMargin,
                    (arrowHeight + verticalTextMargin) - verticalButtonPadding
                )
            }

            Direction.TOP_RIGHT -> {
                balloonPopup.setPivot(
                    (arrowPositionX - balloonX + scaleMargin).toFloat(),
                    (balloonHeight + scaleMargin).toFloat()
                )

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_04)
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon)
                } else {
                    balloonBubbleHint.rotation = 180.0f
                }

                balloonBg1.rotation = 180.0f
                balloonBg2.rotation = 180.0f
                paramBalloonBg2.gravity = Gravity.BOTTOM or Gravity.START
                paramBalloonBg1.gravity = Gravity.BOTTOM or Gravity.START
                paramBalloonBubble.gravity = Gravity.BOTTOM or Gravity.START

                if (leftMargin < minBackgroundWidth) {
                    val scaledRightMargin = balloonWidth - minBackgroundWidth
                    paramBalloonBg1.setMargins(minBackgroundWidth, 0, 0, 0)
                    paramBalloonBg2.setMargins(0, 0, scaledRightMargin - scaleFactor, 0)
                    debugLog("Left Margin is less then minimum background width!")
                    debugLog("updated !! leftMargin[$minBackgroundWidth],  rightMargin[]")
                } else {
                    paramBalloonBg1.setMargins(leftMargin, 0, 0, 0)
                    paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0)
                }

                paramBalloonContent.setMargins(
                    horizontalContentMargin,
                    verticalTextMargin,
                    horizontalContentMargin,
                    (arrowHeight + verticalTextMargin) - verticalButtonPadding
                )
            }

            Direction.BOTTOM_LEFT -> {
                balloonPopup.setPivot(
                    (arrowPositionX - balloonX + scaleMargin).toFloat(),
                    scaleMargin.toFloat()
                )

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_01)
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon)
                }

                paramBalloonBg2.gravity = Gravity.TOP or Gravity.END
                paramBalloonBg1.gravity = Gravity.TOP or Gravity.END
                paramBalloonBubble.gravity = Gravity.TOP or Gravity.END
                paramBalloonBg1.setMargins(0, 0, rightMargin - bubbleWidth, 0)
                paramBalloonBg2.setMargins((bubbleWidth + leftMargin) - scaleFactor, 0, 0, 0)

                paramBalloonContent.setMargins(
                    horizontalContentMargin,
                    arrowHeight + verticalTextMargin,
                    horizontalContentMargin,
                    verticalTextMargin - verticalButtonPadding
                )
            }

            Direction.BOTTOM_RIGHT -> {
                balloonPopup.setPivot(
                    (arrowPositionX - balloonX + scaleMargin).toFloat(),
                    scaleMargin.toFloat()
                )

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_02)
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon)
                } else {
                    balloonBubbleHint.rotationY = 180.0f
                }

                balloonBg1.rotationY = 180.0f
                balloonBg2.rotationY = 180.0f
                paramBalloonBg2.gravity = Gravity.TOP or Gravity.START
                paramBalloonBg1.gravity = Gravity.TOP or Gravity.START
                paramBalloonBubble.gravity = Gravity.TOP or Gravity.START
                paramBalloonBg1.setMargins(leftMargin, 0, 0, 0)
                paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0)

                paramBalloonContent.setMargins(
                    horizontalContentMargin,
                    arrowHeight + verticalTextMargin,
                    horizontalContentMargin,
                    verticalTextMargin - verticalButtonPadding
                )
            }

            Direction.DEFAULT -> {
            }
        }

        paramBalloonBubble.setMargins(
            leftMargin + scaleMargin,
            topMargin + scaleMargin,
            (rightMargin - bubbleWidth) + scaleMargin,
            bottomMargin + scaleMargin
        )

        paramBalloonPanel.setMargins(scaleMargin, scaleMargin, scaleMargin, scaleMargin)

        balloonPopupX = balloonX - scaleMargin
        balloonPopupY = balloonY - scaleMargin

        balloonBubble.layoutParams = paramBalloonBubble
        balloonPanel.layoutParams = paramBalloonPanel
        balloonBg1.layoutParams = paramBalloonBg1
        balloonBg2.layoutParams = paramBalloonBg2
        balloonContent.layoutParams = paramBalloonContent

        balloonPopup.width = balloonWidth + (scaleMargin * 2)
        balloonPopup.height = balloonHeight + (scaleMargin * 2)
    }

    private fun calculateArrowDirection(arrowX: Int, arrowY: Int) {
        if (isDefaultPosition) {
            val location = IntArray(2)
            parentView.getLocationInWindow(location)
            val parentY = location[1] + (parentView.height / 2)

            arrowDirection = if (arrowX * 2 <= displayMetrics.widthPixels) {
                if (arrowY <= parentY) Direction.TOP_RIGHT else Direction.BOTTOM_RIGHT
            } else {
                if (arrowY <= parentY) Direction.TOP_LEFT else Direction.BOTTOM_LEFT
            }
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = Direction.BOTTOM_RIGHT
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_LEFT
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_RIGHT
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_LEFT
        }
        debugLog("calculateArrowDirection : arrow position ($arrowX, $arrowY) / arrowDirection = $arrowDirection")
    }

    private fun calculateArrowPosition() {
        val location = IntArray(2)
        parentView.getLocationInWindow(location)
        debugLog("calculateArrowPosition anchor location : ${location[0]}, ${location[1]}")

        arrowPositionX = location[0] + (parentView.width / 2)
        val y = location[1] + (parentView.height / 2)
        arrowPositionY = if (y * 2 <= displayMetrics.heightPixels) {
            (parentView.height / 2) + y
        } else {
            y - (parentView.height / 2)
        }
        debugLog("calculateArrowPosition arrowPosition : $arrowPositionX, $arrowPositionY")
    }

    private fun calculatePopupSize() {
        val balloonMaxWidth: Int
        if (isDeskTopMode()) {
            val windowLocation = IntArray(2)
            val rootView = parentView.rootView
            rootView.getLocationOnScreen(windowLocation)
            val windowWidthInDexMode = rootView.measuredWidth + minOf(windowLocation[0], 0)
            debugLog("Window width in DexMode $windowWidthInDexMode")
            balloonMaxWidth = maxWidthFor(windowWidthInDexMode, windowWidthInDexMode)
        } else {
            val screenWidthDp = resources.configuration.screenWidthDp
            debugLog("screen width DP $screenWidthDp")
            balloonMaxWidth = maxWidthFor(screenWidthDp, displayMetrics.widthPixels)
        }

        if (!isMessageViewMeasured) {
            messageView.measure(0, 0)
            initialMessageViewWidth = messageView.measuredWidth
            isMessageViewMeasured = true
        }

        val balloonMinWidth = arrowWidth + (horizontalTextMargin * 2)

        balloonWidth = maxOf(
            balloonMinWidth,
            minOf(initialMessageViewWidth + (horizontalTextMargin * 2), balloonMaxWidth)
        )

        messageView.width = balloonWidth - (horizontalTextMargin * 2)
        messageView.measure(0, 0)
        balloonHeight = messageView.measuredHeight + (verticalTextMargin * 2) + arrowHeight

        if (type == Type.BALLOON_ACTION) {
            actionView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            balloonWidth = maxOf(
                balloonWidth,
                actionView.measuredWidth +
                    (resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal) * 2)
            )
            balloonHeight += (actionView.measuredHeight -
                resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical))
        }
    }

    private fun calculatePopupPosition() {
        getDisplayFrame(displayFrame)

        if (balloonX < 0) {
            balloonX = if (arrowDirection == Direction.BOTTOM_RIGHT || arrowDirection == Direction.TOP_RIGHT) {
                (arrowPositionX + arrowWidth) - (balloonWidth / 2)
            } else {
                (arrowPositionX - arrowWidth) - (balloonWidth / 2)
            }
        }

        if (arrowDirection == Direction.BOTTOM_RIGHT || arrowDirection == Direction.TOP_RIGHT) {
            val minX = displayFrame.left + sideMargin + horizontalTextMargin
            val maxX = maxOf(minX, (displayFrame.right - sideMargin) - horizontalTextMargin - arrowWidth)
            arrowPositionX = coerceIn(arrowPositionX, minX, maxX)
        } else {
            val minX = displayFrame.left + sideMargin + horizontalTextMargin + arrowWidth
            val maxX = maxOf(minX, (displayFrame.right - sideMargin) - horizontalTextMargin)
            arrowPositionX = coerceIn(arrowPositionX, minX, maxX)
        }

        if (isDeskTopMode()) {
            val windowLocation = IntArray(2)
            val rootView = parentView.rootView
            rootView.getLocationOnScreen(windowLocation)
            val windowWidthInDexMode = if (windowLocation[0] < 0) {
                rootView.measuredWidth + windowLocation[0]
            } else {
                rootView.measuredWidth
            }
            val minX = displayFrame.left + sideMargin
            val maxX = maxOf(
                minX,
                (windowWidthInDexMode - sideMargin) - balloonWidth - minOf(windowLocation[0], 0)
            )
            balloonX = coerceIn(balloonX, minX, maxX)
        } else {
            val minX = displayFrame.left + sideMargin
            val maxX = maxOf(minX, (displayFrame.right - sideMargin) - balloonWidth)
            balloonX = coerceIn(balloonX, minX, maxX)
        }

        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                bubbleX = arrowPositionX - bubbleWidth
                bubbleY = arrowPositionY - bubbleHeight
                balloonY = arrowPositionY - balloonHeight
            }

            Direction.TOP_RIGHT -> {
                bubbleX = arrowPositionX
                bubbleY = arrowPositionY - bubbleHeight
                balloonY = arrowPositionY - balloonHeight
            }

            Direction.BOTTOM_LEFT -> {
                bubbleX = arrowPositionX - bubbleWidth
                bubbleY = arrowPositionY
                balloonY = arrowPositionY
            }

            Direction.BOTTOM_RIGHT -> {
                bubbleX = arrowPositionX
                bubbleY = arrowPositionY
                balloonY = arrowPositionY
            }

            Direction.DEFAULT -> throw IllegalStateException()
        }

        debugLog("QuestionPopup : $bubbleX, $bubbleY, $bubbleWidth, $bubbleHeight")
        debugLog("BalloonPopup : $balloonX, $balloonY, $balloonWidth, $balloonHeight")
    }

    private fun dismissBubble(withAnimation: Boolean) {
        bubblePopup.setUseDismissAnimation(withAnimation)
        bubblePopup.dismiss()
        onDismissListener?.onDismiss()
    }

    private fun scheduleTimeout() {
        val h = handler ?: return
        h.removeMessages(0)
        h.sendMessageDelayed(Message.obtain(h, MSG_TIMEOUT), TIMEOUT_DURATION_MS)
    }

    private fun animateViewIn() {
        val pivotX: Float
        val pivotY: Float
        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                pivotX = 1.0f
                pivotY = 1.0f
            }
            Direction.TOP_RIGHT -> {
                pivotX = 0.0f
                pivotY = 1.0f
            }
            Direction.BOTTOM_LEFT -> {
                pivotX = 1.0f
                pivotY = 0.0f
            }
            Direction.BOTTOM_RIGHT -> {
                pivotX = 0.0f
                pivotY = 0.0f
            }
            Direction.DEFAULT -> throw IllegalStateException()
        }

        val animScale = ScaleAnimation(
            0.0f, 1.0f, 0.0f, 1.0f,
            Animation.RELATIVE_TO_SELF, pivotX, Animation.RELATIVE_TO_SELF, pivotY
        )
        animScale.interpolator = interpolatorElastic50
        animScale.duration = 500L
        listen(animScale, null) {
            scheduleTimeout()
            animateBounce()
        }
        bubbleView.startAnimation(animScale)
    }

    private fun animateBounce() {
        val pivotX: Float
        val pivotY: Float
        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                pivotX = bubblePopup.width.toFloat()
                pivotY = bubblePopup.height.toFloat()
            }
            Direction.TOP_RIGHT -> {
                pivotX = 0.0f
                pivotY = bubblePopup.height.toFloat()
            }
            Direction.BOTTOM_LEFT -> {
                pivotX = bubblePopup.width.toFloat()
                pivotY = 0.0f
            }
            Direction.BOTTOM_RIGHT -> {
                pivotX = 0.0f
                pivotY = 0.0f
            }
            Direction.DEFAULT -> throw IllegalStateException()
        }

        val animationSet = AnimationSet(false)

        val scaleAnimation1 = ScaleAnimation(
            1.0f, 1.2f, 1.0f, 1.2f,
            Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY
        )
        scaleAnimation1.duration = ANIMATION_DURATION_BOUNCE_SCALE1
        scaleAnimation1.interpolator = interpolatorSineInOut70

        val scaleAnimation2 = ScaleAnimation(
            1.0f, 0.833f, 1.0f, 0.833f,
            Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY
        )
        scaleAnimation2.startOffset = ANIMATION_DURATION_BOUNCE_SCALE1
        scaleAnimation2.duration = ANIMATION_DURATION_BOUNCE_SCALE2
        scaleAnimation2.interpolator = interpolatorSineInOut33
        var count = 0
        listen(scaleAnimation2, { count++ }) {
            debugLog("repeat count $count")
            bubbleView.startAnimation(animationSet)
        }

        animationSet.addAnimation(scaleAnimation1)
        animationSet.addAnimation(scaleAnimation2)
        animationSet.startOffset = ANIMATION_OFFSET_BOUNCE_SCALE
        bubbleView.startAnimation(animationSet)
    }

    private fun animateScaleUp() {
        val deltaHintY: Float
        val pivotHintX: Float
        val pivotHintY: Float
        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                pivotHintX = balloonBubble.width.toFloat()
                pivotHintY = balloonBubble.height.toFloat()
                deltaHintY = 0.0f - (arrowHeight / 2.0f)
            }
            Direction.TOP_RIGHT -> {
                pivotHintX = 0.0f
                pivotHintY = balloonBubble.height.toFloat()
                deltaHintY = 0.0f - (arrowHeight / 2.0f)
            }
            Direction.BOTTOM_LEFT -> {
                pivotHintX = balloonBubble.width.toFloat()
                pivotHintY = 0.0f
                deltaHintY = arrowHeight / 2.0f
            }
            Direction.BOTTOM_RIGHT,
            Direction.DEFAULT -> {
                pivotHintX = 0.0f
                pivotHintY = 0.0f
                deltaHintY = arrowHeight / 2.0f
            }
        }

        val animationBubble = AnimationSet(false)

        val translate = TranslateAnimation(
            Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, 0.0f,
            Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, deltaHintY
        )
        translate.duration = ANIMATION_DURATION_EXPAND_SCALE
        translate.interpolator = interpolatorElasticCustom
        animationBubble.addAnimation(translate)

        val scale = ScaleAnimation(
            1.0f, 1.5f, 1.0f, 1.5f,
            Animation.ABSOLUTE, pivotHintX, Animation.ABSOLUTE, pivotHintY
        )
        scale.duration = ANIMATION_DURATION_EXPAND_SCALE
        scale.interpolator = interpolatorElastic50
        animationBubble.addAnimation(scale)

        val alpha = AlphaAnimation(1.0f, 0.0f)
        alpha.duration = ANIMATION_DURATION_EXPAND_TEXT
        alpha.interpolator = interpolatorSineInOut70
        animationBubble.addAnimation(alpha)

        listen(
            animationBubble,
            { balloonPanel.visibility = View.VISIBLE },
            { balloonBubble.visibility = View.GONE }
        )

        balloonBubble.startAnimation(animationBubble)
        animateBalloonScaleUp()
    }

    private fun animateBalloonScaleUp() {
        val pivotPanelX: Float
        val pivotPanelY: Float
        val questionHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height)
        // Integer division is intentional: it mirrors the original Java behaviour.
        val panelScale = (questionHeight / balloonHeight).toFloat()

        when (arrowDirection) {
            Direction.TOP_LEFT -> {
                pivotPanelX = (arrowPositionX - balloonX).toFloat()
                pivotPanelY = balloonHeight.toFloat()
            }
            Direction.TOP_RIGHT -> {
                pivotPanelX = (bubbleX - balloonX).toFloat()
                pivotPanelY = balloonHeight.toFloat()
            }
            Direction.BOTTOM_LEFT -> {
                pivotPanelX = (arrowPositionX - balloonX).toFloat()
                pivotPanelY = 0.0f
            }
            Direction.BOTTOM_RIGHT -> {
                pivotPanelX = (bubbleX - balloonX).toFloat()
                pivotPanelY = 0.0f
            }
            Direction.DEFAULT -> {
                pivotPanelX = 0.0f
                pivotPanelY = 0.0f
            }
        }

        val animationPanel = AnimationSet(false)

        val panelScaleAnimation = ScaleAnimation(
            0.32f, 1.0f, panelScale, 1.0f,
            Animation.ABSOLUTE, pivotPanelX, Animation.ABSOLUTE, pivotPanelY
        )
        panelScaleAnimation.interpolator = interpolatorElasticCustom
        panelScaleAnimation.duration = ANIMATION_DURATION_SHOW_SCALE
        animationPanel.addAnimation(panelScaleAnimation)

        val panelAlpha = AlphaAnimation(0.0f, 1.0f)
        panelAlpha.interpolator = interpolatorSineInOut70
        panelAlpha.duration = ANIMATION_DURATION_EXPAND_ALPHA
        animationPanel.addAnimation(panelAlpha)

        balloonPanel.startAnimation(animationPanel)

        val animationText = AlphaAnimation(0.0f, 1.0f)
        animationText.interpolator = interpolatorSineInOut33
        animationText.startOffset = ANIMATION_OFFSET_EXPAND_TEXT
        animationText.duration = ANIMATION_DURATION_EXPAND_TEXT
        listen(
            animationText,
            { messageView.visibility = View.VISIBLE },
            {
                bubblePopup.setUseDismissAnimation(false)
                bubblePopup.dismiss()
            }
        )
        messageView.startAnimation(animationText)
        actionView.startAnimation(animationText)
    }

    private fun isNavigationbarHide(): Boolean {
        return Settings.Global.getInt(context.contentResolver, "navigationbar_hide_bar_enabled", 0) == 1
    }

    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    private fun getNavigationbarHeight(): Int {
        val identifier = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (identifier > 0) resources.getDimensionPixelSize(identifier) else 0
    }

    private fun isTablet(): Boolean {
        return resources.configuration.smallestScreenWidthDp >= 600
    }

    private fun isDeskTopMode(): Boolean {
        return try {
            SeslConfigurationReflector.isDexEnabled(resources.configuration)
        } catch (ignored: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun getRealMetrics(outMetrics: DisplayMetrics) {
        windowManager.defaultDisplay.getRealMetrics(outMetrics)
    }

    @Suppress("DEPRECATION")
    private fun getDisplayFrame(screenRect: Rect) {
        val navigationbarHeight = getNavigationbarHeight()
        val navigationbarHide = isNavigationbarHide()

        val displayRotation = windowManager.defaultDisplay.rotation

        val realMetrics = DisplayMetrics()
        getRealMetrics(realMetrics)

        debugLog("realMetrics = $realMetrics")
        debugLog("is tablet? = ${isTablet()}")

        if (forceRealDisplay) {
            screenRect.left = 0
            screenRect.top = 0
            screenRect.right = realMetrics.widthPixels
            screenRect.bottom = realMetrics.heightPixels
            debugLog("Screen Rect = $screenRect forceRealDisplay = $forceRealDisplay")
            return
        }
        screenRect.left = 0
        screenRect.top = 0
        screenRect.right = displayMetrics.widthPixels
        screenRect.bottom = displayMetrics.heightPixels

        val bounds = Rect()
        parentView.rootView.getWindowVisibleDisplayFrame(bounds)
        debugLog("Bounds = $bounds")
        if (isTablet()) {
            debugLog("tablet")
            if (realMetrics.widthPixels == displayMetrics.widthPixels &&
                realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight &&
                navigationbarHide
            ) {
                screenRect.bottom += navigationbarHeight
            }
        } else {
            debugLog("phone")
            when (displayRotation) {
                Surface.ROTATION_0 -> {
                    if (realMetrics.widthPixels == displayMetrics.widthPixels &&
                        realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight &&
                        navigationbarHide
                    ) {
                        screenRect.bottom += navigationbarHeight
                    }
                }

                Surface.ROTATION_90 -> {
                    if (realMetrics.heightPixels == displayMetrics.heightPixels &&
                        realMetrics.widthPixels - displayMetrics.widthPixels == navigationbarHeight &&
                        navigationbarHide
                    ) {
                        screenRect.right += navigationbarHeight
                    }
                    val windowInsets: WindowInsets? = parentView.rootWindowInsets
                    if (windowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val cutout: DisplayCutout? = windowInsets.displayCutout
                        if (cutout != null) {
                            screenRect.left += cutout.safeInsetLeft
                            screenRect.right += cutout.safeInsetLeft
                            debugLog("displayCutout.getSafeInsetLeft() :  ${cutout.safeInsetLeft}")
                        }
                    }
                }

                Surface.ROTATION_180 -> {
                    if (realMetrics.widthPixels == displayMetrics.widthPixels &&
                        realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight
                    ) {
                        if (navigationbarHide) {
                            screenRect.bottom += navigationbarHeight
                        } else {
                            screenRect.top += navigationbarHeight
                            screenRect.bottom += navigationbarHeight
                        }
                    } else if (realMetrics.widthPixels == displayMetrics.widthPixels &&
                        bounds.top == navigationbarHeight
                    ) {
                        debugLog("Top Docked")
                        screenRect.top += navigationbarHeight
                        screenRect.bottom += navigationbarHeight
                    }
                }

                Surface.ROTATION_270 -> {
                    if (realMetrics.heightPixels == displayMetrics.heightPixels &&
                        realMetrics.widthPixels - displayMetrics.widthPixels == navigationbarHeight
                    ) {
                        if (navigationbarHide) {
                            screenRect.right += navigationbarHeight
                        } else {
                            screenRect.left += navigationbarHeight
                            screenRect.right += navigationbarHeight
                        }
                    } else if (realMetrics.heightPixels == displayMetrics.heightPixels &&
                        bounds.left == navigationbarHeight
                    ) {
                        debugLog("Left Docked")
                        screenRect.left += navigationbarHeight
                        screenRect.right += navigationbarHeight
                    }
                }

                else -> {
                }
            }
        }
        debugLog("Screen Rect = $screenRect")
    }

    fun semGetBubblePopupWindow(): PopupWindow = bubblePopup

    fun semGetBalloonPopupWindow(): PopupWindow = balloonPopup

    private fun isRTL(): Boolean {
        return resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    private fun getLocale(): String {
        return resources.configuration.locales[0].toString()
    }

    internal open class TipWindow(
        contentView: View,
        width: Int,
        height: Int,
        focusable: Boolean
    ) : PopupWindow(contentView, width, height, focusable) {
        var isDismissing = false
        private var isUsingDismissAnimation = true
        var pivotX = 0.0f
        var pivotY = 0.0f

        fun setUseDismissAnimation(useAnimation: Boolean) {
            isUsingDismissAnimation = useAnimation
        }

        fun setPivot(pivotX: Float, pivotY: Float) {
            this.pivotX = pivotX
            this.pivotY = pivotY
        }

        override fun dismiss() {
            if (isUsingDismissAnimation && !isDismissing) {
                animateViewOut()
            } else {
                super.dismiss()
            }
        }

        fun dismissFinal() {
            super.dismiss()
        }

        open fun animateViewOut() {
        }
    }

    private class TipWindowBubble(
        contentView: View,
        width: Int,
        height: Int,
        focusable: Boolean
    ) : TipWindow(contentView, width, height, focusable) {

        override fun animateViewOut() {
            val animationSet = AnimationSet(true)

            val scale = ScaleAnimation(
                1.0f, 0.81f, 1.0f, 0.81f,
                Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY
            )
            scale.interpolator = interpolatorElasticCustom
            scale.duration = ANIMATION_DURATION_DISMISS_SCALE
            animationSet.addAnimation(scale)

            val alpha = AlphaAnimation(1.0f, 0.0f)
            alpha.interpolator = interpolatorSineInOut33
            alpha.duration = ANIMATION_DURATION_DISMISS_ALPHA
            animationSet.addAnimation(alpha)

            listen(animationSet, { isDismissing = true }) { dismissFinal() }
            contentView.startAnimation(animationSet)
        }
    }

    private class TipWindowBalloon(
        contentView: View,
        width: Int,
        height: Int,
        focusable: Boolean
    ) : TipWindow(contentView, width, height, focusable) {

        override fun animateViewOut() {
            val messageView = contentView.findViewById<View>(R.id.sem_tip_popup_message)

            val animAlpha = AlphaAnimation(1.0f, 0.0f)
            animAlpha.duration = ANIMATION_DURATION_EXPAND_SCALE

            val animationSet = AnimationSet(true)

            val scale = ScaleAnimation(
                1.0f, 0.32f, 1.0f, 0.32f,
                Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY
            )
            scale.duration = ANIMATION_DURATION_DISMISS_SCALE
            scale.interpolator = interpolatorElasticCustom
            animationSet.addAnimation(scale)
            animationSet.addAnimation(animAlpha)

            listen(animationSet, { isDismissing = true }) { dismissFinal() }
            contentView.startAnimation(animationSet)
            messageView.startAnimation(animAlpha)
        }
    }

    companion object {
        private const val ANIMATION_DURATION_BOUNCE_SCALE1 = 167L
        private const val ANIMATION_DURATION_BOUNCE_SCALE2 = 250L
        private const val ANIMATION_DURATION_DISMISS_ALPHA = 167L
        private const val ANIMATION_DURATION_DISMISS_SCALE = 167L
        private const val ANIMATION_DURATION_EXPAND_ALPHA = 83L
        private const val ANIMATION_DURATION_EXPAND_SCALE = 500L
        private const val ANIMATION_DURATION_EXPAND_TEXT = 167L
        private const val ANIMATION_DURATION_SHOW_SCALE = 500L
        private const val ANIMATION_OFFSET_BOUNCE_SCALE = 3000L
        private const val ANIMATION_OFFSET_EXPAND_TEXT = 333L

        private const val MSG_TIMEOUT = 0
        private const val MSG_DISMISS = 1
        private const val MSG_SCALE_UP = 2
        private const val TAG = "OneUiTipPopup"
        private const val TIMEOUT_DURATION_MS = 7100L

        private var interpolatorSineInOut33: Interpolator? = null
        private var interpolatorSineInOut70: Interpolator? = null
        private var interpolatorElastic50: Interpolator? = null
        private var interpolatorElasticCustom: Interpolator? = null

        @SuppressLint("PrivateApi")
        private fun createElasticInterpolator(amplitude: Float, period: Float): Interpolator {
            try {
                val constructor = Class.forName("androidx.appcompat.animation.SeslElasticInterpolator")
                    .getDeclaredConstructor(
                        java.lang.Float.TYPE,
                        java.lang.Float.TYPE
                    )
                constructor.isAccessible = true
                val interpolator = constructor.newInstance(amplitude, period)
                if (interpolator is Interpolator) {
                    return interpolator
                }
            } catch (ignored: Throwable) {
            }
            return SeslAnimationUtils.ELASTIC_40
        }

        private fun doOnNextLayout(view: View, action: () -> Unit) {
            view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                override fun onLayoutChange(
                    v: View,
                    left: Int,
                    top: Int,
                    right: Int,
                    bottom: Int,
                    oldLeft: Int,
                    oldTop: Int,
                    oldRight: Int,
                    oldBottom: Int
                ) {
                    v.removeOnLayoutChangeListener(this)
                    action()
                }
            })
        }

        private fun resetRotation(view: View) {
            view.rotation = 0f
            view.rotationX = 0f
            view.rotationY = 0f
        }

        private fun opaque(color: Int): Int {
            return (color and 0x00FFFFFF) or (0xFF000000).toInt()
        }

        private fun maxWidthFor(widthForBreakpoint: Int, widthPixels: Int): Int {
            return if (widthForBreakpoint <= 480) {
                (widthPixels * 0.83f).toInt()
            } else if (widthForBreakpoint <= 960) {
                (widthPixels * 0.6f).toInt()
            } else if (widthForBreakpoint <= 1280) {
                (widthPixels * 0.45f).toInt()
            } else {
                (widthPixels * 0.25f).toInt()
            }
        }

        private fun coerceIn(value: Int, min: Int, max: Int): Int {
            return maxOf(min, minOf(value, max))
        }

        private fun debugLog(msg: String) {
            if (BuildConfig.DEBUG) {
                Log.v(TAG, " #### $msg")
            }
        }

        private fun listen(animation: Animation, onStart: (() -> Unit)?, onEnd: (() -> Unit)?) {
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation) {
                    onStart?.invoke()
                }

                override fun onAnimationEnd(a: Animation) {
                    onEnd?.invoke()
                }

                override fun onAnimationRepeat(a: Animation) {
                }
            })
        }
    }
}
