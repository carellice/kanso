package app.kanso

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import app.kanso.ui.K
import app.kanso.ui.KansoApp
import app.kanso.ui.KansoTheme

class MainActivity : ComponentActivity() {
    private val vm: KansoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        K.style = { vm.prefs.style }
        setContent {
            // Verticale di default (come nel manifest); con l'opzione attiva segue la rotazione del telefono.
            val landscape = vm.prefs.landscape
            LaunchedEffect(landscape) {
                requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            KansoTheme { KansoApp(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        vm.resumed()
    }

    // Il tasto Home con il launcher già aperto: torna alla pagina centrale e chiude tutto.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }
}
