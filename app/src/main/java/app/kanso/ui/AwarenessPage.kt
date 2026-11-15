package app.kanso.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.kanso.KansoViewModel
import app.kanso.SummaryState
import app.kanso.data.DayUsage
import app.kanso.data.formatDuration
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AwarenessPage(vm: KansoViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val date = remember { SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date()) }
        PageHeader(date, "Consapevolezza") {
            IconCircle(Icons.Rounded.Tune, "Impostazioni") { vm.guarded { vm.settingsOpen = true } }
        }

        val usage = vm.usage
        if (!vm.hasUsageAccess) {
            PermissionCard(
                "Il tuo tempo, in chiaro",
                "Per mostrarti tempo schermo e sblocchi di oggi, Kanso ha bisogno dell'accesso ai dati di utilizzo. Restano sul telefono.",
                "Concedi accesso",
            ) { vm.start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        } else if (usage != null) {
            ScreenTimeCard(usage)
            TopAppsCard(vm, usage)
        }

        SummaryCard(vm)
        Spacer(Modifier.navigationBarsPadding().height(60.dp))
    }
}

@Composable
fun PermissionCard(title: String, body: String, action: String, onClick: () -> Unit) {
    KCard {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = K.Paper)
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
            Spacer(Modifier.height(18.dp))
            PillButton(action, onClick = onClick)
        }
    }
}

/** Il numero sale da zero fino al valore, una sola volta all'apertura. */
@Composable
private fun rememberCountUp(target: Float, delayMs: Long = 0): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(target) { delay(delayMs); a.animateTo(target, tween(1400, easing = Motion.Calm)) }
    return a.value
}

@Composable
private fun ScreenTimeCard(usage: DayUsage) {
    val minutes = rememberCountUp(usage.screenMs / 60_000f)
    val unlocks = rememberCountUp(usage.unlocks.toFloat(), 150)
    val m = minutes.toInt()
    KCard {
        Column {
            Eyebrow("Tempo schermo")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                if (m >= 60) {
                    Text("${m / 60}", style = MaterialTheme.typography.displayLarge, color = K.Paper)
                    Text(" h  ", style = MaterialTheme.typography.titleLarge, color = K.Muted, modifier = Modifier.padding(bottom = 10.dp))
                }
                Text("${m % 60}", style = MaterialTheme.typography.displayLarge, color = K.Paper)
                Text(" min", style = MaterialTheme.typography.titleLarge, color = K.Muted, modifier = Modifier.padding(bottom = 10.dp))
            }
            Spacer(Modifier.height(18.dp))
            HourlyChart(usage.hourly)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(K.Line))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Sblocchi")
                    Text("${unlocks.toInt()}", style = MaterialTheme.typography.headlineLarge, color = K.Paper)
                }
                Column(Modifier.weight(1f)) {
                    Eyebrow("In media")
                    val avg = if (usage.unlocks > 0) usage.screenMs / usage.unlocks else 0
                    Text(if (usage.unlocks > 0) formatDuration(avg) else "—", style = MaterialTheme.typography.titleLarge, color = K.Paper)
                    Text("per sblocco", style = MaterialTheme.typography.labelMedium, color = K.Muted)
                }
            }
        }
    }
}

