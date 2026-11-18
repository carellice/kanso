package app.kanso.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kanso.KansoViewModel
import app.kanso.MainActivity
import app.kanso.data.Widgets

/** I widget di Android scelti dall'utente, in fondo alla pagina Consapevolezza. */
@Composable
fun WidgetsSection(vm: KansoViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    var picking by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    var resizing by remember { mutableStateOf<Int?>(null) }
    val ids = vm.prefs.widgets
    val density = LocalDensity.current

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ids.forEach { id ->
            key(id) {
                val info = remember(id) { Widgets.manager(context).getAppWidgetInfo(id) }
                // App disinstallata: il widget non c'è più, resta solo il pulsante per toglierlo.
                if (info != null || removing) Column {
                    Box {
                        if (info != null) {
                            val minDp = with(density) { maxOf(info.minResizeHeight.toDp(), 48.dp).value }
                            val defaultDp = with(density) { maxOf(info.minHeight.toDp(), 72.dp).value }
                            var height by remember(id) { mutableFloatStateOf(vm.prefs.widgetHeights[id]?.toFloat() ?: defaultDp) }
                            AndroidView(
                                factory = { ctx -> Widgets.host(ctx).createView(ctx, id, info).also { (it as? Widgets.HostView)?.onLongPress = { resizing = id } } },
                                modifier = Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(20.dp)),
                            )
                            if (resizing == id) {
                                PillButton("Fine", Modifier.align(Alignment.TopEnd).padding(8.dp)) { resizing = null }
                                // Maniglia in basso: si trascina su e giù per cambiare l'altezza.
                                Box(
                                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(36.dp)
                                        .draggable(
                                            rememberDraggableState { delta -> height = (height + delta / density.density).coerceIn(minDp, 640f) },
                                            Orientation.Vertical,
                                            onDragStopped = { vm.prefs.saveWidgetHeight(id, height.toInt()) },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(Modifier.width(48.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(K.Paper))
                                }
                            }
                        }
                        if (removing) PillButton("Togli", Modifier.align(Alignment.TopEnd).padding(8.dp), icon = Icons.Rounded.Close) {
                            Widgets.host(context).deleteAppWidgetId(id)
                            vm.prefs.saveWidgets(vm.prefs.widgets - id)
                            if (vm.prefs.widgets.isEmpty()) removing = false
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Aggiungi widget", filled = false, icon = Icons.Rounded.Add) { vm.guarded { picking = true } }
            if (ids.isNotEmpty()) PillButton(if (removing) "Fine" else "Togli widget", filled = false) {
                if (removing) removing = false else vm.guarded { removing = true }
            }
        }
    }

    if (picking) WidgetPicker(vm, onDismiss = { picking = false }) { info ->
        picking = false
        activity?.addWidget(info)
    }
}

/** Elenco dei widget installati, raggruppati per app. */
@Composable
private fun WidgetPicker(vm: KansoViewModel, onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    // App (nome) -> widget (info, nome), in ordine alfabetico.
    val groups = remember {
        val pm = context.packageManager
        Widgets.manager(context).installedProviders
            .groupBy { it.provider.packageName }
            .map { (pkg, list) -> Triple(pkg, vm.byPkg[pkg]?.label ?: pkg, list.map { it to it.loadLabel(pm) }.sortedBy { it.second.lowercase() }) }
            .sortedBy { it.second.lowercase() }
    }
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()
    // Cerca per nome dell'app; se il testo è nel nome del widget, mostra solo quei widget.
    val shown = groups.mapNotNull { (pkg, app, widgets) ->
        when {
            q.isEmpty() || q in app.lowercase() -> Triple(pkg, app, widgets)
            else -> widgets.filter { q in it.second.lowercase() }.takeIf { it.isNotEmpty() }?.let { Triple(pkg, app, it) }
        }
    }
    ModalCard(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Aggiungi widget")
            Spacer(Modifier.height(8.dp))
            if (groups.isNotEmpty()) FieldBox(query, { query = it }, "Cerca un'app")
            if (shown.isEmpty()) Text(
                if (groups.isEmpty()) "Nessun widget disponibile" else "Nessun risultato",
                style = MaterialTheme.typography.bodyMedium, color = K.Muted,
            )
            shown.forEach { (pkg, app, widgets) ->
                Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(vm.appsRepo, pkg, 28.dp, monochrome = vm.prefs.monochrome)
                    Spacer(Modifier.width(12.dp))
                    Text(app, style = MaterialTheme.typography.labelMedium, color = K.Muted, maxLines = 1)
                }
                widgets.forEach { (info, label) ->
                    Text(
                        label, style = MaterialTheme.typography.titleMedium, color = K.Paper, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(pressedScale = 0.98f) { onPick(info) }
                            .padding(start = 40.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }
}
