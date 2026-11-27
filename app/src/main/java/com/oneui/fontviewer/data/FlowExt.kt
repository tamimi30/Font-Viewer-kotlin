package com.oneui.fontviewer.data

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

import java.util.concurrent.TimeoutException

/**
 * قراءة أول قيمة من الـ Flow وحجب الخيط الحالي حتى تصل (بديل Flowable.blockingFirst()).
 *
 * يُستخدم فقط في المسارات التي يجب أن تكون متزامنة بطبيعتها (Application.onCreate، attachBaseContext،
 * قيم تُقرأ داخل دوال عادية غير suspend ...). وبما أن AppDataStore يحتفظ بنسخة في الذاكرة،
 * فالقراءة فورية بعد وصول أول قراءة من القرص.
 *
 * في أي كود suspend استخدم `flow.first()` مباشرة بدل هذه الدالة.
 */
fun <T> Flow<T>.firstBlocking(): T = runBlocking { first() }

/**
 * نفس [firstBlocking] مع مهلة بالمللي ثانية (بديل timeout(...).blockingFirst()).
 * ترمي [TimeoutException] عند انتهاء المهلة.
 */
fun <T> Flow<T>.firstBlocking(timeoutMillis: Long): T = runBlocking {
    try {
        withTimeout(timeoutMillis) { first() }
    } catch (e: TimeoutCancellationException) {
        throw TimeoutException("No value received within $timeoutMillis ms")
    }
}
