package com.oneui.fontviewer.ui.utils

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.view.View

import com.google.android.material.animation.MotionSpec
import com.oneui.fontviewer.R

object FormatBarAnimator {

    fun showWithFabMotion(context: Context, container: View, vararg icons: View?) {
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.scaleX = 0f
        container.scaleY = 0f

        for (icon in icons) {
            if (icon != null) {
                icon.scaleX = 0f
                icon.scaleY = 0f
            }
        }

        val spec = MotionSpec.createFromResource(context, R.animator.mtrl_fab_show_motion_spec)!!
        val animators = ArrayList<Animator>()

        val opacityAnimator = ObjectAnimator.ofFloat(container, View.ALPHA, 1f)
        spec.getTiming("opacity").apply(opacityAnimator)
        animators.add(opacityAnimator)

        val scaleXAnimator = ObjectAnimator.ofFloat(container, View.SCALE_X, 1f)
        spec.getTiming("scale").apply(scaleXAnimator)
        animators.add(scaleXAnimator)

        val scaleYAnimator = ObjectAnimator.ofFloat(container, View.SCALE_Y, 1f)
        spec.getTiming("scale").apply(scaleYAnimator)
        animators.add(scaleYAnimator)

        for (icon in icons) {
            if (icon == null) {
                continue
            }
            val iconScaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f)
            spec.getTiming("iconScale").apply(iconScaleX)
            animators.add(iconScaleX)

            val iconScaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f)
            spec.getTiming("iconScale").apply(iconScaleY)
            animators.add(iconScaleY)
        }

        val set = AnimatorSet()
        set.playTogether(animators)
        set.start()
    }
}
