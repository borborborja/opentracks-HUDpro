package cat.hudpro.opentracks.manager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cat.hudpro.opentracks.ui.theme.HudProTheme
import cat.hudpro.opentracks.viewer.MapViewerActivity

/** Entry point of the management app (visual configuration, tracks, offline maps, Endurain). */
class ManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HudProTheme {
                ManagerApp(
                    onOpenViewer = {
                        startActivity(Intent(this, MapViewerActivity::class.java))
                    },
                )
            }
        }
    }
}
