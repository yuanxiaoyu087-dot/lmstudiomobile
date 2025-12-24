package com.lmstudio.mobile.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

    Box {
        NavHost(
            navController = navController,
            startDestination = Screen.Chat.createRoute()
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
                        navController.navigate(Screen.History.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToDownloads = {
                        navController.navigate(Screen.Downloads.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToMetrics = {
                        navController.navigate(Screen.Metrics.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
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

        // Bottom navigation bar - only show on main screens
        if (currentRoute.startsWith("chat") || 
            currentRoute == Screen.History.route ||
            currentRoute == Screen.Downloads.route ||
            currentRoute == Screen.Metrics.route ||
            currentRoute == Screen.Settings.route) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                BottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        when (route) {
                            "chat/new" -> {
                                val chatRoute = Screen.Chat.createRoute()
                                if (currentRoute != chatRoute) {
                                    navController.navigate(chatRoute) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            Screen.History.route -> {
                                if (currentRoute != Screen.History.route) {
                                    navController.navigate(Screen.History.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            Screen.Downloads.route -> {
                                if (currentRoute != Screen.Downloads.route) {
                                    navController.navigate(Screen.Downloads.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            Screen.Metrics.route -> {
                                if (currentRoute != Screen.Metrics.route) {
                                    navController.navigate(Screen.Metrics.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            Screen.Settings.route -> {
                                if (currentRoute != Screen.Settings.route) {
                                    navController.navigate(Screen.Settings.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

