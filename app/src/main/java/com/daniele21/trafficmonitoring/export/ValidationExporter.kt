package com.daniele21.trafficmonitoring.export

import android.content.Context
import android.net.Uri
import com.daniele21.trafficmonitoring.data.ValidationRepository
import java.time.Instant

class ValidationExporter(
    private val context: Context,
    private val repository: ValidationRepository,
    private val writer: ValidationExportWriter = ValidationExportWriter()
) {
    suspend fun exportRun(runId: String, destination: Uri) {
        val bundle = repository.loadExportBundle(runId)
        val output = requireNotNull(context.contentResolver.openOutputStream(destination)) {
            "Unable to open export destination"
        }
        output.use { writer.write(bundle, it) }
    }

    fun suggestedFilename(nowMs: Long = System.currentTimeMillis()): String {
        val timestamp = Instant.ofEpochMilli(nowMs).toString().replace(":", "-")
        return "traffic-monitoring-validation-$timestamp.zip"
    }
}
