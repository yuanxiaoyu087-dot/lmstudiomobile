package com.lmstudio.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.lmstudio.mobile.ui.components.BottomNavigationBar
import com.lmstudio.mobile.ui.screens.chat.ChatScreen
import com.lmstudio.mobile.ui.screens.downloads.DownloadsScreen
import com.lmstudio.mobile.ui.screens.history.HistoryScreen
import com.lmstudio.mobile.ui.screens.metrics.MetricsScreen
import com.lmstudio.mobile.ui.screens.models.ModelsScreen
import com.lmstudio.mobile.ui.screens.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Chat.createRoute()

    val showBottomBar = currentRoute.startsWith("chat") || 
                        currentRoute == Screen.History.route ||
                        currentRoute == Screen.Downloads.route ||
                        currentRoute == Screen.Metrics.route ||
                        currentRoute == Screen.Settings.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(
                route = Screen.Chat.route,
                arguments = listOf(navArgument("chatId") { defaultValue = "new" })
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getString("chatId") ?: "new"
                ChatScreen(
                    chatId = chatId,
                    onNavigateToModels = {
                        navController.navigate(Screen.Models.route)
                    },
                    onNavigateToHistory = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }

            composable(Screen.Models.route) {
                ModelsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    onNavigateToChat = { chatId ->
                        navController.navigate(Screen.Chat.createRoute(chatId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Metrics.route) {
                MetricsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
