# Family Link (iOS-Design) – Kindersicherungs-App für Android

Eine tamper-resistente Kindersicherung für Android im **iOS-/Cupertino-Look**, funktional an
Google Family Link angelehnt. Native Umsetzung in **Kotlin + Jetpack Compose** – native
Android-Entwicklung ist hier die richtige Wahl, weil Device-Admin, Accessibility-Service,
Usage-Stats und System-Overlays tiefe Systemintegrationen sind, die Flutter nur umständlich
erreicht.

> ⚠️ **Rechtlicher Hinweis:** Diese App ist ausschließlich für die elterliche Aufsicht über die
> **eigenen minderjährigen Kinder** auf einem **eigenen Gerät** gedacht. Das heimliche
> Überwachen anderer (auch erwachsener) Personen ist in vielen Ländern strafbar. Kläre das
> Kind altersgerecht über die Nutzung auf.

---

## Funktionsumfang (laut Vorgabe)

### 1. System-Berechtigungen
| Berechtigung | Zweck | Umsetzung |
|---|---|---|
| **Device Administrator** | verhindert Deinstallation | `admin/DeviceAdmin.kt` – aktiviert **ohne** das Gerät zu sperren (`res/xml/device_admin.xml` deklariert keine automatisch erzwungene Sperre) |
| **Accessibility Service** | Vordergrund-App überwachen, Umgehung blocken | `service/AppAccessibilityService.kt` |
| **Usage Stats** | Nutzungszeit-Prüfung ab 00:00 Uhr | `util/Permissions.kt` + Tages-Reset in `data/Prefs.kt` |
| **System Alert Window** | Sperrbildschirm über allen Apps | `lock/LockOverlayManager.kt` |

### 2. Zeit-Limits & Sperr-Logik (`data/LimitEngine.kt`, `data/Prefs.kt`)
- **Echte Nutzungsmessung:** Die Zeit wird direkt aus dem **`UsageStatsManager`** des
  Betriebssystems gelesen (`util/UsageStatsTracker.kt`) – nicht mehr über einen selbst
  gebauten Zähler. Dadurch ist die Messung **präzise, überlebt einen Neustart des Dienstes**
  und startet garantiert um 00:00 Uhr.
- **Globales Limit:** Standard **1 Std**, maximal **2 Std**/Tag = Summe der Zeit aller
  `Standard`-Apps.
- **App-Kategorien:** `Plus` (immer erlaubt, zählt nie) · `Limit` (eigenes Tageslimit) ·
  `Standard` (teilt sich das globale Guthaben).
- **Präzision:** Der `MonitorService` prüft alle ~1,5 s → Sperre greift binnen ~2 s.
- **Gesperrte Apps:** werden protokolliert und im Eltern-Portal unter „Heute gesperrte Apps"
  mit genutzter Zeit angezeigt.
- **Ruhezeit (Bedtime):** konfigurierbares Fenster (Standard 20:00–06:00), sperrt komplett;
  optional mit **beruhigendem Ton** (gebündelte Ambient-Audiodatei, `res/raw/`).
- **Aus-Button:** deaktiviert alle Limits **bis 23:00 Uhr** des aktuellen Tages.

### 3. iOS-Design (`ui/`)
- Cupertino-Theme, iOS-Systemfarben, `CupertinoButton`, `CupertinoSwitch`, Settings-Karten.
- **PIN-Schutz:** iOS-Tastenfeld (`ui/cupertino/PinPad.kt`).
- **Sperrbildschirm:** großer Uhr-Anzeige, genutzter Zeit **und Telefon-/Notruf-Button**
  (`ui/screens/LockScreen.kt`).
- **Eltern-Portal:** Übersicht + Einstellungen, **nur 1×/Woche** ohne Aus-Button erreichbar.

### 4. Schutz vor Umgehung (`service/AppAccessibilityService.kt`)
- **Gastprofil / Nutzerwechsel:** wird erkannt und mit `GLOBAL_ACTION_HOME` abgebrochen.
- **Power-Menü / abgesicherter Modus:** wird per `GLOBAL_ACTION_BACK` weggeblendet.
- **Schnelleinstellungen** während einer Sperre: Shade wird wieder eingeklappt.
- **Overlay:** Vollbild, verschluckt BACK/MENU, blendet System-Bars aus.
- Selbstheilung: `BootReceiver` + `MonitorService` (START_STICKY) starten den Wächter nach
  Boot/Kill neu.

> **Hinweis zur „Unumgehbarkeit":** Ohne *Device-Owner*-Provisionierung sind Gastprofil-/
> Safe-Mode-Blockaden „best effort". Für harte Garantien siehe [Device Owner](#optional-device-owner)
> weiter unten.

---

