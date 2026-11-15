<p align="center"><img src="docs/icon.png" alt="Icona di Kanso" width="128"></p>

<h1 align="center">Kanso</h1>

Launcher Android open source che mette insieme semplicità e benessere digitale.
*Kanso* (簡素) è il principio estetico giapponese della semplicità: togliere il superfluo.

<p align="center">
  <img src="docs/screenshots/orologio.jpg" alt="Home con orologio" width="200">
  <img src="docs/screenshots/agenda.jpg" alt="Agenda e tempo schermo" width="200">
  <img src="docs/screenshots/libreria.jpg" alt="Libreria" width="200">
  <img src="docs/screenshots/attrito.jpg" alt="Attrito consapevole" width="200">
  <img src="docs/screenshots/ricerca.jpg" alt="Ricerca" width="200">
</p>

## Tre schermate, nessun App Drawer

| Sinistra | Centro | Destra |
|---|---|---|
| **Consapevolezza**: agenda dei prossimi 7 giorni, tempo schermo e sblocchi di oggi, grafico per ora, app più usate, riassunto AI delle notifiche su richiesta | **Home**: orologio facoltativo (spento di default), poi solo una griglia di riquadri tutti uguali (Telefono, Messaggi, Fotocamera, Galleria, Mappe di default) e la barra di ricerca | **Libreria**: tutte le altre app raggruppate per categoria in automatico |

- **Ricerca universale**: app, contatti, indirizzi (aperti in Google Maps), siti e ricerca web.
- **Attrito consapevole**: prima di aprire un'app fuori dalla Home compare "Vuoi davvero aprire…?" con un respiro guidato. Social, video, giochi e shopping aspettano la durata intera, le altre app la metà.
- **Blocco**: impostazioni e modifica della Home protette da PIN (salvato come hash SHA-256 con sale) o dal gesto "tieni premuto 3 secondi".
- **Riassunto notifiche** con Cloudflare Workers AI e la tua chiave personale. Kanso tiene solo una copia locale delle ultime 24 ore e invia il testo solo quando premi "Riassumi". Le notifiche restano gestite da Android.

## Permessi

- Accesso ai dati di utilizzo: tempo schermo e sblocchi (resta sul telefono).
- Lettura notifiche: solo per il riassunto.
- Contatti: solo per la ricerca.
- Calendario (sola lettura): solo per l'agenda.
- Internet: solo per il riassunto AI.

## Riassunto AI

Nelle impostazioni inserisci l'**Account ID** di Cloudflare e un **token API** con permesso *Workers AI*
(dash.cloudflare.com → My Profile → API Tokens). Modello usato: `@cf/meta/llama-3.3-70b-instruct-fp8-fast`.

## Installazione

Scarica `Kanso-vX.Y.Z.apk` dall'[ultima release](https://github.com/carellice/kanso/releases/latest), installalo e scegli Kanso come app Home (Impostazioni di Kanso → Kanso come Home). Android 9 o successivo.

## Build

```bash
./gradlew assembleDebug
```

Kotlin + Jetpack Compose, minSdk 28. Nessuna dipendenza oltre AndroidX.

## Licenza

MIT
