package com.lmstudio.mobile.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            // Inference Settings
            SettingsSection(title = "Inference Settings") {
                SettingsItem(
                    title = "Threads",
                    description = "Number of CPU threads",
                    value = state.nThreads.toString(),
                    onValueChange = { viewModel.setNThreads(it.toIntOrNull() ?: 4) }
                )
                SettingsItem(
                    title = "GPU Layers",
                    description = "Number of layers to offload to GPU",
                    value = state.nGpuLayers.toString(),
                    onValueChange = { viewModel.setNGpuLayers(it.toIntOrNull() ?: 0) }
                )
                SettingsItem(
                    title = "Context Size",
                    description = "Maximum context window size",
                    value = state.contextSize.toString(),
                    onValueChange = { viewModel.setContextSize(it.toIntOrNull() ?: 2048) }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // App Settings
            SettingsSection(title = "App Settings") {
                SettingsSwitchItem(
                    title = "Dark Theme",
                    description = "Enable dark mode",
                    checked = state.isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) }
                )
                SettingsSwitchItem(
                    title = "Auto-save Chats",
                    description = "Automatically save chat history",
                    checked = state.autoSaveChats,
                    onCheckedChange = { viewModel.setAutoSaveChats(it) }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // About
            SettingsSection(title = "About") {
                SettingsInfoItem(
                    title = "Version",
                    value = "1.0.0"
                )
                SettingsInfoItem(
                    title = "Build",
                    value = "Debug"
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
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
fun SettingsItem(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(100.dp),
            singleLine = true
        )
    }
}

@Composable
fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsInfoItem(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
