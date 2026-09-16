# Agnes.AI — Deep Analysis für Windows-Port

> Grundlage: Reverse Engineering des Android-APK `Agnes 3.0.33/3.0.47` und der
> Referenzimplementierung in `agnes-image-service/`. Ziel: Eine Windows-Variante,
> die den Edit-Image-Workflow **1:1** wie die Android-App verhält.

---

## 1. Gesamtarchitektur

```
┌─────────────────────────────────────────────────────────────┐
│                     Agnes-App (Android)                      │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────────┐  │
│  │   UI        │  │  ViewModel   │  │   Data Layer      │  │
│  │ (Compose)   │◄─►│(StateFlow)   │◄─►│ (AgnesApi,        │  │
│  │             │  │              │  │  EditImagePipeline)│  │
│  └─────────────┘  └──────────────┘  └───────────────────┘  │
│         ▲                  ▲                   ▲             │
│         │                  │                   │             │
│    Skills-Anzeige     Workflow-Logik      HTTP (OkHttp)      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │  AgnesAI-Models Gateway │
              │  https://apihub.agnes-  │
              │  ai.com/v1             │
              │  ┌───────────────────┐  │
              │  │ /chat/completions │  │ ← agnes-2.5-flash
              │  │ /images/generations│ │ ← agnes-image-2.1-flash
              │  └───────────────────┘  │
              └─────────────────────────┘
```

Die App ist ein **dünner Client**: Die gesamte KI-Logik läuft auf dem
AgnesAI-Gateway. Das APK enthält keine Skill-Inhalte serverseitig — es sendet
nur `agent_type`, Prompt und Datei-URLs.

---

## 2. Projektstruktur (Android)

```
android-app/
├── app/src/main/java/com/agnes/editimage/
│   ├── MainActivity.kt              # Entry Point, Compose-Setup, Settings-Dialog
│   ├── data/
│   │   ├── AgnesApi.kt             # HTTP-Client (OkHttp) für Gateway
│   │   └── EditImagePipeline.kt    # Skills + Workflow-Logik (Analyse, Edit)
│   ├── ui/
│   │   ├── ChatScreen.kt           # Alle Composable-UI-Elemente
│   │   ├── EditImageViewModel.kt   # State-Management, submit()-Workflow
│   │   └── theme/Theme.kt          # Dark Theme (Farben)
│   └── util/
│       └── ImageUtils.kt           # Bild-Decoding, -Speichern, HTTP-Fetch
├── build.gradle.kts                 # Root-Build
└── app/build.gradle.kts             # App-Dependencies
```

**Abhängigkeiten:**
- Kotlin 1.9.24 + Jetpack Compose (BOM 2024.06.00)
- OkHttp 4.12.0 (HTTP)
- kotlinx-coroutines-android 1.8.1 (Async)
- AndroidX Lifecycle (ViewModel, StateFlow)
- Material3 + Material Icons Extended

---

## 3. Der 6-Schritte-Workflow (exakte Implementierung)

### Schritt 1 – Bild auswählen
**Datei:** `EditImageViewModel.kt:71-88`, `ChatScreen.kt:115-121`

Der Nutzer wählt Bilder über:
- **Android Gallery Picker** (`ActivityResultContracts.PickMultipleVisualMedia(10)`)
- **URL-Eingabe** (Dialog, `UrlInputDialog`)

Bilder werden als `Attachment` gespeichert:
```kotlin
sealed interface Attachment {
    data class Local(val bytes: ByteArray, val mime: String) : Attachment
    data class Remote(val url: String) : Attachment
}
```

**Windows-Äquivalent:**
- `System.Windows.Forms.OpenFileDialog` oder `Microsoft.Win32.OpenFileDialog`
- Mehrfachauswahl erlauben (bis 10 Bilder)
- URL-Dialog (einfacher Input)
- `Attachment`-Modell 1:1 übernehmen

---

### Schritt 2 – Prompt eingeben
**Datei:** `EditImageViewModel.kt:118-122`

