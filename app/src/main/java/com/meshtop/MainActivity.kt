package com.meshtop

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meshtop.ui.MainScreen
import com.meshtop.ui.theme.*
import com.meshtop.viewmodel.MonitorViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted or not */ }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MeshtopTheme {
                val viewModel: MonitorViewModel = viewModel()
                val state by viewModel.state.collectAsState()
                val settings by viewModel.settings.collectAsState()
                val showSettings by viewModel.showSettings.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "meshtop",
                                    fontFamily = Mono,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                )
                            },
                            actions = {
                                // Reconnect button
                                IconButton(onClick = viewModel::reconnect) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Reconnect",
                                        tint = if (state.connected) TextDim else ErrorRed,
                                    )
                                }
                                // Settings button
                                IconButton(onClick = viewModel::toggleSettings) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = TextDim,
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = SurfaceCard,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    }
                ) { innerPadding ->
                    MainScreen(
                        state = state,
                        settings = settings,
                        showSettings = showSettings,
                        getRelayName = viewModel::getRelayNodeName,
                        onToggleSettings = viewModel::toggleSettings,
                        onHideSettings = viewModel::hideSettings,
                        onSaveSettings = viewModel::updateSettings,
                        onReconnect = viewModel::reconnect,
                        onClearStorage = viewModel::clearStorage,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
