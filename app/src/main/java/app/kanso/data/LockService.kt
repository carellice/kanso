package app.kanso.data

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/** Servizio di accessibilità usato solo per spegnere lo schermo col doppio tocco in Home: non legge nulla. */
class LockService : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        private var instance: LockService? = null

        /** Falso se il servizio non è attivo nelle impostazioni di accessibilità. */
        fun lock() = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun enabled(context: Context): Boolean {
            val me = ComponentName(context, LockService::class.java).flattenToString()
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { it.equals(me, ignoreCase = true) } == true
        }
    }
}