## Projektstruktur
```
app/src/main/java/com/familylink/ios/
├─ App.kt, MainActivity.kt          # Einstieg + Navigation (Setup-Wizard / Home / Portal)
├─ admin/DeviceAdmin.kt             # Device-Administrator (blockt Deinstallation)
├─ service/
│  ├─ MonitorService.kt             # Foreground-Service, Sekunden-Tracking, löst Sperre aus
│  ├─ AppAccessibilityService.kt    # Vordergrund-Erkennung + Anti-Umgehung
│  └─ BootReceiver.kt               # Neustart nach Boot / Kill
├─ lock/                            # ausbruchssicheres Overlay (Compose in WindowManager)
├─ data/                            # Prefs, Modelle, LimitEngine (Kern-Logik)
├─ ui/                              # Cupertino-Theme, Komponenten, Screens
└─ util/                            # Permissions, ForegroundTracker, Zeit-Formatierung
```

---

## APK bauen

### Voraussetzungen
- **JDK 17**
- **Android SDK** (Platform **API 34**, Build-Tools 34.x). Am einfachsten über Android Studio
  (Giraffe/Koala oder neuer). Danach `local.properties` mit dem SDK-Pfad anlegen:
  ```properties
  sdk.dir=/pfad/zu/Android/Sdk
  ```
  (Android Studio erzeugt diese Datei automatisch beim ersten Öffnen des Projekts.)

### Debug-APK (zum Testen, sofort installierbar)
```bash
./gradlew assembleDebug
# Ergebnis: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release-APK
```bash
./gradlew assembleRelease
# Ergebnis: app/build/outputs/apk/release/app-release-unsigned.apk
```
Die Release-APK muss **signiert** werden, bevor sie installierbar ist. Signatur-Schlüssel
erzeugen und signieren:
```bash
keytool -genkey -v -keystore family-link.keystore -alias familylink \
        -keyalg RSA -keysize 2048 -validity 10000

$ANDROID_HOME/build-tools/34.0.0/apksigner sign \
        --ks family-link.keystore \
        --out app-release.apk \
        app/build/outputs/apk/release/app-release-unsigned.apk

adb install -r app-release.apk
```
Alternativ einen `signingConfig` in `app/build.gradle.kts` hinterlegen, dann erzeugt
`assembleRelease` direkt eine signierte APK.

---

## Online-Synchronisation (Eltern-Gerät ↔ Kinder-Gerät)

Die App läuft auf **beiden** Geräten aus derselben APK; beim ersten Start wählt man die Rolle.

### Server einrichten — dauerhaft kostenlos
Die Echtzeit-Sync nutzt eine **Firebase Realtime Database** über reines HTTPS – **kein Firebase-SDK
und keine `google-services.json` nötig**.

**Der „für immer gratis"-Weg ist der Spark-Plan.** Er ist kein Testzeitraum, sondern Googles
dauerhaft kostenlose Stufe: **keine Kreditkarte, läuft nicht ab**. Enthalten sind 1 GB Speicher und
10 GB Transfer pro Monat. Diese App überträgt pro Gerät nur wenige Kilobyte pro Minute — eine
Familie liegt im Jahr bei deutlich unter 1 % dieses Kontingents. Wichtig ist nur: **nicht** auf
„Blaze" (Pay-as-you-go) hochstufen, dann bleibt es kostenlos.

1. [console.firebase.google.com](https://console.firebase.google.com) → Projekt anlegen.
   Google Analytics kann man abwählen.
2. **Realtime Database** erstellen, Region wählen, im **gesperrten Modus** starten.
3. Unter **Regeln** die folgenden Regeln einfügen und veröffentlichen:

```json
{
  "rules": {
    "accounts": {
      "$account": {
        ".read": true,
        ".write": "!data.exists()"
      }
    },
    "families": {
      "$family": {
        ".read": true,
        ".write": true,
        ".validate": "$family.length >= 8"
      }
    }
  }
}
```

   Diese Regeln laufen **nicht ab** (anders als der 30-Tage-Testmodus) und verhindern, dass ein
   bestehendes Konto überschrieben wird.
4. Die Datenbank-URL kopieren, z. B.
   `https://mein-projekt-default-rtdb.europe-west1.firebasedatabase.app`, und beim Einrichten auf
   allen Geräten eintragen.

> **Ehrlicher Sicherheitshinweis:** Die Anmeldung läuft clientseitig gegen diese Datenbank; das
> Passwort wird nur gesalzen und gehasht gespeichert, nie im Klartext übertragen. Das trennt
> Familien sauber voneinander und hält Unbefugte fern, ist aber **kein Ersatz für einen echten
> Auth-Anbieter**. Wer maximale Sicherheit braucht, sollte Firebase Authentication ergänzen —
> das bleibt im Spark-Plan ebenfalls kostenlos.

### Konto & Geräte-Limit
- Beim Einrichten legt man ein **Familien-Konto** (E-Mail + Passwort) an bzw. meldet sich an.
- Pro Konto sind **maximal 5 Geräte** erlaubt. Ein sechstes Gerät wird abgewiesen, bis im Portal
  unter **Geräte** eines entfernt wurde.
- Das Portal zeigt alle Geräte mit Namen, Rolle und Online-Status.

### Geräte verbinden
1. **Eltern-Gerät:** App öffnen → „Eltern-Gerät" → URL eintragen → es wird ein **6-stelliger Code**
   erzeugt → „Familie erstellen".