Der Prompt wird im `UiState.input` gespeichert. Versand erfolgt nur wenn:
- `attachments.isNotEmpty()` (mindestens 1 Bild)
- `prompt.isNotBlank()`
- `!busy`

**Windows-Äquivalent:**
- TextBox im Input-Bar
- Gleiche Validierungslogik

---

### Schritt 3 – Skill laden (UI-Simulation)
**Datei:** `EditImageViewModel.kt:127-134`, `EditImagePipeline.kt:71-75`

In der Android-App werden **3 Skills** definiert und als `LoadedSkill` angezeigt:

```kotlin
val SKILLS: List<SkillDef> = listOf(
    SkillDef("image-generation", Skills.IMAGE_GENERATION),
    SkillDef("image-generation / reference-image", Skills.REFERENCE_IMAGE),
    SkillDef("image-prompt-craft", Skills.PROMPT_CRAFT),
)
```

Beim `submit()` werden sie sofort als `ChatItem.ThoughtGroup` eingefügt:
```kotlin
val skillLoads = SKILLS.map { LoadedSkill(it.badge, it.content.length) }
items += ChatItem.ThoughtGroup("…", skillLoads, expanded = true)  // base + 2
```

**Wichtig:** Das sind **keine echten Server-Skill-Ladevorgänge**. Die App
simuliert das Laden, indem sie die hartkodierten Skill-Definitionen anzeigt.
Der tatsächliche `load_skill` Tool-Call kommt erst im Backend (oder im
`agnes-image-service`).

**Windows-Äquivalent:**
- Die `SKILLS`-Liste und `Skills`-Object aus `EditImagePipeline.kt` **direkt
  übernehmen** (1:1 Kopie der Strings)
- `LoadedSkill`-Modell übernehmen
- Anzeige in der UI identisch gestalten

---

### Schritt 4 – Bildanalyse + Prompt-Enhancement
**Datei:** `EditImageViewModel.kt:148-172`, `EditImagePipeline.kt:104-133`

Aufruf:
```kotlin
val analysis = analyzeAndEnhance(api, imageDataUris, prompt)
```

Implementierung in `analyzeAndEnhance()`:
1. Baut `userContent` als JSONArray mit:
   - Text-Part: `"User edit instruction:\n\"\"\"\n$userPrompt\n\"\"\""`
   - Image-Part(s): `{ type: "image_url", image_url: { url: dataUri } }`
2. Ruft `api.chat()` auf mit:
   - `model = "agnes-2.5-flash"`
   - `systemPrompt = Skills.PROMPT_CRAFT`
   - `maxTokens = 1200`, `jsonMode = true`
3. Parst das JSON-Ergebnis mit `extractJson()`

**System-Prompt `Skills.PROMPT_CRAFT` (exakter Inhalt):**
```
You are the "image-prompt-craft" skill.
Analyse the attached image(s) and the user's edit instruction, then rewrite the
instruction into ONE precise, self-contained English edit prompt for an
image-to-image model. The user may write in English or German.
If several images are provided, they show the SAME person wearing the SAME
outfit: use them together to pin down the person's exact identity features
(face, hair, skin, body shape and proportions, clothing) so the output matches
the references faithfully.

POSE AND CAMERA RULES (apply to every rewrite, no exceptions):
- The person must keep the same camera angle and framing as the reference
  image(s). Never rotate the subject to a full side profile.
- Keep the body turned only slightly to one side: at most about 20 degrees from
  the camera. A full frontal pose is NOT desired; prefer a natural slight quarter turn.
- Weight shift: let the weight rest softly on one leg with a natural, moderate
  hip accent. No exaggerated hip thrust, no pronounced contrapposto, no
  theatrical pose. A confident, elegant stance is fine.
- Arms stay relaxed and below shoulder height whenever possible. At most ONE arm
  may be slightly raised. Never raise both arms at the same time, and never above
  the shoulders. No waving, no lifting, no gymnastic or theatrical arm positions.
- Keep the face and gaze toward the camera with a confident, inviting
  expression. A subtle smile is fine.

Also write a short German confirmation sentence telling the user what will be
changed (use informal "du", keep it to one sentence).

Return STRICT JSON only, no markdown, with exactly these keys:
{
  "analysis": "short description of what is visible in the image(s)",
  "edit_prompt": "the precise English edit instruction, explicitly preserving the person's identity, face, body proportions and clothing",
  "preserve": "explicit list of everything that must stay unchanged, including the person's identity, face, body proportions, clothing and aspect ratio",
  "reply_de": "short German confirmation sentence"
}
```

