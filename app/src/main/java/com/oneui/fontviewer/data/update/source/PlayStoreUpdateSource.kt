package com.oneui.fontviewer.data.update.source

import android.content.Context

import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import com.oneui.fontviewer.data.update.model.UpdateInfo
import com.oneui.fontviewer.utils.update.AppLinks

import java.io.IOException
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * مصدر التحديث للنسخة المثبتة من Play Store: Play In-App Updates API.
 * نستخدمها فقط لمعرفة توفر تحديث، أما التحديث نفسه فيتم بفتح صفحة التطبيق في Play.
 *
 * تعمل فقط للنسخة المثبتة من Play لتطبيق منشور (ولو في مسار الاختبار الداخلي).
 */
class PlayStoreUpdateSource(context: Context) : UpdateSource {

    private val appContext: Context = context.applicationContext

    @Throws(Exception::class)
    override fun checkForUpdate(): Optional<UpdateInfo> {
        val appUpdateManager = AppUpdateManagerFactory.create(appContext)

        // المستمعات تُستدعى على الخيط الرئيسي، ونحن على خيط خلفي ننتظر النتيجة
        val latch = CountDownLatch(1)
        val info = AtomicReference<AppUpdateInfo>()
        val error = AtomicReference<Exception>()

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { result ->
                info.set(result)
                latch.countDown()
            }
            .addOnFailureListener { e ->
                error.set(e)
                latch.countDown()
            }

        if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw IOException("Play update check timed out")
        }
        val failure = error.get()
        if (failure != null) {
            throw failure
        }

        val updateInfo = info.get()
        if (updateInfo != null
            && updateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        ) {
            val versionCode = updateInfo.availableVersionCode().toLong()
            return Optional.of(
                UpdateInfo(
                    versionCode.toString(),
                    AppLinks.playStoreWebUrl(appContext.packageName), versionCode
                )
            )
        }
        return Optional.empty()
    }

    companion object {
        private const val TIMEOUT_SECONDS = 15L
    }
}
