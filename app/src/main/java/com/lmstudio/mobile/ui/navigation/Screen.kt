package com.lmstudio.mobile.ui.navigation

sealed class Screen(val route: String) {
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: String = "new") = "chat/$chatId"
    }
    object Models : Screen("models")
    object Downloads : Screen("downloads")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Metrics : Screen("metrics")
}

