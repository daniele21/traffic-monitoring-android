package com.daniele21.trafficmonitoring

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniele21.trafficmonitoring.ui.ProductViewModel
import com.daniele21.trafficmonitoring.ui.TrafficMonitoringRoot
import com.daniele21.trafficmonitoring.ui.ValidationViewModel
import com.daniele21.trafficmonitoring.ui.theme.TrafficMonitoringTheme

class MainActivity : ComponentActivity() {
    private lateinit var validationViewModel: ValidationViewModel
    private lateinit var productViewModel: ProductViewModel

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
        productViewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        setContent {
            TrafficMonitoringTheme {
                val validationState = validationViewModel.state.collectAsStateWithLifecycle().value
                val productState = productViewModel.state.collectAsStateWithLifecycle().value
                TrafficMonitoringRoot(
                    productState = productState,
                    validationState = validationState,
                    onSelectTimeframe = productViewModel::selectTimeframe,
                    onSelectCustomRange = productViewModel::selectCustomRange,
                    onRefreshProduct = productViewModel::refresh,
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
        if (::productViewModel.isInitialized) {
            productViewModel.refresh()
        }
    }

    override fun onStop() {
        if (::validationViewModel.isInitialized) {
            validationViewModel.recordBackground()
        }
        super.onStop()
    }
}
