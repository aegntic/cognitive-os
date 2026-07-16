package com.thresholdinc.insidher.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.thresholdinc.insidher.InsidherApp
import com.thresholdinc.insidher.sms.SmsSender
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Polls backend for pending outbound SMS and sends them via [SmsSender].
 */
class PollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? InsidherApp ?: return Result.retry()
        val client = app.backendClient ?: return Result.retry()
        val sender = SmsSender(applicationContext)
        return try {
            val pending = client.pollOutbound()
            for (sms in pending) {
                val delayMs = computeDelayMs(sms.scheduledFor)
                val result = sender.send(sms.phoneNumber, sms.body, delayMs = delayMs)
                if (result.isSuccess) {
                    client.markDelivered(sms.id)
                } else {
                    Log.w(TAG, "send failed for ${sms.id}: ${result.exceptionOrNull()?.message}")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "poll failed", e)
            Result.retry()
        }
    }

    private fun computeDelayMs(scheduledFor: String): Long {
        return try {
            val target = Instant.parse(scheduledFor).toEpochMilli()
            (target - System.currentTimeMillis()).coerceAtLeast(0L).coerceAtMost(30_000L)
        } catch (_: Exception) {
            0L
        }
    }

    companion object {
        private const val TAG = "InsidherPollWorker"
        const val UNIQUE_NAME = "insidher_outbound_poll"
        private const val ONCE_NAME = "insidher_outbound_poll_once"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            // ponytail: immediate one-shot so first outbound isn't stuck 15m; keep periodic for later
            wm.enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PollWorker>().build(),
            )
            val req = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }
    }
}
