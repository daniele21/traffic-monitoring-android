package com.daniele21.trafficmonitoring

import android.app.Application
import com.daniele21.trafficmonitoring.data.ValidationDatabase
import com.daniele21.trafficmonitoring.data.ValidationRepository
import com.daniele21.trafficmonitoring.platform.AndroidNetworkContextReader

class TrafficMonitoringApplication : Application() {
    val validationDatabase: ValidationDatabase by lazy {
        ValidationDatabase.getInstance(this)
    }

    val validationRepository: ValidationRepository by lazy {
        ValidationRepository(
            database = validationDatabase,
            networkContextReader = AndroidNetworkContextReader(this)
        )
    }
}
