# Rideadent

**Deutsch:** [Zur deutschen Beschreibung](#deutsch) · **English:** [Go to English description](#english)

Rideadent ist ein Bluetooth-basierter Fahrassistent. / Rideadent is a Bluetooth-based driving assistant.

---

## Deutsch

**Sprache wechseln:** [Deutsch](#deutsch) · [English](#english)

### Über Rideadent

**Rideadent** (*Ride-a-dent*) verbindet *Ride* und *Assistant* zu einem eigenen Namen. Die App unterstützt ausgewählte Bluetooth-Geräte als Auslöser für die Blitzer.de-Überwachung und eine optionale Anrufautomatik.

### Funktionen

- **Getrennte Bluetooth-Auswahl:** Geräte individuell für Blitzer.de und die Anrufautomatik festlegen.
- **Blitzer.de:** Bei passender Bluetooth-Verbindung einen Startbefehl senden und die aktive Blitzer.de-Benachrichtigung regelmäßig prüfen.
- **Anrufautomatik:** Eingehende Anrufe bei aktiver Automatik abweisen und, soweit möglich, eine automatische SMS senden; mit 10-Minuten-Wiederholsperre pro Rufnummer.
- **Längere SMS:** Mehrteilige Nachrichten werden über Androids SMS-Funktionen unterstützt.
- **Startverhalten:** Nach einem Geräteneustart die Automatik wahlweise aktivieren; eine erneute Bluetooth-Verbindungsbenachrichtigung kann nötig sein.
- **Fahrmodus:** Hintergrunddienst mit Statusanzeige, Heartbeat und lokaler Diagnosehistorie.
- **Darstellung:** Hell, Dunkel oder Systemeinstellung; Deutsch und Englisch.

Die beiden Fahrfunktionen können unabhängig voneinander aktiviert und Bluetooth-Geräten zugeordnet werden.

### Voraussetzungen und Berechtigungen

- **Android 16 oder neuer** (mindestens API 36); Bluetooth-Hardware und für die jeweilige Automatik passende Geräte.
- **Bluetooth („Geräte in der Nähe“):** Verbindungen ausgewählter Geräte erkennen.
- **Anrufprüfung und Kontakte:** Android benötigt die Rolle für die Anrufprüfung; `READ_CONTACTS` ermöglicht auch die Prüfung von Anrufen gespeicherter Kontakte. Rideadent liest das Adressbuch nicht selbst aus.
- **SMS:** `SEND_SMS` für automatische Antworten; SMS können Kosten verursachen. Versand bedeutet nicht bestätigte Zustellung.
- **Benachrichtigungen:** Erlaubnis für die Fahrmodus-Benachrichtigung und Benachrichtigungszugriff zur Erkennung der Blitzer.de-Benachrichtigung. Die Kategorie **„Unterhaltungen“** kann beim Benachrichtigungszugriff deaktiviert werden.
- **Hintergrundbetrieb:** Abhängig von Android-Einstellungen kann es nötig sein, die Akkubeschränkungen für Rideadent anzupassen.

**Wichtig:** Das Vorhandensein einer Blitzer.de-Benachrichtigung ist ein Betriebsindikator, aber keine Garantie für die korrekte Warnfunktion. Nach einem Neustart oder bei Android-Hintergrundbeschränkungen können erneute Verbindungen beziehungsweise Einstellungen nötig sein. Rideadent ersetzt keine aufmerksame Fahrweise.

### Datenschutz

Geräteauswahl, Einstellungen und Diagnoseverlauf bleiben lokal auf dem Gerät. Die App besitzt keine Internet-Berechtigung. Android-Backups sind deaktiviert. Für automatische SMS werden Rufnummern und Zeitpunkte der letzten Sendeversuche lokal zur Wiederholsperre gespeichert.

### Installation und Entwicklung

- **Paketkennung:** `de.pritcloud.rideadent`
- **Entwicklungsbranch:** `dev/feasibility`
- **Testversionen:** [GitHub Releases](https://github.com/1260er/Rideadent/releases) – signierte Prereleases können mit Obtainium bezogen werden.
- **Erste Stable-Version `v1.0.0`:** noch nicht veröffentlicht; ausschließlich nach abgeschlossenen Tests und Freigabe.
- **Signierung:** Entwicklungs- und spätere Stable-APKs verwenden denselben dauerhaften Schlüssel. Eine abweichend signierte Installation lässt sich nicht direkt aktualisieren.

### Qualität und lokaler Build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
```

GitHub Actions führt dieselben Qualitätsprüfungen durch und überprüft die Signatur der veröffentlichten Entwicklungs-APK sowie des Release-Builds. Lokal ist ein signierter Release-Build nur mit den entsprechenden Signierumgebungsvariablen verfügbar; Schlüssel und Passwörter gehören **nicht** ins Repository.

**Änderungen:** [Changelog auf Deutsch](CHANGELOG.md#deutsch)

---

## English

**Switch language:** [Deutsch](#deutsch) · [English](#english)

### About Rideadent

**Rideadent** (*Ride-a-dent*) combines *Ride* and *Assistant* into a coined name. It uses selected Bluetooth devices to trigger Blitzer.de monitoring and an optional call-handling automation.

### Features

- **Separate Bluetooth selections:** Assign devices independently to Blitzer.de monitoring and call automation.
- **Blitzer.de:** Send a start command on a matching Bluetooth connection and periodically check for an active Blitzer.de notification.
- **Call automation:** Reject incoming calls when enabled and, where possible, send an automatic SMS with a ten-minute cooldown per phone number.
- **Long messages:** Supports multipart SMS through Android's SMS APIs.
- **Startup behavior:** Optionally enable automation after reboot; a fresh Bluetooth connection event may be required.
- **Driving mode:** Background service with status display, heartbeat and on-device diagnostic history.
- **Appearance:** Light, dark or system theme; German and English.

The two driving features can be enabled independently and assigned to different Bluetooth devices.

### Requirements and permissions

- **Android 16 or later** (minimum API 36), Bluetooth hardware and compatible devices for the chosen automation.
- **Bluetooth (Nearby devices):** Detect connections to selected devices.
- **Call screening and contacts:** Android requires the call-screening role; `READ_CONTACTS` allows screening calls from saved contacts as well. Rideadent does not read the address book itself.
- **SMS:** `SEND_SMS` is required for automatic replies. Carrier charges may apply; initiating a message is not confirmation of delivery.
- **Notifications:** Permission for the driving-mode notification and notification access for detecting Blitzer.de notifications. The **Conversations** notification-access category can be disabled.
- **Background operation:** Android battery restrictions may need to be adjusted for reliable monitoring.

**Important:** The presence of a Blitzer.de notification is an indicator, not a guarantee that warnings are operating correctly. A reboot or Android background restrictions may require a fresh connection or configuration. Rideadent does not replace attentive driving.

### Privacy

Device selections, settings and diagnostic history are stored locally. The app does not request internet access, and Android backups are disabled. For automatic SMS, phone numbers and the times of previous send attempts are stored locally to enforce the cooldown.

### Installation and development

- **Application ID:** `de.pritcloud.rideadent`
- **Development branch:** `dev/feasibility`
- **Test versions:** [GitHub Releases](https://github.com/1260er/Rideadent/releases) – signed prereleases can be installed through Obtainium.
- **First stable version `v1.0.0`:** not yet published; only after final testing and explicit approval.
- **Signing:** Development builds and future stable APKs use the same permanent signing key. An installation signed with a different key cannot be updated directly.

### Quality checks and local build

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
```

GitHub Actions runs these checks and verifies the certificate of both the published development APK and the release build. A locally signed release build requires the signing environment variables; never commit keys or passwords to Git.

**Changes:** [Changelog in English](CHANGELOG.md#english)
