package app.kanso.ui

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kanso.BuildConfig
import app.kanso.KansoViewModel
import app.kanso.data.LockMode

@Composable
fun SettingsSheet(vm: KansoViewModel) {
    val context = LocalContext.current
    val p = vm.prefs
    var settingPin by remember { mutableStateOf(false) }
    val isHome = rememberIsHome(vm)
    val requestHome = rememberHomeRequest(vm)

    Box(Modifier.fillMaxSize().backdrop(cloth = true)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.padding(horizontal = 4.dp)) {
                PageHeader("Kanso", "Impostazioni") { PillButton("Fine", filled = false) { vm.settingsOpen = false } }
            }

            Section("Launcher") {
                Row_(
                    "Kanso come Home", if (isHome) "Attivo" else "Tocca per impostarlo come launcher predefinito", done = isHome,
                    onClick = requestHome,
                )
                Divider()
                Row_("Dati di utilizzo", "Tempo schermo e sblocchi", done = vm.hasUsageAccess) {
                    vm.start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Divider()
                Row_("Lettura notifiche", "Per il riassunto e per timer e cronometro in Home", done = vm.hasNotificationAccess) {
                    vm.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                Divider()
                Row_("Rivedi la presentazione", "Le schede del primo avvio") {
                    vm.settingsOpen = false; p.saveOnboarded(false)
                }
            }

            Section("Stile") {
                Segmented(listOf("Kanso", "Skeuo", "Modern UI", "Nothing", "Dueffe"), p.style.ordinal) { p.saveStyle(app.kanso.data.Style.entries[it]) }
                Spacer(Modifier.height(8.dp))
                Text(
                    when (p.style) {
                        app.kanso.data.Style.KANSO -> "Inchiostro, carta e un solo accento: essenziale."
                        app.kanso.data.Style.SKEUO -> "Cuoio cucito, ottone e rilievi, come gli oggetti veri."
                        app.kanso.data.Style.METRO -> "Nero pieno, riquadri vivi e testo sottile, come Windows Phone."
                        app.kanso.data.Style.NOTHING -> "Nero OLED, griglia di punti, cifre a matrice e un solo rosso segnale."
                        app.kanso.data.Style.DUEFFE -> "Fondo quasi nero, superfici calde, testo avorio e un solo accento oro."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                )
            }

            Section("Home") {
                Row_("Modifica riquadri", "Aggiungi, togli e riordina le app della Home") {
                    vm.settingsOpen = false; vm.editing = true
                }
                Divider()
                Toggle("Mostra l'orologio", "Ora e data in alto nella Home", p.showClock, p::saveShowClock)
                Divider()
                Toggle("Pulsante torcia", "In alto nella Home, accanto all'orologio", p.showTorch, p::saveShowTorch)
                Divider()
                Toggle("Pulsante suoneria", "Suoneria, vibrazione o silenzioso con un tocco", p.showRinger, p::saveShowRinger)
                Divider()
                Toggle("Icone in bianco e nero", "Nella Libreria: meno colore, meno richiamo", p.monochrome, p::saveMonochrome)
            }

            Section("Lavoro") { WorkSettings(vm) }

            Section("Attrito consapevole") {
                Toggle("Chiedi prima di aprire", "Per le app che non sono nella Home", p.friction, p::saveFriction)
                AnimatedVisibility(p.friction) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Label("Durata del respiro per le app che distraggono")
                        val options = listOf(3, 5, 10)
                        Segmented(options.map { "$it s" }, options.indexOf(p.frictionSeconds).coerceAtLeast(0)) { p.saveFrictionSeconds(options[it]) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Social, video, giochi e shopping aspettano la durata intera; le altre app la metà.",
                            style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                        )
                    }
                }
            }

            Section("Blocco") {
                Text(
                    "Protegge impostazioni e Home da modifiche accidentali.",
                    style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                )
                Spacer(Modifier.height(14.dp))
                Segmented(LockMode.entries.map { it.title }, p.lockMode.ordinal) { i ->
                    val mode = LockMode.entries[i]
                    if (mode == LockMode.PIN && !p.hasPin) settingPin = true else p.saveLockMode(mode)
                }
                Spacer(Modifier.height(8.dp))
                Text(p.lockMode.hint, style = MaterialTheme.typography.labelMedium, color = K.Faint)
                if (p.lockMode == LockMode.PIN) {
                    Spacer(Modifier.height(6.dp))
                    Row_("Cambia PIN", "") { settingPin = true }
                }
            }

            Section("Riassunto AI") {
                var account by remember { mutableStateOf(p.cfAccount) }
                var token by remember { mutableStateOf(p.cfToken) }
                val dirty = account.trim() != p.cfAccount || token.trim() != p.cfToken
                Text(
                    "Usa Cloudflare Workers AI con la tua chiave personale. Crea un token API con il permesso «Workers AI» " +
                        "su dash.cloudflare.com. Il testo delle notifiche lascia il telefono solo quando premi «Riassumi». " +
                        "Gli impegni dei prossimi 7 giorni vengono inviati per il riassunto dell'agenda in Home.",
                    style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                )
                Spacer(Modifier.height(16.dp))
                FieldBox(account, { account = it }, "Account ID")
                Spacer(Modifier.height(10.dp))
                FieldBox(token, { token = it }, "Token API", password = true)
                Spacer(Modifier.height(14.dp))
                PillButton(if (dirty) "Salva" else "Salvato", enabled = dirty, icon = if (dirty) null else Icons.Rounded.Check) {
                    p.saveCloudflare(account, token)
                }
            }

            Section("Diagnostica timer") {
                var report by remember { mutableStateOf<String?>(null) }
                Text(
                    "Avvia un timer o un cronometro nell'Orologio, poi tocca «Leggi»: qui compare cosa Kanso vede nella sua notifica.",
                    style = MaterialTheme.typography.bodyMedium, color = K.Muted,
                )
                Spacer(Modifier.height(14.dp))
                PillButton("Leggi", filled = false) {
                    report = app.kanso.data.Chrono.describe(vm.appContext, app.kanso.data.NotificationCollector.active())
                }
                report?.let {
                    Spacer(Modifier.height(14.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = K.Paper)
                    }
                }
            }

            Text(
                "Kanso ${BuildConfig.VERSION_NAME} · open source · nessun tracciamento",
                style = MaterialTheme.typography.labelMedium, color = K.Faint,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).navigationBarsPadding(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        AnimatedVisibility(settingPin, enter = fadeIn(tween(240)) + scaleIn(tween(400, easing = Motion.Calm), 0.96f), exit = fadeOut(tween(200))) {
            PinSetup(onCancel = { settingPin = false }) { pin ->
                p.setPin(pin); p.saveLockMode(LockMode.PIN); settingPin = false
            }
        }
    }
}

@Composable
private fun PinSetup(onCancel: () -> Unit, onDone: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    Scrim(onCancel) {
        Box(Modifier.background(K.Ink.copy(alpha = 0f))) {
            val f = first
            if (f == null) PinPad("Nuovo PIN", "Scegli 4 cifre", check = { true }, onDone = { first = it })
            else PinPad("Conferma PIN", "Ripeti le stesse cifre", check = { it == f }, onDone = onDone)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    KCard {
        Column {
            Eyebrow(title)
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = K.Paper)
    Spacer(Modifier.height(10.dp))
}

/** Vero se Kanso è il launcher predefinito; si ricontrolla a ogni ritorno nell'app. */
@Composable
fun rememberIsHome(vm: KansoViewModel): Boolean {
    val context = LocalContext.current
    return remember(vm.apps, vm.returnSignal) {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName == context.packageName
    }
}

/** Chiede di diventare la Home: il dialogo di sistema dove c'è, altrimenti le impostazioni. */
@Composable
fun rememberHomeRequest(vm: KansoViewModel): () -> Unit {
    val context = LocalContext.current
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    return {
        val rm = if (Build.VERSION.SDK_INT >= 29) context.getSystemService(RoleManager::class.java) else null
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            homeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
        } else vm.start(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}

@Composable
private fun Divider() = Groove()

@Composable
internal fun Row_(title: String, subtitle: String, done: Boolean? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).pressable(pressedScale = 0.98f, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = K.Paper)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
        }
        when (done) {
            true -> Box(Modifier.size(26.dp).clip(CircleShape).background(K.Matcha.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Fatto", Modifier.size(16.dp), tint = K.Matcha)
            }
            else -> Icon(Icons.Rounded.ChevronRight, null, tint = K.Muted)
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val knob by animateDpAsState(if (value) (if (K.skeuo) 24.dp else 22.dp) else 2.dp, Motion.press(), label = "knob")
    val track by animateColorAsState(if (value) (if (K.dueffe) K.Shu else K.Paper) else K.SurfaceHigher, tween(250), label = "track")
    val dot by animateColorAsState(if (value) (if (K.dueffe) DF.OnPrimary else K.Ink) else K.Muted, tween(250), label = "dot")
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f) { onChange(!value) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = K.Paper)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
        }
        Spacer(Modifier.width(12.dp))
        if (K.metro) {
            // Metro: l'interruttore di Windows Phone. Binario rettangolare bordato, riempito d'accento, e un cursore bianco.
            val fill by animateColorAsState(if (value) K.Shu else K.Ink, tween(200), label = "fill")
            val x by animateDpAsState(if (value) 42.dp else 0.dp, Motion.press(), label = "thumb")
            Box(Modifier.width(54.dp).height(26.dp)) {
                Box(Modifier.matchParentSize().border(2.dp, K.Paper).padding(5.dp).background(fill))
                Box(Modifier.offset(x = x).width(12.dp).fillMaxHeight().background(K.Paper))
            }
        } else if (K.skeuo) {
            // Levetta: binario incassato che si colora, pomello di metallo che scorre.
            val lit by animateColorAsState(if (value) K.Matcha else K.Ink, tween(250), label = "lit")
            Box(Modifier.width(52.dp).height(30.dp).skin(CircleShape, lit, inset = true)) {
                Box(Modifier.offset(x = knob, y = 2.dp).size(26.dp).knob())
            }
        } else Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(track)) {
            Box(Modifier.offset(x = knob, y = 2.dp).size(24.dp).clip(CircleShape).background(dot))
        }
    }
}

/** Controllo a segmenti con un indicatore che scivola sotto la scelta. */
@Composable
internal fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(46.dp).skin(CircleShape, K.SurfaceHigh, inset = true).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, Motion.soft(), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().then(
            when {
                K.metro -> Modifier.background(K.Shu)
                K.skeuo -> Modifier.brass()
                K.dueffe -> Modifier.gold()
                else -> Modifier.clip(CircleShape).background(K.Paper)
            },
        ))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val color by animateColorAsState(if (i == selected && !K.metro) (if (K.dueffe) DF.OnPrimary else K.Ink) else K.Paper, tween(250), label = "segText")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).pressable(pressedScale = 0.96f) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(
                        when { K.metro -> o.lowercase(); K.nothing -> o.uppercase(); else -> o },
                        // Con quattro voci il testo pieno non ci sta: si passa alla misura delle etichette.
                        style = if (options.size > 3) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                        color = color, maxLines = 1, softWrap = false,
                        // Con cinque voci una può non entrare: si rimpicciolisce invece di essere tagliata.
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }
}