@Composable
private fun HourlyChart(hourly: List<Long>) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) { grow.animateTo(1f, tween(1200, delayMillis = 200, easing = Motion.Calm)) }
    val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val max = (hourly.maxOrNull() ?: 0L).coerceAtLeast(15 * 60_000L).toFloat()
    Column {
        Canvas(Modifier.fillMaxWidth().height(72.dp)) {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * 23) / 24
            hourly.forEachIndexed { i, v ->
                val h = (v / max).coerceIn(0f, 1f) * size.height * grow.value
                val color = when {
                    i == now -> K.Shu
                    i > now -> K.Paper.copy(alpha = 0.06f)
                    else -> K.Paper.copy(alpha = 0.72f)
                }
                val barH = maxOf(h, 3.dp.toPx())
                drawRoundRect(color, Offset(i * (w + gap), size.height - barH), Size(w, barH), CornerRadius(w / 2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            listOf("0", "6", "12", "18", "24").forEachIndexed { i, t ->
                Text(t, style = MaterialTheme.typography.labelMedium, color = K.Faint)
                if (i < 4) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TopAppsCard(vm: KansoViewModel, usage: DayUsage) {
    // Solo app avviabili: launcher e servizi di sistema contano nel totale ma non in classifica.
    val top = usage.top.filter { it.first in vm.byPkg }.take(4)
    if (top.isEmpty()) return
    val max = top.first().second.toFloat()
    KCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Eyebrow("Dove è andato il tempo")
            top.forEachIndexed { i, (pkg, ms) ->
                val p = rememberCountUp(ms / max, 250L + i * 90)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(vm.appsRepo, pkg, 34.dp, monochrome = vm.prefs.monochrome)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(vm.byPkg[pkg]?.label ?: pkg, style = MaterialTheme.typography.titleMedium, color = K.Paper, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(formatDuration(ms), style = MaterialTheme.typography.labelMedium, color = K.Muted)
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(K.SurfaceHigher)) {
                            Box(Modifier.fillMaxWidth(p.coerceIn(0.02f, 1f)).height(4.dp).clip(CircleShape).background(if (i == 0) K.Shu else K.Paper.copy(alpha = 0.6f)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(vm: KansoViewModel) {
    if (!vm.hasNotificationAccess) {
        PermissionCard(
            "Le notifiche, in poche righe",
            "Kanso può leggere le notifiche per riassumerle quando lo chiedi tu. Non le tocca: restano gestite da Android.",
            "Consenti lettura notifiche",
        ) { vm.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        return
    }
    KCard(Modifier.animateContentSize(Motion.soft())) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Riassunto notifiche")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vm.noteCount == 1) "1 notifica nelle ultime 24 ore" else "${vm.noteCount} notifiche nelle ultime 24 ore",
                        style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            AnimatedContent(
                vm.summary,
                transitionSpec = { fadeIn(tween(400, 100)) togetherWith fadeOut(tween(200)) },
                contentKey = { it::class },
                label = "summary",
            ) { state ->
                when (state) {
                    SummaryState.Idle -> PillButton("Riassumi con l'AI", icon = Icons.Rounded.AutoAwesome) { vm.summarize() }
                    SummaryState.Loading -> BreathingDots()
                    is SummaryState.Done -> Column {
                        RevealLines(vm, state.text, state.apps)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Aggiorna", filled = false) { vm.summarize() }
                            PillButton("Chiudi", filled = false) { vm.dismissSummary() }
                        }
                    }
                    is SummaryState.Failed -> Column {
                        Text(state.message, style = MaterialTheme.typography.bodyMedium, color = K.Shu)
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PillButton("Riprova") { vm.summarize() }
                            PillButton("Impostazioni", filled = false) { vm.dismissSummary(); vm.guarded { vm.settingsOpen = true } }
                        }
                    }
                }
            }
        }
    }
}

/** Le righe del riassunto compaiono una dopo l'altra, salendo appena. */
@Composable
private fun RevealLines(vm: KansoViewModel, text: String, apps: Map<String, String>) {
    val lines = remember(text) { text.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    // Ogni riga inizia con «[App]»: al posto del nome mostro l'icona dell'app.
    val tagged = Regex("""^•?\s*\[([^\]]+)]\s*(.*)$""")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        lines.forEachIndexed { i, line ->
            val a = remember(text) { Animatable(0f) }
            LaunchedEffect(text) { delay(120L * i); a.animateTo(1f, tween(600, easing = Motion.Calm)) }
            val m = tagged.find(line)
            val label = m?.groupValues?.get(1)?.trim()
            val pkg = label?.let { l -> apps[l.lowercase()] ?: vm.apps.firstOrNull { it.label.equals(l, ignoreCase = true) }?.pkg }
            val from = rememberLaunchFrom()
            Row(
                Modifier
                    .graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 14.dp.toPx() }
                    .then(from.modifier)
                    // Un tocco sulla riga apre l'app da cui arriva la notifica.
                    .then(if (pkg != null) Modifier.clip(RoundedCornerShape(12.dp)).pressable(pressedScale = 0.98f) {
                        vm.notesOpen = false
                        vm.launch(pkg, from.options())
                    } else Modifier),
                verticalAlignment = Alignment.Top,
            ) {
                if (pkg != null) {
                    AppIcon(vm.appsRepo, pkg, 24.dp, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    when {
                        m == null -> line
                        pkg != null -> m.groupValues[2]
                        else -> "• $label · ${m.groupValues[2]}"
                    },
                    style = MaterialTheme.typography.bodyLarge, color = K.Paper,
                )
            }
        }
    }
}

@Composable
private fun BreathingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val s = t.animateFloat(0.4f, 1f, infiniteRepeatable(tween(700, easing = Motion.Standard), RepeatMode.Reverse, androidx.compose.animation.core.StartOffset(i * 160)), label = "d$i")
            Box(Modifier.size(9.dp).graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = s.value }.clip(CircleShape).background(K.Paper))
        }
        Spacer(Modifier.width(6.dp))
        Text("Sto leggendo con calma…", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
    }
}
