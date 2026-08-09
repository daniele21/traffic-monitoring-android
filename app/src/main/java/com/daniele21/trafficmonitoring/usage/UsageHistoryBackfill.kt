package com.daniele21.trafficmonitoring.usage

import com.daniele21.trafficmonitoring.data.ValidationDatabase

class UsageHistoryBackfill(
    private val validationDatabase: ValidationDatabase,
    private val usageRepository: UsageRepository
) {
    suspend fun run() {
        validationDatabase.validationDao()
            .allAttributionIntervals()
            .forEach { usageRepository.ingest(it) }
    }
}
