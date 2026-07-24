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

## Ersteinrichtung auf dem Kindergerät
1. APK installieren und App öffnen → **4-stellige PIN** festlegen (Eltern).
2. Im Berechtigungs-Schritt nacheinander erteilen: **Nutzungszugriff**, **Über anderen Apps
   anzeigen**, **Bedienungshilfe** und (empfohlen) **Geräteadministrator**.
3. Apps in **Plus / Limit / Standard** einordnen. Fertig.
4. Danach zeigt das Kindergerät die verbleibende Zeit. Das **Eltern-Portal** wird über die
   PIN geöffnet (1×/Woche bzw. jederzeit während eines aktiven Aus-Buttons).

---

## Optional: Device Owner (harte Garantien)
Für maximale Manipulationssicherheit kann die App als **Device Owner** provisioniert werden
(nur auf einem frisch zurückgesetzten Gerät, ohne bestehendes Google-Konto):
```bash
adb shell dpm set-device-owner com.familylink.ios/.admin.DeviceAdmin
```
Als Device Owner lassen sich Gastprofile, Safe-Mode und weitere Umgehungen **hart** sperren
(`DevicePolicyManager.addUserRestriction(...)`). Ohne diesen Modus arbeitet die App mit den
oben beschriebenen Best-Effort-Mechanismen.

---

## Technischer Stack
- Kotlin 1.9.24 · AGP 8.5.2 · Gradle 8.7
- Jetpack Compose (BOM 2024.09) · Material + Cupertino-eigene Komponenten
- minSdk 26 (Android 8.0) · targetSdk/compileSdk 34
