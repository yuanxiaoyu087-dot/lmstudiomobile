package com.lmstudio.mobile.ui.screens.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lmstudio.mobile.data.remote.dto.HFModelDto
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> }

    state.selectedModelFiles?.let { files ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissFileSelection() },
            title = { Text("Select Quantization") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(files) { file ->
                        ListItem(
                            headlineContent = { Text(file.rfilename) },
                            supportingContent = { 
                                file.size?.let { 
                                    Text("${it / (1024 * 1024)} MB") 
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { 
                                    viewModel.downloadFile(state.selectedModelId!!, file.rfilename) 
                                }) {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissFileSelection() }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Download Models") },
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
                .padding(padding)
                .fillMaxSize()
        ) {
            // Network Warning
            AnimatedVisibility(visible = !state.isNetworkAvailable) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "No internet connection.",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.checkNetwork(); viewModel.loadPopularModels() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    if (it.isNotBlank()) viewModel.searchModels(it) else viewModel.loadPopularModels()
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search models (e.g. Gemma)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                enabled = state.isNetworkAvailable
            )

            // Active Downloads
            if (state.activeDownloads.isNotEmpty()) {
                Text(
                    "Downloading",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                state.activeDownloads.forEach { (id, progress) ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (progress.error != null) 
                                MaterialTheme.colorScheme.errorContainer 
                            else if (progress.isPaused)
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            else 
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        id.substringAfterLast("/"),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (progress.error != null) {
                                        Text(
                                            progress.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    } else if (progress.isPaused) {
                                        Text(
                                            "Paused",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                if (progress.error == null) {
                                    Text("${progress.progress}%")
                                } else {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (progress.error == null) {
                                LinearProgressIndicator(
                                    progress = { progress.progress / 100f },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                            }
                            
                            // Cancel and Pause/Resume buttons
                            if (progress.error == null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Button(
                                        onClick = { viewModel.pauseDownload(id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Icon(
                                            if (progress.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (progress.isPaused) "Resume" else "Pause")
                                    }
                                    
                                    Button(
                                        onClick = { viewModel.cancelDownload(id) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }

            // Content
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.models, key = { it.id }) { model ->
                        ModelDownloadCard(
                            model = model,
                            onDownload = {
                                val permissions = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                                if (permissions.isEmpty()) {
                                    viewModel.selectModel(model)
                                } else {
                                    requestPermissionLauncher.launch(permissions.toTypedArray())
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelDownloadCard(
    model: HFModelDto,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = model.id, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(text = model.pipeline_tag ?: "Text Generation", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(text = formatNumber(model.downloads), style = MaterialTheme.typography.bodySmall)
                }
                if (model.likes > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(text = formatNumber(model.likes), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun formatNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> String.format("%.1fM", number / 1_000_000.0)
        number >= 1_000 -> String.format("%.1fK", number / 1_000.0)
        else -> NumberFormat.getNumberInstance().format(number)
    }
}
