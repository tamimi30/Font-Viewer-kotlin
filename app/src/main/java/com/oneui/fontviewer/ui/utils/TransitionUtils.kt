package com.oneui.fontviewer.ui.utils

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

object TransitionUtils {

    private const val TAG = "TransitionUtils"

    enum class AnimationType(
        val durationMs: Long,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float
    ) {

        NONE(0L, 0f, 0f, 1f, 1f),

        FADE(600L, 0.33f, 0f, 0.67f, 1f),

        WIPE_TOP_TO_BOTTOM(600L, 0.33f, 0f, 0.3f, 1f);

        // FADE(600L, 0.22f, 0.25f, 0f, 1f) منحنى One UI.

        fun createInterpolator(): PathInterpolator {
            return PathInterpolator(x1, y1, x2, y2)
        }
    }

    // تغيير أنيميشن الثيم
    @JvmField
    val THEME_ANIMATION: AnimationType = AnimationType.FADE

    const val THEME_DURATION_MS = 600L

    // تغيير أنيميشن اللغة
    @JvmField
    val LANGUAGE_ANIMATION: AnimationType = AnimationType.FADE

    const val LANGUAGE_DURATION_MS = 400L


    private const val PENDING_TIMEOUT_MS = 3000L

    private var sSnapshot: Bitmap? = null
    private var sAnimation: AnimationType? = null
    private var sDurationMs: Long = 0L
    private var sOwnerActivityName: String? = null
    private var sOldNightFlag: Int = 0
    private var sOldLanguage: String? = null
    private var sCaptureTime: Long = 0L

    fun prepareForThemeChange(activity: Activity?) {
        capture(activity, THEME_ANIMATION, THEME_DURATION_MS)
    }

    fun prepareForLanguageChange(activity: Activity?) {
        capture(activity, LANGUAGE_ANIMATION, LANGUAGE_DURATION_MS)
    }

    private fun capture(activity: Activity?, animation: AnimationType, durationMs: Long) {
        discardPending()

        if (activity == null || animation == AnimationType.NONE) {
            return
        }

        val decor = activity.window.decorView
        val width = decor.width
        val height = decor.height
        if (width <= 0 || height <= 0) {
            return
        }

        try {
            finishTouchEffects(decor)

            val snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            decor.draw(Canvas(snapshot))

            sSnapshot = snapshot
            sAnimation = animation
            sDurationMs = durationMs
            sOwnerActivityName = activity.javaClass.name
            sOldNightFlag = getNightFlag(activity)
            sOldLanguage = getLanguage(activity)
            sCaptureTime = SystemClock.elapsedRealtime()

            Handler(Looper.getMainLooper()).postDelayed({
                if (sSnapshot === snapshot) {
                    discardPending()
                }
            }, PENDING_TIMEOUT_MS)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Failed to capture snapshot", e)
            discardPending()
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Failed to capture snapshot", e)
            discardPending()
        }
    }

