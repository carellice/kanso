package app.kanso.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.kanso.KansoViewModel
import app.kanso.data.AppEntry
import app.kanso.data.Category
import app.kanso.data.DistractingCategories

private const val PREVIEW = 8

@Composable
fun LibraryPage(vm: KansoViewModel) {
    val tiles = vm.tiles.toSet()
    val groups = remember(vm.apps, tiles) {
        vm.apps.filter { it.pkg !in tiles }.groupBy { it.category }.toSortedMap(compareBy { it.ordinal }).toList()
    }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Box(Modifier.statusBarsPadding().padding(horizontal = 4.dp)) {
                PageHeader("${vm.apps.size - tiles.size} app", "Libreria")
            }
        }
        items(groups, key = { it.first.name }) { (category, apps) ->
            CategoryCard(vm, category, apps, Modifier.animateItem(), onLongPress = { menuFor = it })
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }

    menuFor?.let { app -> AppMenu(vm, app) { menuFor = null } }
}

@Composable
private fun CategoryCard(vm: KansoViewModel, category: Category, apps: List<AppEntry>, modifier: Modifier, onLongPress: (AppEntry) -> Unit) {
    var expanded by rememberSaveable(category) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, Motion.soft(), label = "chevron")
    KCard(modifier.animateContentSize(Motion.soft())) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(category.title, style = MaterialTheme.typography.titleLarge, color = K.Paper, modifier = Modifier.weight(1f))
                if (category in DistractingCategories) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(K.Shu))
                    Spacer(Modifier.width(8.dp))
                }
                Text("${apps.size}", style = MaterialTheme.typography.labelMedium, color = K.Muted)
            }
            Spacer(Modifier.height(18.dp))
            AppRows(vm, apps.take(PREVIEW), onLongPress)
            AnimatedVisibility(
                expanded && apps.size > PREVIEW,
                enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
                exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
            ) {
                Column { Spacer(Modifier.height(16.dp)); AppRows(vm, apps.drop(PREVIEW), onLongPress) }
            }
            if (apps.size > PREVIEW) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp).clip(CircleShape).pressable { expanded = !expanded }.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (expanded) "Mostra meno" else "Altre ${apps.size - PREVIEW}", style = MaterialTheme.typography.labelMedium, color = K.Muted)
                    Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }, tint = K.Muted)
                }
            }
        }
    }
}

@Composable
private fun AppRows(vm: KansoViewModel, apps: List<AppEntry>, onLongPress: (AppEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        apps.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    Column(
                        Modifier.weight(1f).pressable(onLongClick = { onLongPress(app) }) { vm.open(app) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(vm.appsRepo, app.pkg, 48.dp, monochrome = vm.prefs.monochrome)
                        Spacer(Modifier.height(8.dp))
                        AppLabel(app.label, Modifier.padding(horizontal = 2.dp))
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun AppMenu(vm: KansoViewModel, app: AppEntry, onDismiss: () -> Unit) {
    ModalCard(onDismiss) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(vm.appsRepo, app.pkg, 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(app.label, style = MaterialTheme.typography.headlineMedium, color = K.Paper)
            Text(app.category.title, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            Spacer(Modifier.height(24.dp))
            PillButton("Aggiungi alla Home", Modifier.fillMaxWidth()) { onDismiss(); vm.guarded { vm.addTile(app.pkg) } }
            Spacer(Modifier.height(10.dp))
            PillButton("Informazioni app", Modifier.fillMaxWidth(), filled = false) {
                onDismiss()
                vm.start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
            }
        }
    }
}
