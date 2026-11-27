package com.oneui.fontviewer.ui.base

import android.content.Context
import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity

import com.oneui.fontviewer.ui.utils.LocaleHelper
import com.oneui.fontviewer.ui.utils.TransitionUtils

open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val wrappedContext = LocaleHelper.wrapContext(newBase)

        super.attachBaseContext(wrappedContext)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        TransitionUtils.playIfPending(this)
    }

}
