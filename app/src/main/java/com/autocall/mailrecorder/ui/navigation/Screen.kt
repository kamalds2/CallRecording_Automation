package com.autocall.mailrecorder.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Consent : Screen("consent")
    object Permissions : Screen("permissions")
    object EmailSetup : Screen("email_setup")
    object Dashboard : Screen("dashboard")
    object History : Screen("history")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
}
