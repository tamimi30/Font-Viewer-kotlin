package androidx.appcompat.graphics.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/**
 * SESL Compatibility Shim
 * This class fixes the crash when using SplashScreen with SESL libraries
 * and ensures Ripple effects work correctly by propagating states.
 */
open class DrawableWrapper(private val mDrawable: Drawable?) : Drawable(), Drawable.Callback {

    init {
        mDrawable?.callback = this
    }

    override fun draw(canvas: Canvas) {
        mDrawable?.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        mDrawable?.bounds = bounds
    }

    override fun setChangingConfigurations(configs: Int) {
        mDrawable?.changingConfigurations = configs
    }

    override fun getChangingConfigurations(): Int {
        return mDrawable?.changingConfigurations ?: 0
    }

    @Suppress("DEPRECATION")
    override fun setDither(dither: Boolean) {
        mDrawable?.setDither(dither)
    }

    override fun setFilterBitmap(filter: Boolean) {
        mDrawable?.isFilterBitmap = filter
    }

    override fun setAlpha(alpha: Int) {
        mDrawable?.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mDrawable?.colorFilter = colorFilter
    }

    override fun isStateful(): Boolean {
        return mDrawable != null && mDrawable.isStateful
    }

    override fun onStateChange(state: IntArray): Boolean {
        if (mDrawable != null && mDrawable.isStateful) {
            val changed = mDrawable.setState(state)
            if (changed) {
                invalidateSelf()
            }
            return changed
        }
        return false
    }

    override fun onLevelChange(level: Int): Boolean {
        return mDrawable != null && mDrawable.setLevel(level)
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int {
        return if (mDrawable != null) mDrawable.opacity else PixelFormat.UNKNOWN
    }

    override fun getIntrinsicWidth(): Int {
        return mDrawable?.intrinsicWidth ?: -1
    }

    override fun getIntrinsicHeight(): Int {
        return mDrawable?.intrinsicHeight ?: -1
    }

    // Callback methods for animations and ripples
    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, time: Long) {
        scheduleSelf(what, time)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }
}
