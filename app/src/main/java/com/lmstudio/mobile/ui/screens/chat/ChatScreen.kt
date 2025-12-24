package com.lmstudio.mobile.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                onRenameChat = { viewModel.renameChat(it) },
                onDeleteChat = { viewModel.deleteChat() }
            )
        },
        bottomBar = {
            Column {
                if (state.isModelLoaded) {
                    // Metrics bar can be added here
                }
                MessageInputBar(
                    message = messageText,
                    onMessageChange = { messageText = it },
                    onSendMessage = {
                        if (messageText.isNotBlank()) {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        }
                    },
                    enabled = state.isModelLoaded && !state.isGenerating
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (!state.isModelLoaded) {
                NoModelLoadedScreen(
                    onLoadModel = onNavigateToModels
                )
            } else {
                val messages = messagesState
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
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
    onRenameChat: (String) -> Unit,
    onDeleteChat: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(chatTitle)
                if (isModelLoaded && modelName != null) {
                    Text(
                        text = modelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        })
        }
@Composable
fun NoModelLoadedScreen(
    onLoadModel: () -> Unit
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
            Button(onClick = onLoadModel) {
                Text("Load Model")
            }
        }
    }
}

@Composable
fun MessageInputBar(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
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
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text("Type a message...") },
                maxLines = 5
            )
            IconButton(
                onClick = onSendMessage,
                enabled = enabled && message.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
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
