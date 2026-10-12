# Rideadent – Changelog

**Deutsch:** [Änderungen auf Deutsch](#deutsch) · **English:** [Changes in English](#english)

---

## Deutsch

**Sprache wechseln:** [Deutsch](#deutsch) · [English](#english) · [README](README.md#deutsch)

### v0.1.0-dev.34 – Entwicklungsversion

- Berechtigungs- und Akku-Kacheln passen ihre Höhe an den vollständigen Beschreibungstext an.
- Die Prüfung der uneingeschränkten Akkunutzung verwendet dieselbe Bedingung wie die Startseite.
- Der Status für „App bei Nichtnutzung pausieren“ erhält eine zusätzliche ausschließlich lesende Android-Abfrage; die Erkennung auf GrapheneOS ist noch im Gerätetest zu bestätigen.
- README und Changelog wurden vollständig zweisprachig aufbereitet und verlinkt.

### v0.1.0-dev.33 – Entwicklungsstand

- Bluetooth-Empfänger verarbeitet ausschließlich erwartete Verbindungs- und Trennungsereignisse und ignoriert ungültige Eingaben.
- Fahrdienst behandelt den Entzug der Bluetooth-Berechtigung kontrollierter.
- Automatische SMS unterstützt bei Bedarf den Versand mehrteiliger Nachrichten.
- Erläuterung zur Kontaktberechtigung für die Android-Anrufprüfung präzisiert.
- Isolierte Regressionstests und gemeinsame Qualitätsprüfung beider Stabilitätspakete erfolgreich durchgeführt.

### v0.1.0-dev.32 – Entwicklungsstand

- Letzten Hinweis zum Benachrichtigungszugriff ergänzt: Die Kategorie „Unterhaltungen“ kann deaktiviert werden.
- Dauerhaften Signierschlüssel eingerichtet; Entwicklungs- und zukünftige Stable-APKs sollen mit demselben Schlüssel signiert werden.
- GitHub Actions prüft die Signatur von Debug- und Release-APK.

### v0.1.0-dev.31 – Entwicklungsstand

- Benachrichtigungszugriff öffnet die allgemeine Android-Einstellungsübersicht statt einer auf bestimmten Systemversionen fehlerhaften Detailansicht.
- „Startverhalten“ auf der Startseite ist immer anklickbar, auch bei automatischem Start.
- Überflüssige „Status“-Überschrift auf der Startseite entfernt.

### v0.1.0-dev.30 – Entwicklungsstand

- Berechtigungsbezeichnungen verständlicher gestaltet und Zugänge zu Android-Berechtigungseinstellungen verbessert.

### Frühere Entwicklungsversionen

- In den Entwicklungsständen vor dev.30 wurden die Bluetooth-Automationen, der Fahrdienst, die Oberfläche, Diagnosefunktionen und Tests schrittweise aufgebaut. Eine vollständige Liste einzelner Zwischenversionen wird hier nicht nachträglich behauptet.

**Stable-Version `v1.0.0`: noch nicht veröffentlicht.**

---

## English

**Switch language:** [Deutsch](#deutsch) · [English](#english) · [README](README.md#english)

### v0.1.0-dev.34 – Development build

- Permission and battery cards now expand to display their complete descriptions.
- The unrestricted-battery status uses the same condition as the home screen.
- Pause-if-unused detection includes an additional read-only Android status query; behavior on GrapheneOS still requires device verification.
- README and changelog now offer full German and English sections with navigation links.

### v0.1.0-dev.33 – Development build

- The Bluetooth receiver now accepts only expected connection and disconnection events and ignores invalid inputs.
- The driving service handles revoked Bluetooth permission more safely.
- Automatic replies support multipart SMS when needed.
- Clarified why Android contacts permission matters for call screening.
- Isolated regression tests and combined quality checks for both stability packages completed successfully.

### v0.1.0-dev.32 – Development build

- Added notification-access guidance: the Conversations category may be disabled.
- Introduced a permanent signing key to be shared by development and future stable builds.
- GitHub Actions verifies both debug and release APK signatures.

### v0.1.0-dev.31 – Development build

- Notification access now opens the general Android settings list rather than a detail screen that can crash on some devices.
- The home screen's startup behavior line is always clickable, including when automatic startup is enabled.
- Removed the redundant Status heading on the home screen.

### v0.1.0-dev.30 – Development build

- Improved permission labels and navigation to Android permission settings.

### Earlier development builds

- Earlier builds gradually introduced Bluetooth automations, the driving service, the user interface, diagnostics and tests. No unverified release-by-release history is asserted here.

**Stable version `v1.0.0`: not yet released.**
