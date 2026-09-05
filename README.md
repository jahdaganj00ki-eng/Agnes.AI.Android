# Agnes.AI.Android

Rund um den **Edit-Image**-Workflow der Agnes-App: wie der Workflow intern
abläuft (Reverse Engineering), und zwei funktionierende Nachbauten auf Basis des
AgnesAI-Modell-Gateways — eine native Android-App und ein Node.js-Referenzdienst.

## Struktur

| Pfad | Zweck |
| --- | --- |
| [`android-app/`](android-app/README.md) | Native Android-App (Kotlin + Jetpack Compose), dunkles Theme, mit In-App-Einstellungen für API-Key und Base-URL. |
| [`agnes-image-service/`](agnes-image-service/README.md) | Node.js-Referenzimplementierung derselben Pipeline als HTTP-API plus Mini-Testseite. |
| [`docs/agnes-edit-image-workflow.md`](docs/agnes-edit-image-workflow.md) | Analyse der Original-App: Backend-Endpunkte, Skill-Katalog, SSE-Protokoll, Tool-Calls und der Umswitch auf die AgnesAI-Models. |
| [`.github/workflows/android.yml`](.github/workflows/android.yml) | CI: baut bei Push auf `main` die Debug-APK und lädt sie als Artefakt `agnes-edit-image-debug` hoch. |

## Der Workflow in 6 Schritten

1. Bild auswählen
2. Prompt eingeben (z. B. „Change color of shorts to black“)
3. Skill „Edit Image“ laden
4. Bildanalyse + Prompt-Enhancement mit `agnes-2.5-flash`
5. Bildbearbeitung (image-to-image) mit `agnes-image-2.1-flash` — Seitenverhältnis
   des Eingabebilds bleibt erhalten, nur die beschriebene Änderung wird umgesetzt
6. Ergebnis anzeigen und herunterladen

Das Gateway ist OpenAI-kompatibel und liegt unter
`https://apihub.agnes-ai.com/v1`
(siehe [`AgnesAI-Labs/AgnesAI-Models`](https://github.com/AgnesAI-Labs/AgnesAI-Models)).

## API-Key

Der Key wird nirgends im Quellcode committet:

- **Android lokal:** `android-app/local.properties` → `AGNES_API_KEY=sk-…` (gitignored)
- **Android CI:** Repository-Secret `AGNES_API_KEY` (optional — ohne Key baut die App
  trotzdem, der Key lässt sich in den In-App-Einstellungen setzen)
- **Service:** Umgebungsvariable `AGNES_API_KEY`

## Schnellstart

**Android-App bauen** (Voraussetzungen: JDK 17, Android SDK):

```bash
cd android-app
./gradlew :app:assembleDebug
```

Alternativ über GitHub Actions: Push auf `main` oder Workflow manuell starten,
Artefakt `agnes-edit-image-debug` herunterladen und installieren
(Debug-Signatur, „Unbekannte Quellen“ zulassen).

**Referenzdienst starten** (Node.js ≥ 18, keine externen Abhängigkeiten):

```bash
cd agnes-image-service
export AGNES_API_KEY=sk-...
npm start          # http://localhost:8080/
```

## Hinweis zu Binärdateien

Dieses Repo enthält bewusst **keine** Build-Artefakte oder Referenz-Screenshots —
`*.apk` und `*.aab` sind gitignored. APKs entstehen lokal im Build-Ordner oder als
CI-Artefakt des Android-Workflows.
