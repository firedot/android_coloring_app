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

private const val COLORING = "coloring?${CanvasViewModel.ARTWORK_ID}={${CanvasViewModel.ARTWORK_ID}}"
private const val GALLERY = "gallery"

/** Coloring (start, with a new animal) ↔ Gallery. Opening a picture replaces the coloring screen. */
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
            ColoringScreen(onOpenGallery = { navController.navigate(GALLERY) { launchSingleTop = true } })
        }
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
