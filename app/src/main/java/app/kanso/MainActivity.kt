package app.kanso

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
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
import app.kanso.data.Widgets
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

    // I widget si aggiornano solo mentre l'host è in ascolto.
    override fun onStart() { super.onStart(); runCatching { Widgets.host(this).startListening() } }
    override fun onStop() { super.onStop(); runCatching { Widgets.host(this).stopListening() } }

    /** Aggiunge un widget: permesso di collegarlo, poi l'eventuale configurazione, poi in pagina. */
    fun addWidget(info: AppWidgetProviderInfo) {
        val id = Widgets.host(this).allocateAppWidgetId()
        if (Widgets.manager(this).bindAppWidgetIdIfAllowed(id, info.provider)) configureWidget(id, info)
        else {
            val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            @Suppress("DEPRECATION") startActivityForResult(bind, REQ_BIND)
        }
    }

    private fun configureWidget(id: Int, info: AppWidgetProviderInfo?) {
        if (info?.configure == null) return widgetReady(id)
        // La schermata di configurazione spesso non è esportata: la apre l'host per conto dell'app.
        runCatching { Widgets.host(this).startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null) }
            .onFailure { widgetReady(id) }
    }

    private fun widgetReady(id: Int) { vm.prefs.saveWidgets(vm.prefs.widgets + id) }

    @Deprecated("Il collegamento e la configurazione dei widget passano ancora da qui.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (id == -1) return
        when {
            resultCode != RESULT_OK -> Widgets.host(this).deleteAppWidgetId(id)
            requestCode == REQ_BIND -> configureWidget(id, Widgets.manager(this).getAppWidgetInfo(id))
            else -> widgetReady(id)
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

    private companion object {
        const val REQ_BIND = 41
        const val REQ_CONFIGURE = 42
    }
}
