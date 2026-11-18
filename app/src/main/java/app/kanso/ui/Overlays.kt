package app.kanso.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.kanso.KansoViewModel
import app.kanso.data.AppEntry
import app.kanso.data.LockMode
import app.kanso.data.formatDuration
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

/** Velo scuro a tutto schermo che chiude la sovrapposizione se si tocca fuori. */
@Composable
fun Scrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(K.Ink.copy(alpha = 0.9f)).pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun ModalCard(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        val a = remember { Animatable(0f) }
        LaunchedEffect(Unit) { a.animateTo(1f, spring(0.78f, 380f)) }
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } }, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .padding(28.dp)
                    .graphicsLayer { alpha = a.value.coerceIn(0f, 1f); scaleX = 0.9f + 0.1f * a.value; scaleY = 0.9f + 0.1f * a.value }
                    .skin(RoundedCornerShape(32.dp), K.SurfaceHigh, elevation = 16.dp, stitched = 32.dp)
                    .pointerInput(Unit) { detectTapGestures { } }
                    // In orizzontale l'altezza è poca: la card non si allarga a tutto schermo e il contenuto scorre.
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(26.dp),
            ) { content() }
        }
    }
}

/**
 * L'attrito consapevole: un respiro prima di aprire un'app non essenziale.
 * Il pulsante "Apri" si sblocca solo a fine respiro; lasciar stare è sempre a un tocco.
 */
@Composable
fun FrictionPrompt(vm: KansoViewModel, app: AppEntry) {
    val distracting = vm.isDistracting(app)
    val seconds = if (distracting) vm.prefs.frictionSeconds else (vm.prefs.frictionSeconds / 2).coerceAtLeast(2)
    val progress = remember(app.pkg) { Animatable(0f) }
    LaunchedEffect(app.pkg) { progress.animateTo(1f, tween(seconds * 1000, easing = LinearEasing)) }
    val ready = progress.value >= 1f
    val breath = rememberInfiniteTransition(label = "breath")
    val b by breath.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = Motion.Standard), RepeatMode.Reverse), label = "b")
    val today = vm.usage?.perApp?.get(app.pkg)
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) { focus.clearFocus(); keyboard?.hide() }

    Scrim(vm::cancelFriction) {
        val landscape = isLandscape()
        val circle = @Composable {
            Box(Modifier.size(if (landscape) 168.dp else 196.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2
                    // Cerchio che respira: si allarga e si stringe lentamente.
                    drawCircle(K.Paper.copy(alpha = 0.05f + 0.05f * b), radius = r * (0.62f + 0.3f * b))
                    drawCircle(K.Paper.copy(alpha = 0.08f), radius = r - 3.dp.toPx(), style = Stroke(1.dp.toPx()))
                    drawArc(
                        if (ready) K.Matcha else K.Shu, -90f, 360f * progress.value, false,
                        topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                AppIcon(vm.appsRepo, app.pkg, 64.dp, modifier = Modifier.graphicsLayer { val s = 0.96f + 0.06f * b; scaleX = s; scaleY = s })
            }
        }
        val words = @Composable {
            Text("Vuoi davvero aprire\n${app.label}?", style = MaterialTheme.typography.headlineMedium, color = K.Paper, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(ready, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(200)) }, label = "breathText") { r ->
                Text(
                    when {
                        r && today != null && today > 60_000 -> "Oggi l'hai già usata per ${formatDuration(today)}."
                        r -> "Se è una scelta, è tua."
                        else -> if (b < 0.5f) "Inspira…" else "Espira…"
                    },
                    style = MaterialTheme.typography.bodyLarge, color = K.Muted, textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(if (landscape) 24.dp else 36.dp))
            PillButton("Lascia stare", Modifier.fillMaxWidth(), onClick = vm::cancelFriction)
            Spacer(Modifier.height(10.dp))
            PillButton(if (ready) "Apri ${app.label}" else "Un momento…", Modifier.fillMaxWidth(), filled = false, enabled = ready, onClick = vm::confirmFriction)
        }
        val m = Modifier.padding(horizontal = 32.dp).pointerInput(Unit) { detectTapGestures { } }
        // In orizzontale il cerchio del respiro sta a sinistra, domanda e pulsanti a destra.
        if (landscape) Row(m.widthIn(max = 720.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(48.dp)) {
            circle()
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { words() }
        } else Column(m.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            circle()
            Spacer(Modifier.height(28.dp))
            words()
        }
    }
}

@Composable
fun UnlockPrompt(vm: KansoViewModel) {
    Scrim(vm::cancelUnlock) {
        Box(Modifier.pointerInput(Unit) { detectTapGestures { } }) {
            when (vm.prefs.lockMode) {
                LockMode.HOLD -> HoldToUnlock(vm::unlocked)
                else -> PinPad(title = "Inserisci il PIN", check = vm.prefs::checkPin, onDone = { vm.unlocked() })
            }
        }
    }
}

/** Il gesto di sblocco: tenere premuto il cerchio finché si chiude. Lasciare prima annulla. */
@Composable
fun HoldToUnlock(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (holding) 0.92f else 1f, Motion.press(), label = "hold")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("Tieni premuto", style = MaterialTheme.typography.headlineMedium, color = K.Paper)
        Spacer(Modifier.height(8.dp))
        Text("per 3 secondi, per modificare", style = MaterialTheme.typography.bodyMedium, color = K.Muted)
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier
                .size(150.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        holding = true
                        val job = scope.launch {
                            progress.animateTo(1f, tween(((1f - progress.value) * 3000).toInt(), easing = LinearEasing))
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDone()
                        }
                        do { val e = awaitPointerEvent() } while (e.changes.any { it.pressed })
                        holding = false
                        if (progress.value < 1f) { job.cancel(); scope.launch { progress.animateTo(0f, tween(400, easing = Motion.Calm)) } }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(K.SurfaceHigh)
                drawCircle(K.Line, style = Stroke(1.dp.toPx()))
                drawArc(K.Paper, -90f, 360f * progress.value, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()))
            }
            Box(Modifier.size(14.dp).graphicsLayer { scaleX = 1 + progress.value * 3; scaleY = 1 + progress.value * 3 }.clip(CircleShape).background(K.Shu))
        }
    }
}

