package com.lmstudio.mobile.ui.screens.metrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    viewModel: MetricsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.startMonitoring()
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopMonitoring()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Performance Metrics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Resource Usage
            MetricsSection(title = "Resource Usage") {
                MetricCard(
                    title = "CPU Usage",
                    value = "${(state.metrics.cpuUsage * 100).toInt()}%",
                    icon = Icons.Default.Memory
                )
                MetricCard(
                    title = "RAM Usage",
                    value = "${(state.metrics.ramUsage * 100).toInt()}%",
                    icon = Icons.Default.Storage
                )
                MetricCard(
                    title = "VRAM Usage",
                    value = "${(state.metrics.vramUsage * 100).toInt()}%",
                    icon = Icons.Default.DeveloperMode
                )
                MetricCard(
                    title = "GPU Usage",
                    value = "${(state.metrics.gpuUsage * 100).toInt()}%",
                    icon = Icons.Default.Speed
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // Model Info
            val modelInfo = state.modelInfo
            if (modelInfo != null) {
                MetricsSection(title = "Model Information") {
                    InfoCard(
                        title = "Model Name",
                        value = modelInfo.name
                    )
                    val parameters = modelInfo.parameters
                    if (parameters != null) {
                        InfoCard(
                            title = "Parameters",
                            value = parameters
                        )
                    }
                    InfoCard(
                        title = "Context Length",
                        value = "${modelInfo.contextLength} tokens"
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // System Info
            MetricsSection(title = "System Information") {
                InfoCard(
                    title = "Device",
                    value = state.deviceInfo
                )
            }
        }
    }
}

@Composable
fun MetricsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    value: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