**Rückgabe (`Analysis` data class):**
```kotlin
data class Analysis(
    val analysis: String,
    val editPrompt: String,
    val preserve: String,
    val replyDe: String,
)
```

**Windows-Äquivalent:**
- `extractJson()`-Funktion 1:1 übernehmen (Regex für Markdown-Fences,
  Fallback auf `{...}`-Extraktion)
- `analyzeAndEnhance()` 1:1 übernehmen (HTTP POST an `/chat/completions`)

---

### Schritt 5 – Bildbearbeitung (image-to-image)
**Datei:** `EditImageViewModel.kt:174-220`, `EditImagePipeline.kt:152-220`

Aufruf:
```kotlin
val resultBytes = generateEdit(api, imageDataUris, analysis, ratio, "2K", s.mode)
```

Implementierung in `generateEdit()`:
1. **Prompt-Zusammenbau** (sehr detailliert):
   - `analysis.editPrompt`
   - Identity-Clause (1 Bild: "The subject must look exactly like...", >1 Bilder:
     "The subject must look exactly like the person in the provided reference images...")
   - Mode-Clause (`full_body`, `enhance`, `black_bg`)
   - Preserve-Clause
   - Allgemeine Schutz-Klauseln (Aspect Ratio, Arme, Pose)
   - **Angehängt:** `Skills.IMAGE_GENERATION` + `Skills.REFERENCE_IMAGE`

2. **Retry-Logik bei Content Policy:**
   - Max 4 Versuche (`MAX_CONTENT_POLICY_RETRIES = 4`)
   - Bei `content_policy_violation` wird der Prompt schrittweise
     entschärft (`softenPrompt()`)

3. **API-Call:**
   - `api.generateImage(model="agnes-image-2.1-flash", ...)`
   - `responseFormat = "b64_json"`
   - Ergebnis wird Base64-dekodiert zu `ByteArray`

**Mode-Clauses (exakte Strings):**
```kotlin
"full_body" -> " Render the person's full body from head to toe, keeping a full-body framing."
"enhance" -> " Enhance the image to maximum quality: restore and sharpen fine details, reduce noise, artifacts and blur, and improve clarity, lighting and skin texture. Remove any errors or defects. Keep it fully photorealistic — do NOT apply a comic, cartoon, illustration, painting or sketch style. Preserve the person's identity, face, body proportions, clothing and background exactly."
"black_bg" -> " Make the background completely solid black (pure black), with no other elements, objects, gradients or edges visible. Keep the subject fully unchanged: same person, same face, same body proportions, same clothing, same pose and same lighting on the subject."
```

**softenPrompt()-Stufen:**
```kotlin
1 -> "$prompt\n\nKeep the result tasteful and modest; the subject should remain appropriately covered."
2 -> "$prompt\n\nMake the result tasteful, modest, and fully clothed, with no revealing or suggestive elements."
3 -> "$prompt\n\nRender a conservative, tasteful, family-friendly version. Keep the subject fully clothed and avoid any skin exposure beyond the face, hands, and neckline."
else -> "Create a modest, tasteful, fully-clothed version of the requested edit, appropriate for all audiences. Original instruction: $prompt"
```

