package com.daniele21.trafficmonitoring

import android.app.Application
import com.daniele21.trafficmonitoring.data.ValidationDatabase
import com.daniele21.trafficmonitoring.data.ValidationRepository
import com.daniele21.trafficmonitoring.platform.AndroidNetworkContextReader
import com.daniele21.trafficmonitoring.platform.AndroidTrafficCounterReader
import com.daniele21.trafficmonitoring.platform.InProcessNetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TrafficMonitoringApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var inProcessNetworkMonitor: InProcessNetworkMonitor

    val validationDatabase: ValidationDatabase by lazy {
        ValidationDatabase.getInstance(this)
    }

    val validationRepository: ValidationRepository by lazy {
        ValidationRepository(
            database = validationDatabase,
            networkContextReader = AndroidNetworkContextReader(this),
            trafficCounterReader = AndroidTrafficCounterReader(this)
        )
    }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            runCatching { validationRepository.recordProcessStart() }
        }

        inProcessNetworkMonitor = InProcessNetworkMonitor(
            context = this,
            repository = validationRepository,
            scope = applicationScope
        )
        inProcessNetworkMonitor.start()
    }
}
