package com.fireworm.colourfun.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fireworm.colourfun.ui.canvas.CanvasViewModel.Companion.ARTWORK_ID
import com.fireworm.colourfun.ui.canvas.CanvasViewModel.Companion.PICTURE_ID
import com.fireworm.colourfun.ui.canvas.ColoringScreen
import com.fireworm.colourfun.ui.library.LibraryScreen
import com.fireworm.colourfun.ui.settings.SettingsScreen

private const val LIBRARY = "library"
private const val COLORING = "coloring?$PICTURE_ID={$PICTURE_ID}&$ARTWORK_ID={$ARTWORK_ID}"
private const val SETTINGS = "settings"

/**
 * Library (start) → Coloring, with a new artwork from a picture or a saved one from "my pictures".
 * Home and back return to the library. Settings open from the library after the parent gate.
 */
@Composable
fun ColourFunNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController, startDestination = LIBRARY) {
        composable(LIBRARY) {
            LibraryScreen(
                onOpenPicture = { navController.openColoring("$PICTURE_ID=${it.encoded()}") },
                onContinue = { navController.openColoring("$ARTWORK_ID=$it") },
                onOpenSettings = { navController.navigate(SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(
            COLORING,
            arguments = listOf(PICTURE_ID, ARTWORK_ID).map { name ->
                navArgument(name) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            },
        ) {
            ColoringScreen(onHome = { navController.popBackStack(LIBRARY, inclusive = false) })
        }
        composable(SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}

private fun NavHostController.openColoring(query: String) = navigate("coloring?$query") { launchSingleTop = true }

/** Picture ids contain a slash (`animals/cat_01`). */
private fun String.encoded(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