**Windows-Äquivalent:**
- `generateEdit()` 1:1 übernehmen
- Retry-Logik mit `softenPrompt()` exakt nachbauen
- Base64-Decodierung (in .NET: `Convert.FromBase64String`)

---

### Schritt 6 – Ergebnis anzeigen + Download
**Datei:** `EditImageViewModel.kt:181-193`, `ChatScreen.kt:514-555`

Nach erfolgreicher Generierung:
```kotlin
newItems += ChatItem.AssistantText("Ich habe die gewünschte Änderung vorgenommen.")
newItems += ChatItem.ResultImage(resultBytes)
```

Die `ResultImage` wird in der UI angezeigt mit:
- Bild-Rendering
- **3 Action-Buttons:**
  - ✏️ `Edit` → `useAsInput(item.bytes)` (weiterverwenden als neues Ausgangsbild)
  - 🔍 `Fullscreen` → `FullscreenImageDialog` (Zoom/Pan)
  - ⬇️ `Download` → `saveToGallery()` (speichert als PNG in Galerie)

**Windows-Äquivalent:**
- `System.Windows.Controls.Image` oder `ImageSharp`/`SkiaSharp` für Rendering
- Download: `System.IO.Path.Combine(Environment.GetFolderPath(...), "agnes_edit_...")`
- Fullscreen-Dialog (Windows Forms `Form` oder WPF `Window` mit Zoom/Pan)

---

## 4. Skills-System (komplett)

### Skill-Definitionen (hartkodiert)
**Datei:** `EditImagePipeline.kt:16-69`

Drei Skills, alle als `const val` Strings:

1. **`IMAGE_GENERATION`** (`badge: "image-generation"`)
   - Beschränkt Änderungen auf das vom Nutzer explizit Genannte
   - Konserviert Identität, Pose, Kleidung, Hintergrund, Licht
   - Verboten: Strecken/Stauchen, Neupositionierung

2. **`REFERENCE_IMAGE`** (`badge: "image-generation / reference-image"`)
   - Reference-Image-Modus: exakte Gesichts-/Körper-Erhaltung
   - Bei mehreren Bildern: alle zusammen zur Identitäts-Festlegung nutzen
   - Arme entspannt, nicht beide gleichzeitig heben

3. **`PROMPT_CRAFT`** (`badge: "image-prompt-craft"`)
   - Vision-Skill für Analyse + Prompt-Optimierung
   - Gibt JSON mit `analysis`, `edit_prompt`, `preserve`, `reply_de` zurück
   - Detaillierte Pose-/Kamera-Regeln (max 20° Drehung, Gewichtsverlagerung,
     Arme unter Schulterhöhe, etc.)

### Skill-Anzeige in der UI
**Datei:** `ChatScreen.kt:391-429`, `EditImageViewModel.kt:127`

Skills werden in einem **collapsiblen ThoughtGroup** angezeigt:
- Icon: `AutoStories` (Buch)
- Badge mit Skill-Name
- `"Loaded ${skill.chars} Chars"`
- Standardmäßig expanded = `true` beim ersten Laden

**Windows-Äquivalent:**
- Expander/Collapsible Panel in der UI
- Identische Badge-Anzeige
- Identische Char-Zählung (`content.length`)

---

## 5. API-Vertrag (AgnesApi.kt)

### Basis-URL
Standard: `https://apihub.agnes-ai.com/v1`
Konfigurierbar via Settings (persistiert in `SharedPreferences`)

### OkHttp-Client
```kotlin
private val client = OkHttpClient.Builder()
    .connectTimeout(60, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .build()
```

**Windows-Äquivalent:**
- `HttpClient` aus `System.Net.Http`
- Timeouts: Connect 60s, Read 300s, Write 120s

### Endpoint 1: Chat Completion
**Pfad:** `POST /chat/completions`
**Auth:** `Authorization: Bearer <apiKey>`

