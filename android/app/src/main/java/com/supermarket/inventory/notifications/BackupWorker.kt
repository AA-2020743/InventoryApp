package com.supermarket.inventory.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.supermarket.inventory.R
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.BackupFiles
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.repository.BackupRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Weekly pull of a full data export from the backend, saved to app-specific
 * local storage as a safety net if the backend is ever lost. This is a
 * belt-and-suspenders copy alongside the server's own daily backups - the
 * phone might be the only thing left if the server's disk dies entirely.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupRepository: BackupRepository,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (sessionManager.token.value == null) return Result.success()

        return when (val result = backupRepository.exportBackup()) {
            is ApiResult.Success -> {
                val file = BackupFiles.write(applicationContext, result.data)
                NotificationHelper.ensureChannel(applicationContext)
                NotificationHelper.showBackupSaved(
                    applicationContext,
                    applicationContext.getString(R.string.notif_backup_saved_title),
                    applicationContext.getString(R.string.notif_backup_saved_body, file.name),
                )
                Result.success()
            }
            is ApiResult.Error -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "weekly_backup"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
