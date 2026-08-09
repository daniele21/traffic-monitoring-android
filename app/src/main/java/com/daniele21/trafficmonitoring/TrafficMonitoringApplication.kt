package com.daniele21.trafficmonitoring

import android.app.Application
import com.daniele21.trafficmonitoring.background.ProcessExitRecorder
import com.daniele21.trafficmonitoring.background.RecoveryScheduler
import com.daniele21.trafficmonitoring.data.ValidationDatabase
import com.daniele21.trafficmonitoring.data.ValidationRepository
import com.daniele21.trafficmonitoring.platform.AndroidNetworkContextReader
import com.daniele21.trafficmonitoring.platform.AndroidTrafficCounterReader
import com.daniele21.trafficmonitoring.platform.InProcessNetworkMonitor
import com.daniele21.trafficmonitoring.platform.PendingIntentNetworkMonitor
import com.daniele21.trafficmonitoring.usage.UsageDatabase
import com.daniele21.trafficmonitoring.usage.UsageRepository
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

    val usageDatabase: UsageDatabase by lazy {
        UsageDatabase.getInstance(this)
    }

    val usageRepository: UsageRepository by lazy {
        UsageRepository(usageDatabase)
    }

    val validationRepository: ValidationRepository by lazy {
        ValidationRepository(
            database = validationDatabase,
            networkContextReader = AndroidNetworkContextReader(this),
            trafficCounterReader = AndroidTrafficCounterReader(this),
            usageRepository = usageRepository
        )
    }

    val backgroundNetworkMonitor: PendingIntentNetworkMonitor by lazy {
        PendingIntentNetworkMonitor(this)
    }

    val processExitRecorder: ProcessExitRecorder by lazy {
        ProcessExitRecorder(this)
    }

    override fun onCreate() {
        super.onCreate()

        RecoveryScheduler.schedule(this)

        applicationScope.launch {
            runCatching {
                validationRepository.recordProcessStart()
                processExitRecorder.captureInto(validationRepository)
            }
        }

        inProcessNetworkMonitor = InProcessNetworkMonitor(
            context = this,
            repository = validationRepository,
            scope = applicationScope
        )
        inProcessNetworkMonitor.start()

        // Do not automatically re-register the PendingIntent here. A process can be created solely
        // to deliver that PendingIntent; re-registering during Application.onCreate could create a
        // duplicate immediate onAvailable wake. UI start / boot / package replacement / recovery own arming.
    }
}
