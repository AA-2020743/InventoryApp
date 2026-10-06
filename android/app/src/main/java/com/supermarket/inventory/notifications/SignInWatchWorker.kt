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
import com.supermarket.inventory.data.ApiResult
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Asks the server which devices have signed in to the account for the first
 * time since the last check, and raises an alert for each.
 *
 * Polling rather than push, like the other alerts here: there is no FCM in
 * this app, so the alert arrives on the next check after the sign-in rather
 * than the moment it happens. Fifteen minutes is the shortest interval
 * Android allows for periodic work.
 *
 * The window is tracked by the server's own clock (see /new-devices), and
 * the watermark only moves forward once the alerts for it are posted - a
 * check that fails leaves it where it was, so the next one covers the gap.
 */
@HiltWorker
class SignInWatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (sessionManager.token.value == null) return Result.success()

        val since = sessionManager.signInWatermark()
        return when (val result = authRepository.getNewDevices(since)) {
            is ApiResult.Success -> {
                // A first check (since == null) reports nothing by design and
                // only establishes where the next one starts.
                result.data.devices.forEach { device ->
                    NotificationHelper.showNewSignIn(applicationContext, device)
                }
                sessionManager.setSignInWatermark(result.data.serverTime)
                Result.success()
            }
            // No connection: retry soon. Anything else - a 401 because this
            // device was signed out (the network layer has already cleared
            // the token), or a server error - has nothing to gain from an
            // immediate retry. Either way the watermark hasn't moved, so the
            // next scheduled check covers this window too.
            is ApiResult.Error -> if (result.isNetworkError) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "sign_in_watch"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<SignInWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
