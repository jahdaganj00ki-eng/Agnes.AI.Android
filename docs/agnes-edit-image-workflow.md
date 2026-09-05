# Agnes – Edit-Image-Workflow (Reverse Engineering)

Dieses Dokument beschreibt, was in der Android-App **Agnes** (Paket
`com.sobrr.agnes`) beim Bildbearbeiten tatsächlich passiert, und wie der
Bildbearbeitungs-Workflow auf die Modelle aus
[`AgnesAI-Labs/AgnesAI-Models`](https://github.com/AgnesAI-Labs/AgnesAI-Models)
umgestellt wird.

> Hinweis: Grundlage der Analyse ist das Split-APK `Agnes 3.0.33.apk+`
> (`versionCode 3000033`, `versionName 3.0.33`); erwähnt wird gelegentlich auch
> eine neuere Build-Nummer 3.0.47. Beide APKs sowie die sechs
> Referenz-Screenshots liegen **nicht mehr im Repo** – dieses Repo enthält keine
> Binärdateien (`*.apk` ist gitignored), die APKs entstehen stattdessen als
> Build-Artefakt. Die nachfolgend rekonstruierten Endpunkte, Skill-Namen und
> UI-Texte stammen aus jener Analyse und bleiben unverändert gültig.

---

## 1. Was die App ist

- **Name:** Agnes
- **Paket:** `com.sobrr.agnes`
- **minSdk:** 26 (Android 8.0)
- **Stack:** Kotlin, Jetpack Compose, Hilt/Dagger, Retrofit + OkHttp, Gson,
  Room, DataStore, Navigation, ExifInterface; Firebase (Analytics/Crashlytics/
  FCM), Tencent IM/ASR, Adjust, Singular.
- **App-Typ:** Ein KI-Assistent mit Chat-Oberfläche. Es gibt „Skills“
  (spezialisierte Agenten) für Web-Suche, Bild-Erstellen/Bearbeiten/Design,
  Video, Slides, Tabellen, Recherche, Schreiben, Photo-Q&A, Hausaufgaben und
  Mini-Spiele.
- Die eigentliche KI-Logik läuft **nicht** im APK, sondern auf dem
  Agnes-Backend. Das APK ist ein dünner Client, der Anfragen streamt und
  Ereignisse rendert.

## 2. Backend-URLs (im APK gefunden)

| Zweck | URL |
| --- | --- |
| Produktion | `https://api.agnes-ai.com` |
| Preview | `https://api-preview.agnes-ai.com/` |
| Web-App | `https://app.agnes-ai.com/` |
| Dev | `https://api-agnes-dev.kiwiar.com`, `https://agnes-dev.kiwiar.com/` |
| Test | `https://api-agnes-test.kiwiar.com`, `https://agnes-test-gcp.kiwiar.com/` |

Die Basis-URL ist fest einkompiliert (BuildConfig), d. h. ein Umbiegen auf ein
eigenes Backend erfordert einen Rebuild des APK.

## 3. Skill-Katalog (hart im APK verdrahtet)

Quelle: dekompilierte Klasse `J5.c` (Enum). Jeder Skill hat `(Name, agent_type,
Titel, Icon, Hinweistext, …)`:

| Skill | `agent_type` |
| --- | --- |
| `WebSearch` | `super` |
| `CreateSlides` | `slide` |
| `PhotoToVideo` | `design` |
| **`EditImage`** | **`design`** |
| `PhotoDesign` | `design` |
| `CreateImage` | `design` |
| `CreateVideo` | `design` |
| `Research` | `research` |
| `CreateSpreadsheet` | `sheet` |
| `Writing` | `super` |
| `PhotoQA` | `super` |
| `HomeworkCheck` | `super` |
| `MiniGame` | `super` |

**Bedeutung:** `EditImage`, `PhotoDesign`, `CreateImage`, `PhotoToVideo` und
`CreateVideo` laufen alle über denselben Backend-Agenten **`design`**. Der
Unterschied zwischen „Edit“, „Design“ und „Create“ steckt also im Backend-
Prompt/Skill, nicht in der App. Die App sendet nur `agent_type` + Prompt + Datei.

Für `EditImage` wird der Nutzer-Prompt **unverändert** als `query` gesendet
(die Methode `c.a()` stellt bei `EditImage` keinen Präfix voran, weil der
Button-Label-Parameter `null` ist). Beispiel: `"Change color of shorts to black"`.

## 4. Der Edit-Image-Workflow (6 Schritte, Client + Backend)

### Schritt 1 – Bild hochladen

1. `POST /api/v1/file/presigned-url`
   ```json
   { "purpose": "chat_attachment", "content_type": "image/jpeg", "filename": "photo.jpg" }
   ```
   Antwort:
   ```json
   { "upload_url": "…", "public_url": "…", "method": "PUT",
     "expires_in": 3600, "required_headers": { } }
   ```
2. Bytes per `PUT` an `upload_url` hochladen (GCS).
3. Optional `POST /api/file/process/chat` zum Verarbeiten der Datei.
4. Für große Dateien gibt es Multipart:
   - `POST /api/v1/file/multipart/init` `{ purpose, content_type, file_size, filename, conversation_id, part_size }`
   - `POST /api/v1/file/multipart/complete` `{ upload_id, key, parts }`
   - `POST /api/v1/file/multipart/abort` `{ upload_id, key }`

Die hochgeladene Datei wird intern als `{ mime_type, url, filename }`
repräsentiert (Quelle: `R7.d.b()` → `UploadFileModel`).

### Schritt 2 – Prompt eingeben

Der Nutzer tippt z. B. „Change color of shorts to black“, „Remove Smartphone“,
„Change top to a sleeveless black crop top“, „Mache Pose sinnlicher, nicht
seitlich stehen!!!“.

### Schritt 3 – Skill laden

- In der App wählt der Nutzer den Skill **Edit Image** (`EditImage`).
- Die App mappt `EditImage → agent_type "design"`.
- Der Backend-Agent `design` lädt serverseitig seinen Skill. Das wird über
  einen Tool-Call **`load_skill`** signalisiert. Der Client zeigt „Skill
  geladen“ mit dem Feld `skill_name` aus dem Tool-Ergebnis an.

**Wichtig:** Die eigentlichen Skill-Inhalte (System-Prompts) liegen auf dem
Backend und sind **nicht** im APK. Im APK stecken nur:
- der Skill-Katalog (`J5.c`),
- UI-Texte (`Edit Image`, `Describe the image you want to edit`, …),
- das `load_skill`-Tool-Call-Handling und die `skill_name`-Anzeige.

### Schritt 4 – Bildanalyse + Prompt-Enhancement

Backend-seitig (Agent `design`): ein Vision-Modell analysiert das Bild und
schreibt den Nutzer-Prompt in einen präzisen Bearbeitungs-Prompt um. Im
Chat-Stream erscheint das als Tool-Call-/„Thinking“-Ereignisse.

### Schritt 5 – Bildbearbeitung

Backend-seitig: Tool-Call **`generate_image`** mit dem optimierten Prompt und
dem Eingabebild (image-to-image). Nur die im Prompt beschriebene Änderung wird
vorgenommen; der Rest bleibt original (das ist die Kernanforderung der Nutzerin).

### Schritt 6 – Ergebnis anzeigen + Download

Das fertige Bild kommt als Artefakt/Medien-Ereignis zurück. Die App rendert es
und bietet Download/Share an.

## 5. Chat-Stream-API-Vertrag (vom APK verwendet)

`POST /api/v1/agnes/chat/stream` – SSE-Stream.

Request-Body (`ChatStreamRequestBody`):

```json
{
  "conversation_id": "…",
  "agent_type": "design",
  "timeout_seconds": 3000,
  "query": "Change color of shorts to black",
  "files": [ { "mime_type": "image/jpeg", "url": "https://…", "filename": "photo.jpg" } ],
  "extra_context": { },
  "news": []
}
```

Weitere Endpunkte (Auswahl):

| Endpoint | Zweck |
| --- | --- |
| `POST /api/v1/agnes/chat/stream/cancel` | Stream abbrechen |
| `POST /api/v1/agnes/chat/stream/resume` | Stream fortsetzen (`{conversation_id, from_seq}`) |
| `POST /api/v1/agnes/chat/stream/hitl-resume` | Human-in-the-loop fortsetzen |
| `POST /api/v1/agnes/conversation` | Konversation anlegen |
| `GET  /api/v1/agnes/conversation/history` | Verlauf |
| `GET  /api/v1/agnes/conversation/running` | laufende Konversation |
| `GET  /api/v1/agnes/agnes-chats` | Chat-Liste |
| `GET  /api/v1/agnes/recommend-topics` | Empfohlene Themen |
| `GET  /api/v1/agnes/follow-up-questions` | Folgefragen |
| `GET  /api/v1/agnes/profile/memory` | Profil-Speicher |

## 6. SSE-Ereignis-Protokoll (aus dem APK rekonstruiert)

Jedes Ereignis ist ein JSON-Objekt mit Feldern wie
`{ message_id, type, data, ts, event_id, trace_id }`.

Beobachtete `type`-Werte:

- Agent-Lebenszyklus: `start_of_agent`, `AgentStart`, `AgentEnd`,
  `final_session_state`, `agent_turn_completed` (Feld `event`:
  `agent.turn.completed`), `AgentError`, `AgentCancelled`.
- Inhalte: `Message` (`data.content`), `File`
  (`data.detected_mime_type`, `file_uid`, `filename`, `mime_type`, `url`,
  `local_preview_path`), `LikeShareButton`.
- Tool-Call-Worker (`WorkerModel`, Felder: `worker_id`, `description`,
  `tool_call_id`, `tool_name`, `tool_input`, `tool_result`, `summary`,
  `message_id`). `tool_result` ist eine Map; bei `load_skill` enthält sie
  `skill_name`.

## 7. Tool-Calls (vom Agenten verwendet)

Quelle: `ToolCallEnum`. Werte: `Other`, `query_weather`, `web_search`,
`image_search`, `generate_image`, `write_report`, `write_file`, `execute`,
`load_skill`, `profile_data`, `read_file`, `list_files`, `edit_file`.

Für den Bild-Workflow relevant: `load_skill` (Schritt 3) und `generate_image`
(Schritt 5).

## 8. Austausch gegen AgnesAI-Models

Die AgnesAI-Models-Repo ist das offizielle, OpenAI-kompatible Gateway:
`https://apihub.agnes-ai.com/v1`.

| App-Schritt | Alte Stelle (Backend `design`-Agent) | Neues Modell |
| --- | --- | --- |
| Analyse + Prompt-Enhancement | Vision-Modell des Backends | `agnes-2.5-flash` via `POST /v1/chat/completions` |
| Bildbearbeitung (image-to-image) | `generate_image`-Tool des Backends | `agnes-image-2.1-flash` via `POST /v1/images/generations` |

### Bildbearbeitung (image-to-image) mit `agnes-image-2.1-flash`

```http
POST https://apihub.agnes-ai.com/v1/images/generations
Authorization: Bearer <AGNES_API_KEY>
Content-Type: application/json
```

```json
{
  "model": "agnes-image-2.1-flash",
  "prompt": "…optimierter Bearbeitungs-Prompt…",
  "size": "1K",
  "ratio": "1:1",
  "extra_body": {
    "image": ["data:image/jpeg;base64,…" ],
    "response_format": "b64_json"
  }
}
```

Antwort: `data[0].url` oder `data[0].b64_json` (+ optional `revised_prompt`).

Wichtige Doku-Punkte (siehe `agnes-ai.com/doc/agnes-image-21-flash`):
- `response_format` gehört **in** `extra_body`, nicht auf Top-Level.
- Für image-to-image einfach `extra_body.image` (Array aus URL oder Data-URI)
  setzen – kein `tags: ["img2img"]` nötig.
- Für „nur die gewünschte Änderung, Rest unverändert“ im Prompt explizit die
  zu **erhaltenden** Elemente beschreiben („…preserving the original
  composition / person / clothing / background“).

### Analyse/Enhancement mit `agnes-2.5-flash`

```http
POST https://apihub.agnes-ai.com/v1/chat/completions
```

mit `messages` (Vision-Eingabe über `image_url` + Nutzer-Prompt) und einer
System-Anweisung, die als „Skill“ fungiert (siehe Referenzimplementierung).

## 9. Referenzimplementierung

Unter `agnes-image-service/` liegt eine kleine Node.js-Referenz, die den
kompletten 6-Schritte-Workflow mit den neuen Modellen nachbildet:

- `src/agnesClient.js` – schlanker Client für Chat-Completion + Image-Generation.
- `src/pipeline.js` – der Edit-Image-Workflow (Skill → Analyse/Enhancement →
  Bearbeitung → Ergebnis).
- `src/server.js` – HTTP-API:
  - `POST /api/edit-image` (JSON: `{ image, prompt }` → bearbeitetes Bild)
  - `POST /api/v1/agnes/chat/stream` (SSE, Best-Effort-Kompatibilität zum
    App-Vertrag; führt für `agent_type: "design"` denselben Workflow aus)
  - `GET /` (Mini-Testseite: Upload → Prompt → Edit → Download)

Benötigte Umgebungsvariable: `AGNES_API_KEY`.
