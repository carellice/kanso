package app.kanso.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.runtime.produceState
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
fun LibraryPage(vm: KansoViewModel, active: Boolean) {
    // Uscendo dalla Libreria si torna in cima: al ritorno si riparte dall'inizio.
    val list = rememberLazyListState()
    LaunchedEffect(active) { if (!active) list.scrollToItem(0) }
    val tiles = vm.tiles.toSet()
    val groups = remember(vm.apps, tiles) {
        vm.apps.filter { it.pkg !in tiles }.groupBy { it.category }.toSortedMap(compareBy { it.ordinal }).toList()
    }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
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
        if (vm.privateUser != null) item(key = "private") { PrivateCard(vm, Modifier.animateItem()) }
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

/** Lo spazio privato: chiuso mostra solo il lucchetto, aperto le sue app. */
@Composable
private fun PrivateCard(vm: KansoViewModel, modifier: Modifier) {
    KCard(modifier.animateContentSize(Motion.soft())) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (vm.privateLocked) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, Modifier.size(20.dp), tint = K.Muted)
                Spacer(Modifier.width(10.dp))
                Text("Spazio privato", style = MaterialTheme.typography.titleLarge, color = K.Paper, modifier = Modifier.weight(1f))
                Text(
                    if (vm.privateLocked) "Sblocca" else "Blocca", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                    modifier = Modifier.clip(CircleShape).background(K.SurfaceHigh).pressable { vm.togglePrivate() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            AnimatedVisibility(
                !vm.privateLocked && vm.privateApps.isNotEmpty(),
                enter = fadeIn(tween(300)) + expandVertically(Motion.soft()),
                exit = fadeOut(tween(150)) + shrinkVertically(Motion.soft()),
            ) {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    vm.privateApps.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Column(Modifier.weight(1f).pressable { vm.launchPrivate(app) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                    val icon by produceState(app.icon, app.key) { value = app.loadIcon() }
                                    Box(Modifier.size(48.dp)) {
                                        icon?.let { Image(it, null, Modifier.size(48.dp), colorFilter = if (vm.prefs.monochrome) grayscale else null) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    AppLabel(app.label, Modifier.padding(horizontal = 2.dp))
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
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
                    val from = rememberLaunchFrom()
                    Column(
                        Modifier.weight(1f).then(from.modifier).pressable(onLongClick = { onLongPress(app) }) { vm.open(app, from.options()) },
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
    var choosing by remember { mutableStateOf(false) }
    ModalCard(onDismiss) {
        Column(Modifier.animateContentSize(Motion.soft()), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(vm.appsRepo, app.pkg, 64.dp)
            Spacer(Modifier.height(14.dp))
            Text(app.label, style = MaterialTheme.typography.headlineMedium, color = K.Paper)
            Text(app.category.title, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            Spacer(Modifier.height(24.dp))
            AnimatedContent(
                choosing,
                transitionSpec = { fadeIn(tween(260, 80)) togetherWith fadeOut(tween(160)) },
                label = "appMenu",
            ) { pick ->
                if (pick) Column(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Category.entries.forEach { c ->
                        val current = c == app.category
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .background(if (current) K.Surface else K.SurfaceHigh)
                                .pressable(pressedScale = 0.98f) { vm.setCategory(app.pkg, c); onDismiss() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, color = K.Paper, modifier = Modifier.weight(1f))
                            if (current) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = K.Shu)
                        }
                    }
                } else Column {
                    PillButton("Aggiungi alla Home", Modifier.fillMaxWidth()) { onDismiss(); vm.guarded { vm.addTile(app.pkg) } }
                    Spacer(Modifier.height(10.dp))
                    PillButton("Cambia categoria", Modifier.fillMaxWidth(), filled = false) { choosing = true }
                    Spacer(Modifier.height(10.dp))
                    PillButton("Informazioni app", Modifier.fillMaxWidth(), filled = false) {
                        onDismiss()
                        vm.start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.pkg, null)))
                    }
                }
            }
        }
    }
}
