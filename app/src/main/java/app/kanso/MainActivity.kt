package app.kanso

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import app.kanso.data.Theme
import app.kanso.ui.K
import app.kanso.ui.KansoApp
import app.kanso.ui.KansoTheme

class MainActivity : ComponentActivity() {
    private val vm: KansoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        K.style = { vm.prefs.style }
        // Al cambio di tema del sistema Android ricrea l'Activity, quindi basta leggerlo qui.
        val systemLight = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK != Configuration.UI_MODE_NIGHT_YES
        K.light = { when (vm.prefs.theme) { Theme.SYSTEM -> systemLight; Theme.LIGHT -> true; Theme.DARK -> false } }
        setContent {
            val light = K.light()
            LaunchedEffect(light) {
                val t = android.graphics.Color.TRANSPARENT
                val bars = if (light) SystemBarStyle.light(t, t) else SystemBarStyle.dark(t)
                enableEdgeToEdge(bars, bars)
            }
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