/**
 * Tastierino per 4 cifre. [check] restituisce true se il codice va bene;
 * se sbaglia, i pallini tremano e si svuotano.
 */
@Composable
fun PinPad(title: String, subtitle: String = "", check: (String) -> Boolean, onDone: (String) -> Unit) {
    var pin by remember(title) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    var error by remember(title) { mutableStateOf(false) }

    LaunchedEffect(pin) {
        if (pin.length < 4) return@LaunchedEffect
        delay(120)
        if (check(pin)) onDone(pin) else {
            error = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            shake.animateTo(0f, keyframesShake())
            pin = ""
        }
    }

    val landscape = isLandscape()
    val key = if (landscape) 60.dp else 72.dp
    val header = @Composable {
        AnimatedContent(title, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) }, label = "pinTitle") {
            Text(it, style = MaterialTheme.typography.headlineMedium, color = K.Paper)
        }
        Spacer(Modifier.height(6.dp))
        Text(if (error) "PIN errato, riprova" else subtitle, style = MaterialTheme.typography.bodyMedium, color = if (error) K.Alert else K.Muted)
        Spacer(Modifier.height(28.dp))
        Row(Modifier.graphicsLayer { translationX = shake.value }, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            repeat(4) { i ->
                val filled = i < pin.length
                val s by animateFloatAsState(if (filled) 1f else 0.55f, Motion.press(), label = "dot")
                Box(
                    Modifier.size(14.dp).graphicsLayer { scaleX = s; scaleY = s }
                        .then(
                            when {
                                !K.skeuo -> Modifier.clip(CircleShape).background(if (filled) K.Paper else K.Paper.copy(alpha = 0.18f))
                                filled -> Modifier.brass()
                                else -> Modifier.skin(CircleShape, K.Ink, inset = true)
                            },
                        ),
                )
            }
        }
    }
    val pad = @Composable {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "<")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(key)
                            .then(
                                when (k) {
                                    "" -> Modifier
                                    "<" -> Modifier.clip(CircleShape)
                                    else -> Modifier.skin(CircleShape, K.SurfaceHigh, border = Color.Transparent, elevation = 4.dp)
                                },
                            )
                            .then(if (k.isEmpty()) Modifier else Modifier.pressable(pressedScale = 0.88f) {
                                error = false
                                pin = if (k == "<") pin.dropLast(1) else if (pin.length < 4) pin + k else pin
                            }),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "<" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Cancella", tint = K.Muted)
                            "" -> Unit
                            else -> Text(k, style = MaterialTheme.typography.headlineMedium, color = K.Paper)
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (landscape) 8.dp else 14.dp))
        }
    }
    // In orizzontale il tastierino sta accanto al titolo: uno sotto l'altro non entrerebbero in altezza.
    if (landscape) Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { header() }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { pad() }
    } else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        header()
        Spacer(Modifier.height(36.dp))
        pad()
    }
}

