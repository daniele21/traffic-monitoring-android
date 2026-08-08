package com.daniele21.trafficmonitoring

import android.app.Application
import com.daniele21.trafficmonitoring.data.ValidationDatabase
import com.daniele21.trafficmonitoring.data.ValidationRepository
import com.daniele21.trafficmonitoring.platform.AndroidNetworkContextReader
import com.daniele21.trafficmonitoring.platform.AndroidTrafficCounterReader
import com.daniele21.trafficmonitoring.platform.InProcessNetworkMonitor
import com.daniele21.trafficmonitoring.platform.PendingIntentNetworkMonitor
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

    val backgroundNetworkMonitor: PendingIntentNetworkMonitor by lazy {
        PendingIntentNetworkMonitor(this)
    }

    override fun onCreate() {
        super.onCreate()

        applicationScope.launch {
            runCatching { validationRepository.recordProcessStart() }
        }

        // M1B live diagnostics remain useful while the process exists.
        inProcessNetworkMonitor = InProcessNetworkMonitor(
            context = this,
            repository = validationRepository,
            scope = applicationScope
        )
        inProcessNetworkMonitor.start()

        // Do not automatically re-register the PendingIntent here. A process can be created solely
        // to deliver that PendingIntent; re-registering during Application.onCreate could create a
        // duplicate immediate onAvailable wake. UI start / boot / package replacement own arming.
    }
}