Request:
```json
{
  "model": "agnes-2.5-flash",
  "messages": [
    { "role": "system", "content": "<system-prompt>" },
    { "role": "user", "content": [ /* JSONArray: text + image_url parts */ ] }
  ],
  "max_tokens": 1200,
  "response_format": { "type": "json_object" }
}
```

Response:
```json
{
  "choices": [
    { "message": { "content": "<JSON-string>" } }
  ]
}
```

### Endpoint 2: Image Generation
**Pfad:** `POST /images/generations`
**Auth:** `Authorization: Bearer <apiKey>`

Request:
```json
{
  "model": "agnes-image-2.1-flash",
  "prompt": "<optimierter-prompt>",
  "size": "2K",
  "ratio": "3:4",
  "extra_body": {
    "image": ["data:image/jpeg;base64,..."],
    "response_format": "b64_json"
  }
}
```

**WICHTIG:** `response_format` gehört **in** `extra_body`, nicht auf Top-Level!

Response:
```json
{
  "data": [
    {
      "b64_json": "<base64-bilddaten>",
      "url": "...",
      "revised_prompt": "..."
    }
  ]
}
```

---

## 6. Datenmodelle (komplett)

### Core Models
```kotlin
// EditImagePipeline.kt
data class SkillDef(val badge: String, val content: String)
data class Analysis(val analysis: String, val editPrompt: String, val preserve: String, val replyDe: String)

// EditImageViewModel.kt
data class LoadedSkill(val badge: String, val chars: Int)

sealed interface Attachment {
    data class Local(val bytes: ByteArray, val mime: String) : Attachment
    data class Remote(val url: String) : Attachment
}

sealed interface ChatItem {
    data class UserMessage(val text: String, val images: List<Attachment>) : ChatItem
    data class ThoughtGroup(val durationSeconds: String, val skills: List<LoadedSkill>, val expanded: Boolean) : ChatItem
    data class AssistantText(val text: String) : ChatItem
    data class PromptEnhancement(val original: String, val enhanced: String) : ChatItem
    data class StatusBanner(val text: String, val active: Boolean = true) : ChatItem
    data class ResultImage(val bytes: ByteArray) : ChatItem
    data class Error(val message: String) : ChatItem
}

data class UiState(
    val items: List<ChatItem> = emptyList(),
    val busy: Boolean = false,
    val input: String = "",
    val attachments: List<Attachment> = emptyList(),
    val mode: String = "edit",
    val title: String = "Edit Image",
    val apiKeyConfigured: Boolean = false,
    val savedApiKey: String = "",
    val savedBaseUrl: String = "",
    val lastSaved: Boolean = false,
)
```

### API Models
```kotlin
// AgnesApi.kt
data class GeneratedImage(val b64: String?, val url: String?, val revisedPrompt: String?)
```

---

## 7. State Management & Workflow-Ablauf

### UiState-Änderungen während `submit()`

```
base = items.size  (vor dem Submit)

[base + 0] UserMessage(prompt, images)
[base + 1] ThoughtGroup("…", emptyList, expanded=false)          // Analysis (leer)
[base + 2] ThoughtGroup("…", skillLoads, expanded=true)          // Skills
[base + 3] AssistantText("")                                     // reply_de (leer)
[base + 4] PromptEnhancement(prompt, "")                         // edit_prompt (leer)
[base + 5] StatusBanner("Das dauert etwa 15–45 Sekunden...", active=true)

Nach Analyse:
[base + 1] ThoughtGroup(duration, skills=empty, expanded=false)
[base + 3] AssistantText(analysis.replyDe)
[base + 4] PromptEnhancement(prompt, analysis.editPrompt)

Nach Bearbeitung:
[base + 2] ThoughtGroup(duration, skills=skillLoads, expanded=true)
[base + 5] StatusBanner("Bearbeitung abgeschlossen.", active=false)
[+] AssistantText("Ich habe die gewünschte Änderung vorgenommen.")
[+] ResultImage(resultBytes)
```