private fun keyframesShake() = androidx.compose.animation.core.keyframes<Float> {
    durationMillis = 420
    -28f at 50; 24f at 110; -18f at 170; 12f at 230; -6f at 300; 0f at 420
}

/** Elenco di tutte le app per aggiungerne una alla Home. */
@Composable
fun AppPicker(vm: KansoViewModel) {
    var query by remember { mutableStateOf("") }
    val work = vm.showingWork
    val tiles = (if (work) vm.workTiles else vm.tiles).toSet()
    val list = remember(vm.apps, query, tiles) {
        val base = vm.apps.filter { it.pkg !in tiles }
        // distinct() su tutta la lista: un'app presente in entrambe le parti duplicherebbe la chiave e farebbe crashare l'elenco.
        if (query.isBlank()) base else (app.kanso.data.matchApps(base, query) + base.filter {
            app.kanso.data.normalize(it.label).contains(app.kanso.data.normalize(query.trim()))
        }).distinct()
    }
    Column(Modifier.fillMaxSize().backdrop(cloth = true).statusBarsPadding().sideInsetsPadding()) {
        Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (work) "Aggiungi a Lavoro" else "Aggiungi alla Home", style = MaterialTheme.typography.headlineMedium, color = K.Paper, modifier = Modifier.weight(1f))
            Text("Chiudi", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                modifier = Modifier.clip(CircleShape).pressable { vm.pickerOpen = false }.padding(12.dp))
        }
        Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { FieldBox(query, { query = it }, "Filtra le app") }
        // In orizzontale l'elenco va su due colonne.
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            androidx.compose.foundation.lazy.grid.GridCells.Fixed(if (isLandscape()) 2 else 1),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            modifier = Modifier.navigationBarsPadding(),
        ) {
            items(list.size, key = { list[it].pkg }) { i ->
                val app = list[i]
                Row(
                    Modifier.animateItem().fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .pressable(pressedScale = 0.98f) { vm.addTile(app.pkg, work); vm.pickerOpen = false }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(vm.appsRepo, app.pkg, 40.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(app.label, style = MaterialTheme.typography.titleMedium, color = K.Paper)
                        Text(app.category.title, style = MaterialTheme.typography.labelMedium, color = K.Muted)
                    }
                }
            }
        }
    }
}

@Composable
fun FieldBox(
    value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    Box(
        modifier.fillMaxWidth().height(54.dp).skin(RoundedCornerShape(18.dp), K.SurfaceHigh, inset = true).padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = K.Faint)
        androidx.compose.foundation.text.BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = K.Paper),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(K.Shu),
            visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        )
    }
}
