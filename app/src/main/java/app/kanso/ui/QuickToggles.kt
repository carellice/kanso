package app.kanso.ui

import android.content.Context
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
