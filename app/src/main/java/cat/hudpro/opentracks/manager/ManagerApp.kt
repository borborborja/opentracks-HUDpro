package cat.hudpro.opentracks.manager

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cat.hudpro.opentracks.manager.screens.EndurainScreen
import cat.hudpro.opentracks.manager.screens.HomeScreen
import cat.hudpro.opentracks.manager.screens.HudDesignerScreen
import cat.hudpro.opentracks.manager.screens.MapLayersScreen
import cat.hudpro.opentracks.manager.screens.TrackLibraryScreen

object Routes {
    const val HOME = "home"
    const val HUD = "hud"
    const val LAYERS = "layers"
    const val TRACKS = "tracks"
    const val ENDURAIN = "endurain"
}

@Composable
fun ManagerApp(onOpenViewer: () -> Unit) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenViewer = onOpenViewer,
                onNavigate = { nav.navigate(it) },
            )
        }
        composable(Routes.HUD) { HudDesignerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LAYERS) { MapLayersScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TRACKS) { TrackLibraryScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ENDURAIN) { EndurainScreen(onBack = { nav.popBackStack() }) }
    }
}
