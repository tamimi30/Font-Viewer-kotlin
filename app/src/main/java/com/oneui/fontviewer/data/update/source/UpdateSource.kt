package com.oneui.fontviewer.data.update.source

import com.oneui.fontviewer.data.update.model.UpdateInfo
import java.util.Optional

/**
 * مصدر معرفة وجود تحديث. لكل مكان تثبيت مصدره الخاص:
 * Play Store ← Play، و Galaxy Store ← Galaxy Store، والتثبيت اليدوي ← GitHub.
 *
 * تُستدعى [checkForUpdate] من خيط خلفي (قد تحجب الخيط حتى انتهاء الطلب).
 */
fun interface UpdateSource {

    /**
     * @return التحديث المتوفر للنسخة المثبتة حالياً، أو Optional.empty() إذا كانت هي الأحدث.
     * @throws Exception عند تعذر التحقق (لا اتصال، استجابة غير مفهومة، التطبيق غير منشور في هذا المتجر ...)
     */
    @Throws(Exception::class)
    fun checkForUpdate(): Optional<UpdateInfo>
}
