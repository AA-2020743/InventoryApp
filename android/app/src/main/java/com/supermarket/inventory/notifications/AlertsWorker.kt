package com.supermarket.inventory.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.repository.DashboardRepository
import com.supermarket.inventory.ui.common.formatAmount
import com.supermarket.inventory.ui.common.formatIsoDate
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically polls /api/alerts and posts local notifications for low
 * stock and supplier invoices due soon or overdue. This is a lightweight
 * substitute for server push (which would need FCM + a project), acceptable
 * for a single-device, self-hosted backend.
 */
@HiltWorker
class AlertsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dashboardRepository: DashboardRepository,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (sessionManager.token.value == null) return Result.success()

        NotificationHelper.ensureChannel(applicationContext)

        return when (val result = dashboardRepository.getAlerts()) {
            is ApiResult.Success -> {
                val data = result.data
                NotificationHelper.showLowStock(applicationContext, data.lowStock.size)

                data.overdueInvoices.firstOrNull()?.let { invoice ->
                    NotificationHelper.showInvoiceReminder(
                        applicationContext,
                        applicationContext.getString(com.supermarket.inventory.R.string.notif_invoice_overdue_title),
                        applicationContext.getString(
                            com.supermarket.inventory.R.string.notif_invoice_overdue_body,
                            invoice.supplier?.name ?: invoice.supplierId,
                            formatAmount(invoice.amount),
                        ),
                        notificationId = 2001,
                    )
                }
                data.dueSoonInvoices.firstOrNull()?.let { invoice ->
                    NotificationHelper.showInvoiceReminder(
                        applicationContext,
                        applicationContext.getString(com.supermarket.inventory.R.string.notif_invoice_due_title),
                        applicationContext.getString(
                            com.supermarket.inventory.R.string.notif_invoice_due_body,
                            invoice.supplier?.name ?: invoice.supplierId,
                            formatAmount(invoice.amount),
                            formatIsoDate(invoice.dueDate),
                        ),
                        notificationId = 2002,
                    )
                }
                Result.success()
            }
            is ApiResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "alerts_poll"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<AlertsWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
