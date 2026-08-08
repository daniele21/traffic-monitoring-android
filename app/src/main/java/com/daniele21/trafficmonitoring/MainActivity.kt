package com.daniele21.trafficmonitoring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniele21.trafficmonitoring.ui.ValidationScreen
import com.daniele21.trafficmonitoring.ui.ValidationViewModel
import com.daniele21.trafficmonitoring.ui.theme.TrafficMonitoringTheme

class MainActivity : ComponentActivity() {
    private lateinit var validationViewModel: ValidationViewModel

    private val exportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && ::validationViewModel.isInitialized) {
            validationViewModel.exportTo(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        validationViewModel = ViewModelProvider(this)[ValidationViewModel::class.java]

        setContent {
            TrafficMonitoringTheme {
                val state = validationViewModel.state.collectAsStateWithLifecycle().value
                ValidationScreen(
                    state = state,
                    onRefreshNetwork = validationViewModel::refreshNetwork,
                    onArmBackground = validationViewModel::armBackgroundCapture,
                    onAddMarker = validationViewModel::addMarker,
                    onExport = { exportDocument.launch(validationViewModel.suggestedExportFilename()) },
                    onStartNewRun = validationViewModel::startNewRun,
                    onClearAll = validationViewModel::clearAllAndStartFresh
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::validationViewModel.isInitialized) {
            validationViewModel.recordForeground()
        }
    }

    override fun onStop() {
        if (::validationViewModel.isInitialized) {
            validationViewModel.recordBackground()
        }
        super.onStop()
    }
}
