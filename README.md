# Rideadent — Bluetooth-basierter Fahrassistent

**Ride-a-dent** verbindet als Wortspiel *Ride* (fahren) mit *Assistant* (Assistent).
Die Schreibweise mit Bindestrichen erklärt die Aussprache des App-Namens **Rideadent**.

## Funktionen

- Bluetooth-Geräte getrennt für Blitzer.de-Überwachung und Anruf-Automatik auswählen
- Blitzer.de bei passender Verbindung starten und regelmäßig überwachen
- Eingehende Anrufe optional abweisen und bei Bedarf eine automatische SMS senden
- Optionaler Start nach Geräteneustart
- Fahrüberwachung mit Heartbeat, Zustandsanzeige und lokaler Diagnosehistorie
- Deutsch und Englisch sowie helle und dunkle Darstellung

Die Automationen lassen sich unabhängig voneinander aktivieren. Für manche Funktionen
werden Android-Berechtigungen und geeignete Akku-Einstellungen benötigt.

## Android-Projekt

- Paketkennung: `de.pritcloud.rideadent`
- Java-Paket: `de.pritcloud.rideadent`
- Entwicklungsbranch: `dev/feasibility`
- Entwicklungs-Releases: signierte GitHub-Prereleases für Obtainium
- Eine Änderung der Paketkennung erfordert eine Neuinstallation. Die alte App
  und ihre Einstellungen werden dadurch nicht automatisch übernommen.

## Lokale Qualitätsprüfung

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease --console=plain
```

Der GitHub-Workflow prüft außerdem die Signatur der veröffentlichten APK.
Stable-Releases erfolgen nur nach ausdrücklicher Freigabe.

---

# Rideadent — Bluetooth driving assistant

**Ride-a-dent** combines *Ride* and *Assistant* as a coined name. The hyphens
illustrate how **Rideadent** is intended to be pronounced.

Rideadent uses selected Bluetooth devices to monitor Blitzer.de and optionally
handle incoming calls. Device selections, preferences and diagnostics remain on
the device. Android background execution and permissions must be configured
accordingly.

For development builds, run the Gradle checks shown above. The debug APK is
published as a signed GitHub prerelease after CI verification. Changing the
Android application ID means installing the renamed app separately.
