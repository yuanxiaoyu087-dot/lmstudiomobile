package com.lmstudio.mobile.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.domain.model.MessageRole
import com.lmstudio.mobile.ui.components.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    viewModel: ChatViewModel = hiltViewModel(),
    onNavigateToModels: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToMetrics: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messagesState by viewModel.messages.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(chatId) {
        viewModel.loadChat(chatId)
    }

    var messageText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            ChatTopBar(
                chatTitle = state.currentChat?.title ?: "New Chat",
                isModelLoaded = state.isModelLoaded,
                modelName = state.loadedModel?.name,
                onNavigateToModels = onNavigateToModels,
                onEjectModel = { viewModel.ejectModel() }
            )
        },
        bottomBar = {
            if (state.isModelLoaded) {
                MessageInputBar(
                    message = messageText,
                    onValueChange = { messageText = it },
                    onSendMessage = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    onStopGeneration = { viewModel.stopGeneration() },
                    isGenerating = state.isGenerating,
                    enabled = true // Always enabled to allow stopping
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (!state.isModelLoaded) {
                NoModelLoadedScreen(
                    lastUsedModel = state.loadedModel,
                    onLoadModel = onNavigateToModels,
                    onLoadLastUsed = { viewModel.loadLastUsedModel() }
                )
            } else {
                val messages = messagesState
                val listState = rememberLazyListState()
                
                // Auto-scroll to bottom when new messages arrive
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (state.isGenerating) {
                    TypingIndicator(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    chatTitle: String,
    isModelLoaded: Boolean,
    modelName: String?,
    onNavigateToModels: () -> Unit,
    onEjectModel: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(chatTitle)
                if (isModelLoaded && modelName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = modelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        IconButton(
                            onClick = onEjectModel,
                            modifier = Modifier.size(24.dp).padding(start = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Logout,
                                contentDescription = "Eject Model",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun NoModelLoadedScreen(
    lastUsedModel: com.lmstudio.mobile.domain.model.LLMModel?,
    onLoadModel: () -> Unit,
    onLoadLastUsed: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "No Model Loaded",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onLoadModel,
                modifier = Modifier.fillMaxWidth(0.7f)
            ) {
                Text("Go to Models")
            }

            if (lastUsedModel != null) {
                OutlinedButton(
                    onClick = onLoadLastUsed,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Load Last Used")
                        Text(
                            text = lastUsedModel.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageInputBar(
    message: String,
    onValueChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled && !isGenerating,
                placeholder = { Text("Type a message...") },
                maxLines = 5
            )
            
            if (isGenerating) {
                IconButton(
                    onClick = onStopGeneration,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Outlined.Stop, contentDescription = "Stop Generation")
                }
            } else {
                IconButton(
                    onClick = onSendMessage,
                    enabled = enabled && message.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) {
            CircularProgressIndicator(
                modifier = Modifier.size(8.dp),
                strokeWidth = 2.dp
            )
        }
    }
}
