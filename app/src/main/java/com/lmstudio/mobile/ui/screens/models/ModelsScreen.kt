package com.lmstudio.mobile.ui.screens.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(
    viewModel: ModelsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.models, key = { it.id }) { model ->
                        ModelCard(
                            model = model,
                            isLoaded = model.isLoaded,
                            onLoadModel = { viewModel.loadModel(model.id) },
                            onEjectModel = { viewModel.ejectModel() },
                            onDeleteModel = { viewModel.deleteModel(model.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModelCard(
    model: com.lmstudio.mobile.domain.model.LLMModel,
    isLoaded: Boolean,
    onLoadModel: () -> Unit,
    onEjectModel: () -> Unit,
    onDeleteModel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Size: ${model.size / (1024 * 1024)} MB",
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoaded) {
                    Button(onClick = onEjectModel) {
                        Text("Eject")
                    }
                } else {
                    Button(onClick = onLoadModel) {
                        Text("Load")
                    }
                }
                OutlinedButton(onClick = onDeleteModel) {
                    Text("Delete")
                }
            }
        }
    }
}