    fun playIfPending(activity: Activity?) {
        val snapshot = sSnapshot
        if (snapshot == null || activity == null) {
            return
        }

        if (activity.javaClass.name != sOwnerActivityName) {
            return
        }

        val type = sAnimation
        val durationMs = sDurationMs
        val oldNightFlag = sOldNightFlag
        val oldLanguage = sOldLanguage
        val expired = SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS
        clearPending()

        val changed = getNightFlag(activity) != oldNightFlag
                || getLanguage(activity) != oldLanguage

        if (expired || !changed || type == null || type == AnimationType.NONE || activity.isFinishing) {
            snapshot.recycle()
            return
        }

        val decor = activity.window.decorView as ViewGroup
        val overlay = SnapshotOverlayView(activity, snapshot, type, durationMs)
        decor.addView(
            overlay, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        overlay.start()
    }

    /**
     * يعرض صورة اللقطة المنتظرة ثابتة (بدون أنيميشن) فوق الشاشة الحالية، ولا يستهلكها: تبقى
     * منتظرة لتُعرض بالأنيميشن في الشاشة الجديدة بعد إعادة البناء (playIfPending).
     * الطبقة تُزال تلقائياً عندما يعيد النظام بناء الشاشة.
     */
    fun freezeScreen(activity: Activity?) {
        val snapshot = sSnapshot
        val animation = sAnimation
        if (snapshot == null || animation == null || activity == null
            || activity.javaClass.name != sOwnerActivityName
            || SystemClock.elapsedRealtime() - sCaptureTime > PENDING_TIMEOUT_MS
        ) {
            return
        }

        val decor = activity.window.decorView as ViewGroup
        decor.addView(
            SnapshotOverlayView(activity, snapshot, animation, sDurationMs),
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    /**
     * ★ تغيير جزء من الواجهة بأنيميشن، داخل نفس الشاشة (بدون إعادة بناء) ★
     *
     * للتغيير المتزامن (يحدث فوراً داخل Runnable):
     *
     *     TransitionUtils.run(recyclerView, TransitionUtils.AnimationType.FADE) {
     *         adapter.submitList(newList)
     *     }
     *
     * @param target    الجزء الذي سيتغير (قائمة، حاوية، أو decorView للشاشة كاملة)
     * @param animation نوع الأنيميشن
     * @param change    الكود الذي يغيّر الواجهة
     */
    fun run(target: View?, animation: AnimationType, change: Runnable) {
        val handle = begin(target, animation)
        change.run()
        handle.play()
    }

    /**
     * ★ نفس الفكرة للتغيير غير المتزامن (مثل تحميل بيانات) على مرحلتين ★
     *
     *     // 1) قبل بدء التغيير: نلتقط صورة للشكل القديم (الشاشة تبقى حيّة ومتفاعلة)
     *     mTransition = TransitionUtils.begin(container, TransitionUtils.AnimationType.FADE)
     *
     *     // 2) عندما تصبح الواجهة الجديدة جاهزة (بعد تحديث الواجهة مباشرة): نشغّل الأنيميشن
     *     mTransition.play()
     *
     * إذا فشلت العملية أو أُلغيت نستدعي cancel() (أو نترك الكائن، فلا يتسرب شيء).
     */
    fun begin(target: View?, animation: AnimationType): Handle {
        return Handle(target, animation)
    }

    class Handle internal constructor(
        private val mTarget: View?,
        private val mAnimation: AnimationType
    ) {

        private val mLocation = IntArray(2)
        private var mSnapshot: Bitmap? = null

        init {
            if (!(mTarget == null || mAnimation == AnimationType.NONE
                        || mTarget.width <= 0 || mTarget.height <= 0)
            ) {
                try {
                    finishTouchEffects(mTarget)
                    mTarget.getLocationInWindow(mLocation)

                    val snapshot = Bitmap.createBitmap(
                        mTarget.width, mTarget.height, Bitmap.Config.ARGB_8888
                    )
                    mTarget.draw(Canvas(snapshot))
                    mSnapshot = snapshot
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Failed to capture snapshot", e)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Failed to capture snapshot", e)
                }
            }
        }

        /** يعرض صورة الشكل القديم فوق مكانها ويحرّكها لتظهر الواجهة الجديدة (مرة واحدة فقط). */
        @SuppressLint("RtlHardcoded")
        fun play() {
            val snapshot = mSnapshot
            mSnapshot = null

            if (snapshot == null || mTarget == null || !mTarget.isAttachedToWindow
                || mTarget.rootView !is FrameLayout
            ) {
                return
            }

            // Gravity.LEFT (مطلق) وليس START: حتى يكون المكان صحيحاً في الواجهات RTL أيضاً
            val params = FrameLayout.LayoutParams(
                snapshot.width, snapshot.height, Gravity.TOP or Gravity.LEFT
            )
            params.leftMargin = mLocation[0]
            params.topMargin = mLocation[1]

            val overlay =
                SnapshotOverlayView(mTarget.context, snapshot, mAnimation, mAnimation.durationMs)
            (mTarget.rootView as FrameLayout).addView(overlay, params)
            overlay.start()
        }

        /** يتخلى عن اللقطة دون تشغيل أي أنيميشن. */
        fun cancel() {
            mSnapshot = null
        }
    }

    private fun finishTouchEffects(view: View) {
        view.jumpDrawablesToCurrentState()

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                finishTouchEffects(view.getChildAt(i))
            }
        }
    }

    // الوسم الكامل (مثل ar أو ar-MA) وليس رمز اللغة فقط، حتى يُعدّ التبديل بين ar و ar-MA تغييراً
    // فيُشغَّل الأنيميشن
    private fun getLanguage(context: Context): String {
        return context.resources.configuration.locales.get(0).toLanguageTag()
    }

    private fun getNightFlag(context: Context): Int {
        return context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
    }

    private fun clearPending() {
        sSnapshot = null
        sAnimation = null
        sDurationMs = 0L
        sOwnerActivityName = null
    }

    private fun discardPending() {
        val snapshot = sSnapshot
        clearPending()
        snapshot?.recycle()
    }

    @SuppressLint("ViewConstructor")
    private class SnapshotOverlayView(
        context: Context,
        private var mSnapshot: Bitmap?,
        private val mType: AnimationType,
        private val mDurationMs: Long
    ) : View(context) {

        private val mDst = Rect()
        private val mPaint = Paint(Paint.FILTER_BITMAP_FLAG)

        private var mProgress = 0f
        private var mReleased = false

        init {
            setWillNotDraw(false)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            z = 1000f
        }

        fun start() {
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = mDurationMs
            animator.interpolator = mType.createInterpolator()
            animator.addUpdateListener { a ->
                mProgress = a.animatedValue as Float
                invalidate()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    release()
                }
            })

            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {}

                override fun onViewDetachedFromWindow(v: View) {
                    mReleased = true
                    mSnapshot = null
                    animator.cancel()
                }
            })

            viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    viewTreeObserver.removeOnPreDrawListener(this)
                    if (!mReleased) {
                        animator.start()
                    }
                    return true
                }
            })
        }

        private fun release() {
            if (mReleased) {
                return
            }
            mReleased = true

            val parent = parent as ViewGroup?
            parent?.removeView(this)
            mSnapshot = null
        }

        override fun onDraw(canvas: Canvas) {
            val snapshot = mSnapshot ?: return

            val width = width
            val height = height
            mDst.set(0, 0, width, height)

            when (mType) {
                AnimationType.FADE -> {
                    mPaint.alpha = Math.round(255f * (1f - mProgress))
                    canvas.drawBitmap(snapshot, null, mDst, mPaint)
                }

                AnimationType.WIPE_TOP_TO_BOTTOM -> {
                    mPaint.alpha = 255
                    canvas.save()
                    canvas.clipRect(0f, height * mProgress, width.toFloat(), height.toFloat())
                    canvas.drawBitmap(snapshot, null, mDst, mPaint)
                    canvas.restore()
                }

                AnimationType.NONE -> {
                }
            }
        }
    }
}
