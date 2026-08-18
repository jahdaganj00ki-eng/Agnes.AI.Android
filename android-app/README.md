# Agnes Edit – native Android App

Native Android-App (Kotlin + Jetpack Compose) mit dem **Edit-Image**-Workflow
aus den Referenz-Screenshots, angebunden an das Agnes-AI-Gateway
(`https://apihub.agnes-ai.com/v1`).

## Workflow

1. Bild auswählen („+“)
2. Prompt eingeben (z. B. „Change color of shorts to black“)
3. Skills laden (`image-generation`, `image-generation / reference-image`, `image-prompt-craft`)
4. Bildanalyse + Prompt-Enhancement mit `agnes-2.5-flash`
5. Bildbearbeitung mit `agnes-image-2.1-flash` (image-to-image, Seitenverhältnis wird beibehalten)
6. Ergebnis anzeigen + Download-Button

## API-Key

Der Key wird **nicht** im Quellcode committet:

- **Lokal:** `android-app/local.properties` → `AGNES_API_KEY=sk-…` (gitignored)
- **GitHub Actions:** Secret `AGNES_API_KEY` (optional; ohne Key baut die App
  trotzdem und der Key kann in den In-App-Einstellungen eingegeben werden)
- **In der App:** Menü (☰) → Einstellungen → API-Key & Base-URL

## Build via GitHub Actions

Beim Push auf `main` (oder manuell über „Run workflow“) baut
`.github/workflows/android.yml` die Debug-APK und lädt sie als Artefakt
`agnes-edit-image-debug` hoch.

1. Optional: Repository-Secret `AGNES_API_KEY` setzen (Settings → Secrets and variables → Actions).
2. Push auf `main` oder Workflow manuell starten.
3. Artefakt im Run herunterladen und installieren (Debug-Signatur, „Unbekannte Quellen“ zulassen).

## Lokaler Build

```bash
cd android-app
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Voraussetzungen: JDK 17, Android SDK (`sdk.dir` in `local.properties`).

## Stack

- Kotlin 1.9, Jetpack Compose (Material 3, dunkles Theme)
- OkHttp + org.json (Agnes-AI-Gateway, OpenAI-kompatibel)
- minSdk 26, targetSdk 34
