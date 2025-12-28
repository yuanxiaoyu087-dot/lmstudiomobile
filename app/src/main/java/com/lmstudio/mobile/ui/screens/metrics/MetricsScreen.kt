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
            // System Info
            MetricsSection(title = "System Details") {
                state.deviceCapabilities?.let { caps ->
                    InfoCard(title = "Device Model", value = caps.deviceModel)
                    InfoCard(title = "Processor (SoC)", value = caps.socName)
                    InfoCard(title = "CPU Cores", value = "${caps.cpuCores} cores")
                    InfoCard(title = "Safe Core Limit (80%)", value = "${caps.recommendedThreads} threads")
                    InfoCard(title = "Total System RAM", value = "${caps.totalRam / (1024 * 1024 * 1024)} GB")
                    InfoCard(title = "GPU / Hardware", value = caps.gpuName ?: "Unknown")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Resource Usage
            MetricsSection(title = "Real-time Monitoring") {
                state.systemMetrics?.let { system ->
                    MetricCard(
                        title = "System CPU Usage",
                        value = "${(system.cpuUsagePercent * 100).toInt()}%",
                        icon = Icons.Default.Speed,
                        progress = system.cpuUsagePercent
                    )
                    MetricCard(
                        title = "System RAM Used",
                        value = "${system.ramUsedMb} / ${system.ramTotalMb} MB",
                        icon = Icons.Default.Memory,
                        progress = system.ramUsedMb.toFloat() / system.ramTotalMb.toFloat()
                    )
                }
                
                state.metrics.let { engine ->
                    MetricCard(
                        title = "LLM Engine VRAM",
                        value = "${(engine.vramUsage * 100).toInt()}%",
                        icon = Icons.Default.DeveloperMode,
                        progress = engine.vramUsage
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Model Info
            val modelInfo = state.modelInfo
            if (modelInfo != null) {
                MetricsSection(title = "Loaded Model") {
                    InfoCard(title = "Name", value = modelInfo.name)
                    InfoCard(title = "Context Length", value = "${modelInfo.contextLength} tokens")
                }
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    progress: Float = 0f
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
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
