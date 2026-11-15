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
import app.kanso.BuildConfig
import app.kanso.KansoViewModel
import app.kanso.data.LockMode

@Composable
fun SettingsSheet(vm: KansoViewModel) {
    val context = LocalContext.current
    val p = vm.prefs
    var settingPin by remember { mutableStateOf(false) }
    val homeRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val isHome = remember(vm.apps) {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName == context.packageName
    }

    Box(Modifier.fillMaxSize().background(K.Ink)) {
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
                ) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        val rm = context.getSystemService(RoleManager::class.java)
                        if (rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                            homeRole.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME)); return@Row_
                        }
                    }
                    vm.start(Intent(Settings.ACTION_HOME_SETTINGS))
                }
                Divider()
                Row_("Dati di utilizzo", "Tempo schermo e sblocchi", done = vm.hasUsageAccess) {
                    vm.start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                Divider()
                Row_("Lettura notifiche", "Solo per il riassunto su richiesta", done = vm.hasNotificationAccess) {
                    vm.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }

            Section("Home") {
                Label("Colonne della griglia")
                Segmented(listOf("2", "3", "4"), (p.columns - 2).coerceIn(0, 2)) { p.saveColumns(it + 2) }
                Spacer(Modifier.height(6.dp))
                Row_("Modifica riquadri", "Aggiungi, togli e riordina le app della Home") {
                    vm.settingsOpen = false; vm.editing = true
                }
                Divider()
                Toggle("Mostra l'orologio", "Ora e data in alto nella Home", p.showClock, p::saveShowClock)
                Divider()
                Toggle("Icone in bianco e nero", "Nella Libreria: meno colore, meno richiamo", p.monochrome, p::saveMonochrome)
            }

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
                        "su dash.cloudflare.com. Il testo delle notifiche lascia il telefono solo quando premi «Riassumi».",
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

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(K.Line))

@Composable
private fun Row_(title: String, subtitle: String, done: Boolean? = null, onClick: () -> Unit) {
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
    val knob by animateDpAsState(if (value) 22.dp else 2.dp, Motion.press(), label = "knob")
    val track by animateColorAsState(if (value) K.Paper else K.SurfaceHigher, tween(250), label = "track")
    val dot by animateColorAsState(if (value) K.Ink else K.Muted, tween(250), label = "dot")
    Row(
        Modifier.fillMaxWidth().pressable(pressedScale = 0.98f) { onChange(!value) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = K.Paper)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = K.Muted)
        }
        Spacer(Modifier.width(12.dp))
        Box(Modifier.width(48.dp).height(28.dp).clip(CircleShape).background(track)) {
            Box(Modifier.offset(x = knob, y = 2.dp).size(24.dp).clip(CircleShape).background(dot))
        }
    }
}

/** Controllo a segmenti con un indicatore che scivola sotto la scelta. */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(K.SurfaceHigh).border(1.dp, K.Line, CircleShape).padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, Motion.soft(), label = "seg")
        Box(Modifier.offset(x = x).width(w).fillMaxHeight().clip(CircleShape).background(K.Paper))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, o ->
                val color by animateColorAsState(if (i == selected) K.Ink else K.Paper, tween(250), label = "segText")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).pressable(pressedScale = 0.96f) { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(o, style = MaterialTheme.typography.labelLarge, color = color)
                }
            }
        }
    }
}
