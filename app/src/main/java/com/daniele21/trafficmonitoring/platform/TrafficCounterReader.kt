package com.daniele21.trafficmonitoring.platform

import android.content.Context
import android.net.TrafficStats
import android.provider.Settings

data class TrafficCounterReading(
    val rxBytes: Long?,
    val txBytes: Long?,
    val bootGeneration: String,
    val status: String,
    val errorCode: String?
)

interface TrafficCounterReader {
    fun read(): TrafficCounterReading
}

class AndroidTrafficCounterReader(context: Context) : TrafficCounterReader {
    private val contentResolver = context.applicationContext.contentResolver

    override fun read(): TrafficCounterReading {
        val bootGeneration = readBootGeneration()
        return try {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) {
                TrafficCounterReading(
                    rxBytes = null,
                    txBytes = null,
                    bootGeneration = bootGeneration,
                    status = "unsupported",
                    errorCode = "traffic_stats_unsupported"
                )
            } else {
                TrafficCounterReading(
                    rxBytes = rx,
                    txBytes = tx,
                    bootGeneration = bootGeneration,
                    status = "ok",
                    errorCode = null
                )
            }
        } catch (security: SecurityException) {
            TrafficCounterReading(
                rxBytes = null,
                txBytes = null,
                bootGeneration = bootGeneration,
                status = "error",
                errorCode = "security_exception"
            )
        } catch (error: RuntimeException) {
            TrafficCounterReading(
                rxBytes = null,
                txBytes = null,
                bootGeneration = bootGeneration,
                status = "error",
                errorCode = error::class.java.simpleName.ifBlank { "counter_read_error" }
            )
        }
    }

    private fun readBootGeneration(): String = try {
        val count = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (count >= 0) "boot:$count" else "boot:unknown"
    } catch (_: RuntimeException) {
        "boot:unknown"
    }
}
