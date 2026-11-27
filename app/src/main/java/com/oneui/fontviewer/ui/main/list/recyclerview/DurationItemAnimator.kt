package com.oneui.fontviewer.ui.main.list.recyclerview

import androidx.recyclerview.widget.DefaultItemAnimator

/**
 * نسخة DefaultItemAnimator المستخدمة في المشروع (Sesl) تعمل Override للـ getters
 * وترجع قيمًا ثابتة متجاهلة الـ setters الموروثة. هذا الـ subclass يعيد ربط
 * الـ getters بالـ setters حتى تعمل القيم المخصصة فعليًا.
 */
class DurationItemAnimator : DefaultItemAnimator() {

    //Default duration
    /**
    private long mAddDuration = 200;
    private long mRemoveDuration = 100;
    private long mMoveDuration = 400;
    private long mChangeDuration = 400;
    */

    // هذه هي المدد المستخدمة في قائمة الخطوط المحلية (LocalFontListFragment لا تضبطها بنفسها)
    private var mAddDuration: Long = 200
    private var mRemoveDuration: Long = 100
    private var mMoveDuration: Long = 400
    private var mChangeDuration: Long = 400

    override fun getAddDuration(): Long {
        return mAddDuration
    }

    override fun setAddDuration(duration: Long) {
        mAddDuration = duration
    }

    override fun getRemoveDuration(): Long {
        return mRemoveDuration
    }

    override fun setRemoveDuration(duration: Long) {
        mRemoveDuration = duration
    }

    override fun getMoveDuration(): Long {
        return mMoveDuration
    }

    override fun setMoveDuration(duration: Long) {
        mMoveDuration = duration
    }

    override fun getChangeDuration(): Long {
        return mChangeDuration
    }

    override fun setChangeDuration(duration: Long) {
        mChangeDuration = duration
    }
}
