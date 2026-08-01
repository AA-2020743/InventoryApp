package com.supermarket.inventory.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.supermarket.inventory.R
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.repository.PendingSaleRepository
import com.supermarket.inventory.data.repository.SalesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Drains the offline sales queue (see [PendingSaleRepository]) once
 * connectivity is available. Enqueued right after a sale is queued and
 * again on every app startup, so a batch left over from a killed process
 * still gets a chance to sync without waiting on a periodic schedule.
 */
@HiltWorker
class SalesSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val salesRepository: SalesRepository,
    private val pendingSaleRepository: PendingSaleRepository,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (sessionManager.token.value == null) return Result.success()

        val outcome = pendingSaleRepository.drainAndSync(salesRepository)
        if (outcome.droppedWithError > 0) {
            NotificationHelper.ensureChannel(applicationContext)
            NotificationHelper.showSyncIssue(
                applicationContext,
                applicationContext.getString(R.string.notif_sales_sync_issue_title),
                applicationContext.getString(R.string.notif_sales_sync_issue_body, outcome.droppedWithError),
            )
        }
        return if (outcome.stillPendingNetworkError) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "sales_sync"

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<SalesSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
