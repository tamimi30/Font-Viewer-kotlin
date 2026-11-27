package com.oneui.fontviewer.data.update.source

import android.content.Context

import com.oneui.fontviewer.BuildConfig
import com.oneui.fontviewer.data.update.api.GalaxyStoreUpdateApi
import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.utils.update.AppLinks

import java.io.IOException
import java.util.Optional

/** مصدر التحديث للنسخة المثبتة من Galaxy Store: يسأل خادم المتجر مثل تطبيقات سامسونج. */
class GalaxyStoreUpdateSource(context: Context) : UpdateSource {

    private val appContext: Context = context.applicationContext
    private val api = GalaxyStoreUpdateApi()

    @Throws(IOException::class)
    override fun checkForUpdate(): Optional<UpdateInfo> {
        val result = api.check(appContext)

        when (result.resultCode) {
            GalaxyStoreUpdateApi.RESULT_CAN_UPDATE -> {
                // إن لم يرجع الخادم رقم الإصدار نعتبره أعلى بواحد من المثبّت، ليُمسح المؤشر بعد التحديث
                val versionCode = if (result.versionCode > 0)
                    result.versionCode
                else
                    BuildConfig.VERSION_CODE + 1L
                val versionName = result.versionName ?: ""
                return Optional.of(
                    UpdateInfo(
                        versionName,
                        AppLinks.galaxyStoreProductUrl(appContext.packageName), versionCode
                    )
                )
            }

            GalaxyStoreUpdateApi.RESULT_NO_UPDATE,
            GalaxyStoreUpdateApi.RESULT_NO_UPDATE_ALT -> return Optional.empty()

            else ->
                // مثلاً: التطبيق غير موجود في Galaxy Store بعد
                throw IOException("Unexpected Galaxy Store result code: " + result.resultCode)
        }
    }
}
