package com.supermarket.inventory

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.supermarket.inventory.data.SessionManager
import com.supermarket.inventory.notifications.AlertsWorker
import com.supermarket.inventory.notifications.BackupWorker
import com.supermarket.inventory.notifications.SalesSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class InventoryApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate() {
        super.onCreate()
        MainScope().launch {
            sessionManager.preload()
        }
        AlertsWorker.schedule(this)
        BackupWorker.schedule(this)
        // Catches any sales left in the offline queue from a previous
        // session (e.g. the process was killed before connectivity came
        // back) so they still get a sync attempt without waiting on a
        // periodic schedule.
        SalesSyncWorker.enqueue(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
