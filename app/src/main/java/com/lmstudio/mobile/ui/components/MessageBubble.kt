package com.lmstudio.mobile.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmstudio.mobile.domain.model.Message
import com.lmstudio.mobile.domain.model.MessageRole

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val parsed = remember(message.content) { parseMessage(message.content) }
    var expanded by remember { mutableStateOf(true) }

    Row(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 16.dp, 
                topEnd = 16.dp, 
                bottomStart = if (isUser) 16.dp else 4.dp, 
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = color,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Thought block (Reasoning)
                if (parsed.thought != null && !isUser) {
                    ReasoningBlock(
                        thought = parsed.thought,
                        expanded = expanded,
                        onToggle = { expanded = !expanded }
                    )
                    if (parsed.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Main Answer
                if (parsed.content.isNotBlank()) {
                    Text(
                        text = parsed.content,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = if (isUser) TextAlign.End else TextAlign.Start,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (parsed.thought == null) {
                    // Fallback for empty messages if not thinking
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun ReasoningBlock(
    thought: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reasoning",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.size(20.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(visible = expanded) {
            Text(
                text = thought.trim(),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 18.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

private data class ParsedMessage(val thought: String?, val content: String)

private fun parseMessage(input: String): ParsedMessage {
    val thoughtTags = listOf(
        Pair("<think>", "</think>"),
        Pair("<|thought|>", "<|end_of_thought|>"),
        Pair("<｜thought｜>", "<｜end of sentence｜>") // Native DeepSeek
    )

    for ((startTag, endTag) in thoughtTags) {
        if (input.contains(startTag)) {
            val startIndex = input.indexOf(startTag)
            val endIndex = input.indexOf(endTag, startIndex + startTag.length)
            
            return if (endIndex != -1) {
                val thought = input.substring(startIndex + startTag.length, endIndex)
                val rest = input.substring(endIndex + endTag.length).trim()
                ParsedMessage(thought, rest)
            } else {
                // Thought still in progress
                val thought = input.substring(startIndex + startTag.length)
                ParsedMessage(thought, "")
            }
        }
    }
    
    // Fallback for special case where model might just start with thought content without tags 
    // but the engine injected it (DeepSeek native logic)
    if (input.startsWith("<｜thought｜>")) {
        // Handled by loop above
    }

    return ParsedMessage(null, input)
}

