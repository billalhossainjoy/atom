package com.billal.datom.appwrite

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.appwrite.exceptions.AppwriteException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Explicitly requested connectivity test only. Does not access the SMS provider,
 * upload content, or modify any cloud row. The input contains no message data.
 */
class ConnectionTestWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expectedUser = inputData.getString("expected_user")
            ?: return Result.failure()
        return try {
            val current = EnrollmentSession.currentUser(applicationContext)
            if (current != expectedUser) Result.failure()
            else Result.success(workDataOf("test" to "session_verified"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppwriteException) {
            if ((e.code == 429 || (e.code ?: 0) >= 500) && runAttemptCount < 4) {
                Result.retry()
            } else Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}

object ConnectionTestQueue {
    const val NAME = "datom_connection_test"

    fun enqueue(context: Context, userId: String) {
        require(userId.isNotBlank())
        val request = OneTimeWorkRequestBuilder<ConnectionTestWorker>()
            .setInputData(workDataOf("expected_user" to userId))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(NAME)
    }
}
