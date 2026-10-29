package com.fireworm.colourfun.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fireworm.colourfun.ui.canvas.CanvasViewModel
import com.fireworm.colourfun.ui.canvas.ColoringScreen
import com.fireworm.colourfun.ui.gallery.GalleryScreen
import com.fireworm.colourfun.ui.settings.SettingsScreen

private const val COLORING = "coloring?${CanvasViewModel.ARTWORK_ID}={${CanvasViewModel.ARTWORK_ID}}"
private const val GALLERY = "gallery"
private const val SETTINGS = "settings"

/**
 * Coloring (start, with a new animal) ↔ Gallery; Settings after the parent gate. Opening a picture
 * replaces the coloring screen.
 */
@Composable
fun ColourFunNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = COLORING) {
        composable(
            COLORING,
            arguments = listOf(
                navArgument(CanvasViewModel.ARTWORK_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ColoringScreen(
                // Until the picture library exists, home opens the saved pictures.
                onHome = { navController.navigate(GALLERY) { launchSingleTop = true } },
                onOpenSettings = { navController.navigate(SETTINGS) { launchSingleTop = true } },
            )
        }
        composable(SETTINGS) { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable(GALLERY) {
            GalleryScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id ->
                    navController.navigate("coloring?${CanvasViewModel.ARTWORK_ID}=$id") {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
    }
}