2. **Kinder-Gerät:** App öffnen → „Kinder-Gerät" → dieselbe URL + den 6-stelligen Code eintragen →
   „Verbinden".
3. Danach auf dem Kindergerät die Berechtigungen erteilen und die Apps einordnen.

### Was synchronisiert wird
| Richtung | Inhalt | Geschwindigkeit |
|---|---|---|
| Eltern → Kind | Limits, Ruhezeit, App-Kategorien, Bonuszeit, Aus-Button, Einstellungs-Freigabe | **sofort** (offene SSE-Verbindung) |
| Kind → Eltern | Genutzte Zeit gesamt und pro App, gesperrte Apps, Ruhezeit-Status, Gerätename | alle ~10 s |

Technisch: `sync/SyncService.kt` hält eine dauerhafte **Server-Sent-Events**-Verbindung offen, sodass
eine Änderung im Eltern-Portal binnen etwa einer Sekunde auf dem Kindergerät greift. Ein
periodischer Push dient als Sicherheitsnetz, falls die Verbindung abbricht.

### Unterschiedliche Oberflächen
- **Eltern-Gerät:** das gewohnte Portal – Regeln setzen, Live-Nutzung des Kindes sehen.
  Es braucht **keine** Systemberechtigungen (es überwacht sich ja nicht selbst).
- **Kinder-Gerät:** eigenes Portal mit verbleibender Zeit, Ring-Anzeige, Liste „Heute genutzt"
  (App-Icons + Dauer + Balken), Hinweis auf die nächste Ruhezeit und Verbindungsstatus.

---

## Ersteinrichtung auf dem Kindergerät
1. APK installieren und App öffnen → **4-stellige PIN** festlegen (Eltern).
2. Im Berechtigungs-Schritt nacheinander erteilen: **Nutzungszugriff**, **Über anderen Apps
   anzeigen**, **Bedienungshilfe** und (empfohlen) **Geräteadministrator**.
3. Apps in **Plus / Limit / Standard** einordnen. Fertig.
4. Danach zeigt das Kindergerät die verbleibende Zeit. Das **Eltern-Portal** wird über die
   PIN geöffnet (1×/Woche bzw. jederzeit während eines aktiven Aus-Buttons).

---

## Geräteinhaber (Device Owner) — die unumgehbare Stufe

**Das ist der entscheidende Schritt.** Ohne ihn kann eine normale Android-App die HOME-Taste
nicht blockieren und Einstellungen nur „wegdrücken". Als Geräteinhaber erzwingt das
Betriebssystem selbst die Regeln.

### Einrichten (einmalig, ~10 Minuten)
Nur auf einem **frisch zurückgesetzten** Gerät möglich, **bevor** ein Google-Konto eingerichtet wird:

1. Gerät zurücksetzen (Einstellungen → System → Zurücksetzen) und die Ersteinrichtung
   durchlaufen — **kein Google-Konto hinzufügen, WLAN reicht**.
2. Entwickleroptionen aktivieren (Einstellungen → Über das Telefon → 7× auf „Build-Nummer").
3. **USB-Debugging** einschalten und das Gerät per Kabel an den PC anschließen.
4. APK installieren und die App **einmal öffnen**, Rolle „Kinder-Gerät" wählen.
5. Am PC ausführen:
   ```bash
   adb shell dpm set-device-owner com.familylink.ios/.admin.DeviceAdmin
   ```
   Erfolgsmeldung: `Success: Device owner set to package com.familylink.ios`
6. App neu starten → im Eltern-Portal steht unter **Schutz-Stufe** jetzt **„Maximal"**.

### Was dann vom System erzwungen wird
| Umgehungsversuch | Ohne Geräteinhaber | Mit Geräteinhaber |
|---|---|---|
| HOME-Taste bei Sperre | Sperre kommt nach ~1 s zurück | **Blockiert** (Lock-Task/Kiosk) |
| Einstellungen öffnen | wird weggedrückt | **App ausgeblendet** |
| Abgesicherter Modus | Best effort | **Vom System verboten** |
| Gastprofil / 2. Nutzer | Best effort | **Vom System verboten** |
| App deinstallieren | Geräteadmin blockt | **Zusätzlich systemseitig blockiert** |
| Auf Werkseinstellungen | möglich | **Vom System verboten** |
| Bedienungshilfe abschalten | möglich | **Nur unsere erlaubt** |
| ADB/Entwickleroptionen | möglich | **Vom System verboten** |

> **Was die App bewusst NIE tut:** `wipeData()` wird nirgendwo aufgerufen — die App kann
> **keine Daten löschen** und das Gerät nicht zurücksetzen. Alle Policies sind ausschließlich
> einschränkend oder schützend.

### Rückgängig machen
```bash
adb shell dpm remove-active-admin com.familylink.ios/.admin.DeviceAdmin
```
Oder im Eltern-Portal die Schutz-Stufe zurücksetzen.

---

## Technischer Stack
- Kotlin 1.9.24 · AGP 8.5.2 · Gradle 8.7
- Jetpack Compose (BOM 2024.09) · Material + Cupertino-eigene Komponenten
- minSdk 26 (Android 8.0) · targetSdk/compileSdk 34
