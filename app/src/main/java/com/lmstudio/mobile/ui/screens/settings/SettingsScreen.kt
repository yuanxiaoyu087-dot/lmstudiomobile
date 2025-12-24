package com.lmstudio.mobile.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
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
                    description = "Number of CPU threads to use for generation.",
                    helpText = "Higher values use more CPU power and can speed up generation, but may cause the device to overheat or other apps to lag. Recommended: number of physical cores.",
                    value = state.nThreads.toString(),
                    onValueChange = { viewModel.setNThreads(it.toIntOrNull() ?: 4) }
                )
                SettingsItem(
                    title = "GPU Layers",
                    description = "Number of layers to offload to GPU (Vulkan).",
                    helpText = "Offloading layers to GPU can significantly speed up inference. Set to 0 to use CPU only. High values require more VRAM (System RAM on Android).",
                    value = state.nGpuLayers.toString(),
                    onValueChange = { viewModel.setNGpuLayers(it.toIntOrNull() ?: 0) }
                )
                SettingsItem(
                    title = "Context Size",
                    description = "Maximum context window size (tokens).",
                    helpText = "Determines how much previous conversation history the model can remember. Larger context requires more RAM.",
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
                    value = "1.0.1"
                )
                SettingsInfoItem(
                    title = "Build",
                    value = "Release Candidate"
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
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        content()
    }
}

@Composable
fun SettingsItem(
    title: String,
    description: String,
    helpText: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    IconButton(
                        onClick = { showHelp = !showHelp },
                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (showHelp) Icons.Default.Info else Icons.Outlined.HelpOutline,
                            contentDescription = "Help",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.width(80.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium
            )
        }
        
        AnimatedVisibility(visible = showHelp) {
            Text(
                text = helpText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, end = 80.dp)
            )
        }
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
