package com.aibook.android

import android.app.Application
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import com.aibook.android.background.BackgroundWorkScheduler

class AiBookApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val locator = ServiceLocator.get(this)
        appScope.launch {
            locator.opdsConnectionRepository.migratePlaintextSecrets()
            locator.serverConfigStore.migratePlaintextToken()
            locator.serverRepository.initialize()
        }
        appScope.launch {
            if (locator.backgroundTaskStore.autoScanOnStart.first()) {
                BackgroundWorkScheduler.scanNow(this@AiBookApplication)
            }
        }
        appScope.launch {
            locator.backgroundTaskStore.scanIntervalHours.collect { hours ->
                BackgroundWorkScheduler.configureScan(this@AiBookApplication, hours)
            }
        }
        appScope.launch {
            combine(locator.backgroundTaskStore.opdsIntervalHours, locator.serverConfigStore.wifiOnlySync) { hours, wifi -> hours to wifi }
                .collect { (hours, wifi) -> BackgroundWorkScheduler.configureOpds(this@AiBookApplication, hours, wifi) }
        }
    }
}