### Modi (Action Pills)
```kotlin
"edit"        // Standard: Edit Image
"full_body"   // Full Body Mode
"enhance"     // Enhance Mode
"black_bg"    // Black Background Mode
// "Try-On" ist deaktiviert (onClick = null)
```

---

## 8. Settings-System

**Datei:** `EditImageViewModel.kt:54-58, 206-223`, `MainActivity.kt:61-152`

- **Speicher:** Android `SharedPreferences` ("agnes_settings")
- **Keys:**
  - `api_key` → `BuildConfig.AGNES_API_KEY` als Fallback
  - `base_url` → `BuildConfig.AGNES_BASE_URL` als Fallback (Standard:
    `"https://apihub.agnes-ai.com/v1"`)
- **UI:** `SettingsDialog` (AlertDialog) mit:
  - API-Key Feld (PasswordVisualTransformation, Show/Hide Toggle)
  - Base-URL Feld
  - Speichern/Abbrechen Buttons
  - "Gespeichert ✓" Feedback nach Save

**Windows-Äquivalent:**
- JSON-Datei im AppData-Ordner (`%APPDATA%\AgnesEditImage\settings.json`)
- Oder `Microsoft.Win32.Registry` (CurrentUser)
- Einstellungen-Dialog (Windows Forms `Form` oder WPF `Window`)

---

## 9. Bildverarbeitung

### imageDimensions (Kotlin)
**Datei:** `ImageUtils.kt:21-30`
- Nutzt `BitmapFactory.Options().apply { inJustDecodeBounds = true }`
- Gibt `Pair<Int, Int>` (width, height) zurück

### decodeBitmap (Kotlin)
**Datei:** `ImageUtils.kt:32-42`
- Downsampling auf `maxSize = 2048`
- Berechnet `inSampleSize` (Potenz von 2)

### saveToGallery (Kotlin)
**Datei:** `ImageUtils.kt:44-73`
- Speichert als PNG in `Pictures/agnes_edit_<timestamp>.png`
- Nutzt `MediaStore` (API >= 29) oder `MediaStore.EXTERNAL_CONTENT_URI`

### fetchBytes (Kotlin)
**Datei:** `ImageUtils.kt:75-84`
- Lädt Remote-Bilder via HTTP (OkHttp)
- Timeout: 20s Connect, 60s Read

### pickRatio (Kotlin)
**Datei:** `EditImagePipeline.kt:223-246`
- Mappt Pixel-Dimension auf nächstgelegene Ratio:
  `1:1, 3:4, 2:3, 9:16, 4:3, 3:2, 16:9, 21:9`
- Nutzt logarithmischen Abstand

**Windows-Äquivalent:**
- `System.Drawing.Common` (nur Windows) oder `SixLabors.ImageSharp` (cross-platform)
- `ImageSharp` für Dimensions, Decoding, Encoding
- `HttpClient` für Remote-Fetch
- Ratio-Picking-Logik exakt übernehmen

---

## 10. UI-Struktur (ChatScreen.kt)

### Haupt-Composable: `EditImageScreen`
Verwendet `Scaffold` mit:
- **TopBar:** `AppHeader` (Menü-Button, Titel, "Neuer Chat"-Button)
- **BottomBar:** `ActionPills` + `InputBar`
- **Content:** `LazyColumn` mit Chat-Items

### Chat-Item-Typen (Rendering)
| Typ | Composable | Beschreibung |
|-----|-----------|-------------|
| `UserMessage` | `UserMessageItem` | Weißer Bubble, rechtsbündig, mit Thumbnails |
| `ThoughtGroup` | `ThoughtGroupItem` | Graue Card, Memory-Icon, expandierbar, Skills-Liste |
| `AssistantText` | `AssistantTextItem` | Graue Card, Text |
| `PromptEnhancement` | `PromptEnhancementItem` | Graue Card, "Original prompt" + "Enhanced prompt" Blöcke |
| `StatusBanner` | `StatusBannerItem` | Kleine Card mit Spinner/Check-Icon |
| `ResultImage` | `ResultImageItem` | Bild mit 3 Overlay-Buttons (Edit, Fullscreen, Download) |
| `Error` | `ErrorItem` | Rote Fehlermeldung |

