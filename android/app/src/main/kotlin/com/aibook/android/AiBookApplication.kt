package com.aibook.android

import android.app.Application
import com.aibook.android.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AiBookApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val locator = ServiceLocator.get(this)
        appScope.launch {
            locator.serverRepository.initialize()
        }
    }
}
