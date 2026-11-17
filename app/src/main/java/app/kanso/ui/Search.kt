package app.kanso.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kanso.KansoViewModel
import app.kanso.data.AppEntry
import app.kanso.data.Contact
import app.kanso.data.looksLikeAddress
import app.kanso.data.looksLikeUrl
import app.kanso.data.mapsUri
import app.kanso.data.matchApps
import app.kanso.data.searchContacts
import app.kanso.data.siteUri
import app.kanso.data.webUri
import kotlinx.coroutines.delay

private sealed interface Result {
    val key: String
    data class App(val app: AppEntry) : Result { override val key = "a:" + app.pkg }
    data class Person(val contact: Contact) : Result { override val key = "c:" + contact.id }
    data class Place(val query: String) : Result { override val key = "maps" }
    data class Site(val query: String) : Result { override val key = "site" }
    data class Web(val query: String) : Result { override val key = "web" }
}

@Composable
fun SearchOverlay(vm: KansoViewModel) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    var contactsGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsGranted = it }

    LaunchedEffect(Unit) { delay(120); focus.requestFocus() }

    val contacts by produceState(emptyList<Contact>(), query, contactsGranted) {
        if (!contactsGranted || query.trim().length < 2) { value = emptyList(); return@produceState }
        delay(120) // aspetta che l'utente smetta di scrivere
        value = searchContacts(context, query)
    }
    val q = query.trim()
    val results = buildList {
        if (q.isNotEmpty()) {
            if (looksLikeAddress(q)) add(Result.Place(q))
            if (looksLikeUrl(q)) add(Result.Site(q))
            matchApps(vm.apps, q).forEach { add(Result.App(it)) }
            contacts.forEach { add(Result.Person(it)) }
            if (none { it is Result.Place } && q.length >= 4) add(Result.Place(q))
            add(Result.Web(q))
        }
    }

    fun run(r: Result) = when (r) {
        is Result.App -> vm.open(r.app)
        is Result.Person -> vm.start(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(r.contact.id, r.contact.lookup)))
        is Result.Place -> vm.view(mapsUri(r.query))
        is Result.Site -> vm.view(siteUri(r.query))
        is Result.Web -> vm.view(webUri(r.query))
    }

    Column(Modifier.fillMaxSize().background(K.Ink.copy(alpha = 0.82f)).statusBarsPadding().imePadding()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .height(58.dp)
                    .skin(CircleShape, K.SurfaceHigh, inset = true, border = K.Paper.copy(alpha = 0.16f))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = K.Paper)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Cerca app, persone, luoghi, web", style = MaterialTheme.typography.bodyLarge, color = K.Muted)
                    BasicTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth().focusRequester(focus),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = K.Paper),
                        cursorBrush = SolidColor(K.Shu),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let(::run) }),
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, "Cancella", Modifier.size(20.dp).clip(CircleShape).pressable { query = "" }, tint = K.Muted)
            }
            Text(
                "Annulla", style = MaterialTheme.typography.labelLarge, color = K.Paper,
                modifier = Modifier.clip(CircleShape).pressable { vm.searchOpen = false }.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)) {
            if (q.isEmpty()) item(key = "hint") { Hint(Modifier.animateItem()) }
            items(results, key = { it.key }) { r ->
                // Il primo risultato è quello che apre Invio: lo si evidenzia.
                val m = Modifier.animateItem(fadeInSpec = tween(220), placementSpec = Motion.soft(), fadeOutSpec = tween(120))
                    .then(if (r == results.first()) Modifier.background(K.SurfaceHigh, RoundedCornerShape(20.dp)) else Modifier)
                when (r) {
                    is Result.App -> AppRow(vm, r.app, m)
                    is Result.Person -> ResultRow(Icons.Rounded.Person, r.contact.name, r.contact.number, m,
                        trailing = { IconCircle(Icons.Rounded.Call, "Chiama") { vm.start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r.contact.number))) } },
                    ) { run(r) }
                    is Result.Place -> ResultRow(Icons.Rounded.Place, r.query, "Apri in Google Maps", m, accent = true) { run(r) }
                    is Result.Site -> ResultRow(Icons.Rounded.Language, r.query, "Apri il sito", m) { run(r) }
                    is Result.Web -> ResultRow(Icons.Rounded.Search, "Cerca «${r.query}»", "Sul web", m) { run(r) }
                }
            }
            if (q.isNotEmpty() && !contactsGranted) item(key = "perm") {
                ResultRow(Icons.Rounded.Person, "Cerca anche nei contatti", "Tocca per consentire l'accesso", Modifier.animateItem()) {
                    askContacts.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        }
    }
}

@Composable
private fun Hint(modifier: Modifier) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(700, 150, Motion.Calm)) }
    Column(modifier.padding(horizontal = 8.dp, vertical = 24.dp).graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 20f }) {
        Text("Cerca con intenzione.", style = MaterialTheme.typography.headlineMedium, color = K.Paper)
        Spacer(Modifier.height(10.dp))
        Text(
            "Un'app, il nome di una persona, un indirizzo come «via Roma 12, Milano» o qualsiasi domanda per il web.",
            style = MaterialTheme.typography.bodyMedium, color = K.Muted,
        )
    }
}

@Composable
private fun AppRow(vm: KansoViewModel, app: AppEntry, modifier: Modifier) {
    val from = rememberLaunchFrom()
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).pressable(pressedScale = 0.98f) { vm.open(app, from.options()) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(vm.appsRepo, app.pkg, 42.dp, modifier = from.modifier)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = K.Paper)
            Text(app.category.title, style = MaterialTheme.typography.labelMedium, color = K.Muted)
        }
        if (!vm.skipsFriction(app.pkg) && vm.prefs.friction && vm.isDistracting(app)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(K.Shu))
        }
        if (app.pkg !in vm.tiles) {
            Spacer(Modifier.width(12.dp))
            IconCircle(Icons.Rounded.Add, "Aggiungi alla Home") { vm.guarded { vm.addTile(app.pkg); vm.toast("${app.label} aggiunta alla Home") } }
        }
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector, title: String, subtitle: String, modifier: Modifier,
    accent: Boolean = false, trailing: @Composable () -> Unit = {}, onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).pressable(pressedScale = 0.98f, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(42.dp).skin(CircleShape, if (accent) K.Shu.copy(alpha = if (K.metro) 1f else if (K.skeuo) 0.5f else 0.18f) else K.SurfaceHigh, border = androidx.compose.ui.graphics.Color.Transparent, elevation = 3.dp),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = if (accent && !K.metro) K.Shu else K.Paper) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = K.Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = K.Muted, maxLines = 1)
        }
        trailing()
    }
}
