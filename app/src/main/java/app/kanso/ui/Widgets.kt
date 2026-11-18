package app.kanso.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.LocalActivity
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
    val ids = vm.prefs.widgets

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ids.forEach { id ->
            key(id) {
                val info = remember(id) { Widgets.manager(context).getAppWidgetInfo(id) }
                // App disinstallata: il widget non c'è più, resta solo il pulsante per toglierlo.
                if (info != null || removing) Box {
                    if (info != null) {
                        val minHeight = with(LocalDensity.current) { info.minHeight.toDp() }
                        AndroidView(
                            factory = { Widgets.host(it).createView(it, id, info) },
                            modifier = Modifier.fillMaxWidth().height(maxOf(minHeight, 72.dp)).clip(RoundedCornerShape(20.dp)),
                        )
                    }
                    if (removing) PillButton("Togli", Modifier.align(Alignment.TopEnd).padding(8.dp), icon = Icons.Rounded.Close) {
                        Widgets.host(context).deleteAppWidgetId(id)
                        vm.prefs.saveWidgets(vm.prefs.widgets - id)
                        if (vm.prefs.widgets.isEmpty()) removing = false
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
    val providers = remember {
        val pm = context.packageManager
        val apps = vm.byPkg
        Widgets.manager(context).installedProviders
            .map { it to it.loadLabel(pm) }
            .sortedWith(compareBy({ apps[it.first.provider.packageName]?.label?.lowercase() ?: it.first.provider.packageName }, { it.second.lowercase() }))
    }
    ModalCard(onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Aggiungi widget")
            Spacer(Modifier.height(8.dp))
            if (providers.isEmpty()) Text("Nessun widget disponibile", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            providers.forEach { (info, label) ->
                val pkg = info.provider.packageName
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).pressable(pressedScale = 0.98f) { onPick(info) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(vm.appsRepo, pkg, 32.dp, monochrome = vm.prefs.monochrome)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleMedium, color = K.Paper, maxLines = 1)
                        Text(vm.byPkg[pkg]?.label ?: pkg, style = MaterialTheme.typography.labelMedium, color = K.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}
