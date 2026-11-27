package com.oneui.fontviewer.ui.utils

import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

import androidx.core.splashscreen.SplashScreen

object SplashUtils {

    fun configureSplashScreen(splashScreen: SplashScreen, root: View) {
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            // 1. جلب الخلفية لتطبيق التلاشي عليها فقط
            val splashView = splashScreenViewProvider.view

            // 2. جلب الأيقونة لتطبيق التصغير عليها
            val splashIconView = splashScreenViewProvider.iconView

            val oneEasingInterpolator = PathInterpolator(0.22f, 0.25f, 0f, 1f)
            val linearInterpolator = LinearInterpolator()

            // الحل الجذري لمنع الوميض تحت الضغط:
            // نقل الأنيميشن بالكامل من مسار المعالج (UI Thread) إلى معالج الرسوميات (RenderThread)
            // باستخدام ViewPropertyAnimator بدلاً من ObjectAnimator و AnimatorSet المعقدة.

            // --- 1. أنيميشن خروج أيقونة Splash ---
            splashView.animate()
                .alpha(0f)
                .setDuration(500)
                .setInterpolator(linearInterpolator)
                .withEndAction { splashScreenViewProvider.remove() }
                .start()

            splashIconView.animate()
                .scaleX(0.80f)
                .scaleY(0.80f)
                .setDuration(500)
                .setInterpolator(oneEasingInterpolator)
                .start()

            // --- 2. أنيميشن دخول محتوى التطبيق ---
            root.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setStartDelay(200)
                .setInterpolator(oneEasingInterpolator)
                .start()
        }
    }
}
