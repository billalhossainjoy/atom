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
import io.appwrite.Client
import io.appwrite.services.Account
import io.appwrite.services.TablesDB
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Owner-approved device registration verification only. Does not access the SMS provider,
 * upload content, or modify any cloud row. The input contains no message data.
 */
class ConnectionTestWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expectedUser = inputData.getString("expected_user")
            ?: return Result.failure()
        if (ConnectionTestQueue.approvedUser(applicationContext) != expectedUser) {
            return Result.failure()
        }
        return try {
            val client = Client(applicationContext)
                .setEndpoint(AppwriteConfig.DEFAULT_ENDPOINT)
                .setProject(AppwriteConfig.DEFAULT_PROJECT_ID)
            val current = Account(client).get().id
            if (current != expectedUser) return Result.failure()
            val row = TablesDB(client).getRow(
                databaseId = AppwriteConfig.DEFAULT_DATABASE_ID,
                tableId = AppwriteConfig.TABLE_DEVICES,
                rowId = current
            )
            if (ConnectionTestQueue.approvedUser(applicationContext) != expectedUser ||
                row.id != current || row.data?.get("deviceId") != current) {
                Result.failure()
            } else Result.success(workDataOf("test" to "device_verified"))
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
    private const val PREFS = "device_connection_approval"
    private const val KEY = "approved_account"

    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun approvedUser(context: Context): String? =
        prefs(context).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun approve(context: Context, userId: String) {
        require(userId.isNotBlank())
        prefs(context).edit().putString(KEY, userId).apply()
        enqueue(context, userId)
    }

    fun revoke(context: Context) {
        prefs(context).edit().remove(KEY).apply()
        cancel(context)
    }

    fun enqueueApproved(context: Context) {
        approvedUser(context)?.let { enqueue(context, it) }
    }

    fun enqueue(context: Context, userId: String) {
        if (approvedUser(context) != userId) return
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
