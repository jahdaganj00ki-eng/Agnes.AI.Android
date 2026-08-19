# Agnes AI Image Studio — Windows (Desktop, portabel)

Nativer Windows-Port der Android-App (Kotlin + Jetpack Compose → Compose
Multiplatform Desktop). Gleiche Kernlogik, gleiche Agnes-AI-Gateway-Modelle:

- Analyse/Prompt-Enhancement: `agnes-2.5-flash` (`POST /chat/completions`)
- Bildbearbeitung: `agnes-image-2.1-flash` (`POST /images/generations`)

## Voraussetzungen (lokal bauen)

- JDK **17** (`JAVA_HOME` gesetzt)
- Internetverbindung (Gradle lädt Abhängigkeiten + die App ruft das Agnes-Gateway auf)

Kein Android SDK nötig.

## Starten (Entwicklung)

```bash
./gradlew run          # Linux/macOS
gradlew.bat run        # Windows
```

## Portable Windows-App bauen (App-Image)

```bash
./gradlew createDistributable       # Linux/macOS
gradlew.bat createDistributable     # Windows
```

Ergebnis — ein **portabler Ordner** (den ganzen Ordner kopieren, keine
Installation):

```
build/compose/binaries/main/app/AgnesAI-Image-Studio/
    AgnesAI-Image-Studio.exe   ← starten
    app/                       ← Anwendung
    runtime/                   ← gebündeltes JRE
```

Auf jedem Windows-PC ohne Installation lauffähig; einfach den Ordner
(z. B. auf USB-Stick) kopieren und die `.exe` starten.

## API-Key konfigurieren

1. In der App: oben links **≡ (Menü) → Einstellungen** öffnen, API-Key + Base-URL
   eintragen, **Speichern**.
2. Die Werte werden in **`agnes-image-studio.properties`** gespeichert — **neben
   der `.exe`** (im App-Image-Ordner). Dadurch wandert der Key beim Kopieren des
   portablen Ordners mit.
   - Im Entwicklungsmodus (`gradlew run`) liegt die Datei stattdessen im
     Projektordner.
3. Alternativ per Umgebungsvariable:
   - `AGNES_API_KEY`
   - `AGNES_BASE_URL` (Standard: `https://apihub.agnes-ai.com/v1`)

## Modi

- **Edit Image** — gezielte Änderung, Rest bleibt unverändert
- **Full Body** — Ganzkörper-Framing
- **Enhance** — maximale Verbesserung (Details, Fehler entfernen, fotorealistisch, kein Comic-Look)
- **Black BG** — Hintergrund komplett schwarz, Motiv unverändert

## GitHub Actions

Der Workflow `.github/workflows/windows.yml` baut die portable App analog zum
Android-Workflow: Push auf `windows-app/**` oder manuell (`workflow_dispatch`),
JDK 17, `createDistributable`, Upload als Artefakt `agnes-ai-image-studio-windows`.

## Dateien

```
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/wrapper/…            (Gradle 8.9)
src/main/kotlin/com/agnes/studio/
    Main.kt                 Einstieg / Fenster
    App.kt                  UI + State + Einstellungen
    AgnesApi.kt             Agnes-Gateway-Client (OkHttp)
    EditImagePipeline.kt    Analyse + Prompt-Enhancement + Bildgenerierung
    ImageUtils.kt           Bild-Decode (Skia), Speichern, URL-Download
src/main/resources/
    app-icon.ico / .png     App-Icon (jpackage)
```