### Farbschema (Dark Theme)
```kotlin
AppBackground    = Color(0xFF121212)
CardBackground   = Color(0xFF1E1E1E)
CardBackground2  = Color(0xFF2A2A2E)
UserBubble       = Color(0xFF8184FD)
TealBadge        = Color(0xFF2DD4BF)
TextPrimary      = Color(0xFFF5F5F5)
TextMuted        = Color(0xFF9E9E9E)
SparkleCyan      = Color(0xFF8AD8F0)
ErrorRed         = Color(0xFFEF6A6A)
```

---

## 11. Referenzimplementierung (Node.js)

### agnes-image-service
**Dateien:**
- `src/agnesClient.js` — HTTP-Client (fetch-basiert)
- `src/pipeline.js` — Workflow (Analyse + Edit)
- `src/server.js` — HTTP-Server + Mini-Testseite

### Wichtige Unterschiede zur Android-App
1. **Skill-Definition:** Node.js nutzt ein einziges `EDIT_IMAGE_SKILL` statt
   drei separaten Skills (IMAGE_GENERATION, REFERENCE_IMAGE, PROMPT_CRAFT)
2. **Prompt-Aufbau:** In `pipeline.js:272` wird der Prompt einfacher zusammengebaut
   (keine Mode-Clauses wie `full_body`, `enhance`, `black_bg`)
3. **Skill-Anzeige:** Node.js hat keine UI für Skills

**Für Windows-Port:** Die Android-App-Logik ist die **maßgebliche** Quelle.
Die Node.js-Referenz dient nur als Backup-Verständnis.

---

## 12. Exakte Prompts (unverändert übernehmen)

### Skills.PROMPT_CRAFT
(Siehe Abschnitt 4 — vollständiger Text dort)

### Skills.IMAGE_GENERATION
(Siehe Abschnitt 4 — vollständiger Text dort)

### Skills.REFERENCE_IMAGE
(Siehe Abschnitt 4 — vollständiger Text dort)

### Identity-Clause (1 Bild)
```
The subject must look exactly like the person in the reference image: same face,
same identity, same body shape and proportions, and the same clothing.
```

### Identity-Clause (>1 Bilder)
```
The subject must look exactly like the person in the provided reference images:
same face, same identity, same hair, same body shape and proportions, and the
same clothing. Use all reference images together to reconstruct the person faithfully.
```

---

## 13. Error Handling

### Content Policy Violations
- Erkannt an `e.message?.contains("content_policy_violation", ignoreCase = true)`
- Bis zu 4 Retries mit zunehmend entschärften Prompts (`softenPrompt`)
- Danach: Exception werfen

### Allgemeine Fehler
- Werden als `ChatItem.Error` angezeigt
- Status-Banner aktualisiert auf "Bearbeitung fehlgeschlagen."
- `busy = false`

---

## 14. Was für Windows exakt übernommen werden muss

### Datenmodelle (1:1)
- `SkillDef`, `Analysis`, `LoadedSkill`
- `Attachment` (Local/Remote)
- `ChatItem` (alle 7 Typen)
- `UiState`
- `GeneratedImage`

### Logik (1:1)
- `analyzeAndEnhance()` + `extractJson()`
- `generateEdit()` + `softenPrompt()` + `isContentPolicyViolation()`
- `pickRatio()`
- `Skills`-Object mit allen 3 Konstanten
- `SKILLS`-Liste

### API (1:1)
- `AgnesApi.chat()` → `POST /chat/completions`
- `AgnesApi.generateImage()` → `POST /images/generations`
- OkHttp-Timeouts (60/300/120s)

