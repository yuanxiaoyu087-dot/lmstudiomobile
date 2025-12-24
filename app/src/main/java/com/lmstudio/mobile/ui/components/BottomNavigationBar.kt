package com.lmstudio.mobile.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BottomNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        BottomNavItem(
            route = "chat/new",
            label = "Chat",
            icon = Icons.Default.Chat
        ),
        BottomNavItem(
            route = "history",
            label = "History",
            icon = Icons.Default.History
        ),
        BottomNavItem(
            route = "downloads",
            label = "Downloads",
            icon = Icons.Default.Download
        ),
        BottomNavItem(
            route = "metrics",
            label = "Metrics",
            icon = Icons.Default.Speed
        ),
        BottomNavItem(
            route = "settings",
            label = "Settings",
            icon = Icons.Default.Settings
        )
    )

    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentRoute.startsWith(item.route) || 
                          (item.route == "chat/new" && currentRoute.startsWith("chat")),
                onClick = { onNavigate(item.route) }
            )
        }
    }
}

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

