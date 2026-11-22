package app.kanso.data

import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Telephony

/**
 * Modalità classica: la Home come i telefoni di prima degli smartphone, una funzione per riquadro.
 * Ogni slot apre l'app predefinita del telefono per quella funzione, oppure quella scelta dall'utente.
 */
enum class Slot(val title: String, val key: String) {
    PHONE("Telefono", "phone"),
    MESSAGES("Messaggi", "messages"),
    CAMERA("Fotocamera", "camera"),
    GALLERY("Galleria", "gallery"),
    MAPS("Navigatore", "maps"),
    MUSIC("Musica", "music"),
    BROWSER("Browser", "browser"),
    MAIL("Mail", "mail"),
    FILES("File", "files"),
    CALCULATOR("Calcolatrice", "calculator"),
    CALENDAR("Calendario", "calendar"),
    ALARM("Sveglia", "alarm"),
}

/** Le app di messaggi più comuni: se installate, entrano da sole nello slot Messaggi accanto agli SMS. */
private val Messengers = listOf("com.whatsapp", "org.telegram.messenger", "com.whatsapp.w4b", "org.thoughtcrime.securesms")

/** I file manager più diffusi, per i telefoni che non dichiarano un'app File predefinita. */
private val FileManagers = listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer", "com.google.android.documentsui", "com.android.documentsui")

private fun selector(category: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)

/** Le app predefinite per ogni slot; lista vuota se il telefono non ne ha una. */
fun AppsRepository.slotDefaults(installed: Set<String>): Map<Slot, List<String>> = Slot.entries.associateWith { slot ->
    when (slot) {
        Slot.PHONE -> listOfNotNull(resolve(Intent(Intent.ACTION_DIAL)))
        Slot.MESSAGES -> listOfNotNull(
            Telephony.Sms.getDefaultSmsPackage(context) ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
        ) + Messengers.filter { it in installed }
        Slot.CAMERA -> listOfNotNull(resolve(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) ?: resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)))
        Slot.GALLERY -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_GALLERY)))
        Slot.MAPS -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_MAPS)) ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))))
        Slot.MUSIC -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_MUSIC)))
        Slot.BROWSER -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_BROWSER)) ?: resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))))
        Slot.MAIL -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_EMAIL)) ?: resolve(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))))
        Slot.FILES -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_FILES)) ?: FileManagers.firstOrNull { it in installed })
        Slot.CALCULATOR -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_CALCULATOR)))
        Slot.CALENDAR -> listOfNotNull(resolve(selector(Intent.CATEGORY_APP_CALENDAR)))
        Slot.ALARM -> listOfNotNull(resolve(Intent(AlarmClock.ACTION_SHOW_ALARMS)))
    }.distinct().filter { it in installed }
}