### UI-Logik
- `submit()`-Workflow mit den exakten `base + N` Indizes
- Action Pills: `edit`, `full_body`, `enhance`, `black_bg`
- ThoughtGroup expanded-Logik
- PromptEnhancement-Anzeige (Original + Enhanced)
- ResultImage-Actions (Edit, Fullscreen, Download)

### Skills-Inhalte
- Die drei `Skills.*` Strings **exakt** wie in `EditImagePipeline.kt`

### Settings
- API-Key + Base-URL persistent speichern
- Fallback auf `https://apihub.agnes-ai.com/v1`
- Settings-Dialog mit Key-Show/Hide

---

## 15. Technologie-Empfehlung für Windows

### Option A: WPF (.NET 8/9)
- **Vorteile:** native Windows-Optik, gute Image-Unterstützung,
  `BitmapImage`/`WriteableBitmap` für Bilddarstellung
- Nachteil: Steile Lernkurve, XAML

### Option B: WinForms + modernes Rendering
- **Vorteile:** Einfacher, schneller zu entwickeln
- Nachteil: Ältere UI-Primitive

### Option C: WinUI 3 (Windows App SDK)
- **Vorteile:** Modernste Windows-UI, Compose-ähnliche Deklarationen
- Nachteil: Noch in aktiver Entwicklung, Setup aufwändiger

### Empfohlen: **WPF + CommunityToolkit.Mvvm**
- Passt gut zum ViewModel-Pattern der Android-App
- `ObservableObject` statt `StateFlow`
- `BitmapImage`/`WriteableBitmap` für Bilder
- `HttpClient` für API
- `System.Text.Json` statt `org.json`

---

## 16. Offene Fragen

1. **SSE-Stream:** Die Android-App verwendet in dieser Edit-Image-Ansicht
   **keinen** SSE-Stream (die Analyse zeigt `POST /api/v1/agnes/chat/stream`
   als vorhanden, aber `EditImageViewModel.submit()` macht **direkte**
   `chat()` + `generateImage()` Calls). Soll die Windows-Variante den
   direkten Weg oder den SSE-Weg gehen? → **Direkter Weg wie Android-App.**

2. **Mehrere Bilder:** Die App erlaubt bis zu 10 Bilder (`PickMultipleVisualMedia(10)`).
   Der `generateEdit()` baut dann einen längeren Identity-Clause. Soll die
   Windows-Variante auch Multi-Image-Support haben? → **Ja, 1:1.**

3. **Try-On Pill:** Die "Try-On" Pill ist deaktiviert (`onClick = null`).
   Soll sie in Windows ignoriert oder als Platzhalter bleiben? → **Ignorieren.**

4. **Sprache:** Die Android-App zeigt Deutsche UI-Texte ("Edit Image",
   "Das dauert etwa...", "Bild auswählen"). Soll die Windows-Variante auch
   Deutsch sein? → **Ja.**

5. **Packaging:** Soll die Windows-Variante als Installer (MSI/Winget),
   portable EXE, oder einfach als gebaute Binary bereitgestellt werden?

---

## 17. Implementierungs-Reihenfolge (Empfehlung)

1. **Projekt-Setup:** WPF-App, Verzeichnisstruktur anlegen
2. **Models kopieren:** Alle Kotlin-Datenklassen nach C# portieren
3. **Skills übernehmen:** Die 3 `Skills.*` Strings exakt einfügen
4. **AgnesApi portieren:** HTTP-Client mit beiden Endpunkten
5. **Pipeline portieren:** `analyzeAndEnhance`, `generateEdit`, `pickRatio`
6. **ViewModel portieren:** State-Management (INotifyPropertyChanged /
   ObservableObject), `submit()`-Workflow
7. **UI bauen:** Chat-Liste, Input-Bar, Settings-Dialog, ResultImage
8. **Bildverarbeitung:** ImageSharp für Decode/Dimensions/Save
9. **Theme anwenden:** Dunkles Theme mit den exakten Farbwerten
10. **Tests:** Gegen `agnes-image-service` testen (oder direkt gegen Gateway)
