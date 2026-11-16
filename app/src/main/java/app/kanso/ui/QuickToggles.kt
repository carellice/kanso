package app.kanso.ui

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.kanso.KansoViewModel
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Vibration
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Id della fotocamera col flash, o null se il telefono non ha la torcia. */
fun torchCamera(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(CameraManager::class.java)
    cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
}.getOrNull()

/** Accende e spegne la torcia; segue anche i cambi fatti dalle impostazioni rapide. */
@Composable
fun TorchButton() {
    val ctx = LocalContext.current
    val id = remember { torchCamera(ctx) } ?: return
    val cm = remember { ctx.getSystemService(CameraManager::class.java) }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(id) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == id) on = enabled }
        }
        cm.registerTorchCallback(cb, Handler(Looper.getMainLooper()))
        onDispose { cm.unregisterTorchCallback(cb) }
    }
    IconCircle(
        if (on) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
        if (on) "Spegni la torcia" else "Accendi la torcia",
        active = on,
    ) { runCatching { cm.setTorchMode(id, !on) } }
}

/** Suoneria, vibrazione, silenzioso: ogni tocco passa alla modalità successiva. */
@Composable
fun RingerButton(vm: KansoViewModel) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(AudioManager::class.java) }
    var mode by remember { mutableStateOf(am.ringerMode) }
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { mode = am.ringerMode }
        }
        ContextCompat.registerReceiver(ctx, r, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { ctx.unregisterReceiver(r) }
    }
    val (icon, label) = when (mode) {
        AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration to "Vibrazione"
        AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff to "Silenzioso"
        else -> Icons.Rounded.NotificationsActive to "Suoneria"
    }
    IconCircle(icon, "$label: tocca per cambiare", active = mode != AudioManager.RINGER_MODE_NORMAL) {
        val next = when (mode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        // Entrare o uscire dal silenzioso tocca il "Non disturbare": Android chiede un permesso apposito.
        val dnd = next == AudioManager.RINGER_MODE_SILENT || mode == AudioManager.RINGER_MODE_SILENT
        val allowed = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        if (dnd && !allowed) {
            vm.toast("Consenti a Kanso l'accesso a \"Non disturbare\"")
            vm.start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } else runCatching { am.ringerMode = next; mode = next }
    }
}
