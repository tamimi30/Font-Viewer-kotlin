package com.oneui.fontviewer.ui.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.text.TextUtils

import java.util.Locale

import com.oneui.fontviewer.data.settings.SettingsRepository

object LocaleHelper {

    private const val TAG = "LocaleHelper"

    @Suppress("DEPRECATION")
    fun wrapContext(context: Context): Context {
        // من أندرويد 13 فما فوق، النظام (LocaleManager) هو من يضع لغة التطبيق في إعدادات كل شاشة.
        // فرض اللغة هنا من وراء النظام يجعل ما تعرضه الشاشة يختلف عمّا سجّله النظام لها، فإذا طابقت
        // اللغة الجديدة لاحقاً ما سجّله النظام (القديم) ظنّ أنه لا تغيير ولم يعد بناء الشاشة.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // لا نغيّر اللغة نفسها، لكن نصحّح اتجاه الواجهة فقط ليطابق لغة الإعدادات التي سلّمها النظام.
            // السبب: عند العودة إلى لغة النظام (setApplicationLocales بقائمة فارغة) يسلّم النظام
            // إعدادات لغتها عربية لكن اتجاهها ما زال LTR (يظهر في التقرير: locales=ar-MA ... dir=LTR).
            // اتجاه الواجهة مشتق من اللغة، فتصحيحه لا يجعل الشاشة تعرض لغة غير التي سجّلها النظام لها.
            val current = context.resources.configuration
            val configLocale = current.locales[0]
            val expectedDirection = TextUtils.getLayoutDirectionFromLocale(configLocale)
            if (current.layoutDirection == expectedDirection) {
                return context
            }
            val fixed = Configuration(current)
            fixed.setLayoutDirection(configLocale)
            return context.createConfigurationContext(fixed)
        }

        val locale = SettingsRepository.getLocale(context)
        Locale.setDefault(locale)

        val res = context.resources
        val config = Configuration(res.configuration)

        config.setLocale(locale)

        Log.d(TAG, "Context wrapped with locale: " + locale.language)

        return context.createConfigurationContext(config)
    }
}
