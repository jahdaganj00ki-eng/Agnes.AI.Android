# Agnes Image Service (Referenzimplementierung)

Node.js-Nachbau des **Edit-Image**-Workflows der Agnes-App, umgestellt auf die
Modelle aus [`AgnesAI-Labs/AgnesAI-Models`](https://github.com/AgnesAI-Labs/AgnesAI-Models)
(OpenAI-kompatibles Gateway `https://apihub.agnes-ai.com/v1`).

## Workflow (entspricht der App)

1. Bild hochladen (Data-URI oder öffentliche URL)
2. Prompt eingeben (z. B. „Change color of shorts to black“)
3. Skill „Edit Image“ (System-Prompt, der nur die gewünschte Änderung erlaubt)
4. Bildanalyse + Prompt-Enhancement mit `agnes-2.5-flash` (Vision)
5. Bildbearbeitung (image-to-image) mit `agnes-image-2.1-flash`
6. Ergebnis als Data-URI (anzeigen + herunterladen)

## Voraussetzungen

- Node.js ≥ 18 (keine externen Abhängigkeiten)
- Umgebungsvariable `AGNES_API_KEY` (Agnes-AI-Gateway-Key)

## Start

```bash
cd agnes-image-service
export AGNES_API_KEY=sk-...   # oder über die Freebuff-Keys-UI setzen
npm start                      # läuft auf 0.0.0.0:8080 (PORT überschreibbar)
```

Danach:

- **Testseite:** http://localhost:8080/ (Upload → Prompt → Edit → Download)
- **JSON-API:** `POST /api/edit-image`

```bash
curl -s http://localhost:8080/api/edit-image \
  -H "Content-Type: application/json" \
  -d '{"image":"data:image/png;base64,….","prompt":"Change color of shorts to black"}'
```

Antwort:

```json
{
  "ok": true,
  "image": "data:image/png;base64,…",
  "analysis": "…",
  "edit_prompt": "…",
  "preserve": "…",
  "revised_prompt": "…",
  "size": "2K",
  "ratio": "2:3",
  "input_dimensions": { "width": 600, "height": 900 }
}
```

**Seitenverhältnis:** Der Service erkennt die Maße des Eingabebilds (PNG/JPEG/
GIF/WebP, auch bei URLs) und reicht das passende `ratio` an das Modell durch, damit
Hochformat-Fotos nicht verzerrt werden. Optional im Request überschreibbar:
`"size": "1K"|2K|3K|4K`, `"ratio": "1:1"|3:4|2:3|9:16|4:3|3:2|16:9|21:9`.

- **SSE-Kompatibilität:** `POST /api/v1/agnes/chat/stream` (Best-Effort-Nachbau
  des App-Vertrags; führt für `agent_type: "design"` denselben Workflow aus).

## End-to-End-Test

```bash
export AGNES_API_KEY=sk-...
npm test                 # generiert ein Mini-Testbild und bearbeitet es
npm test -- photo.jpg "Change the top to a sleeveless black crop top"
```

Ergebnis: `test/output.png`.

## Hinweis zum Austausch

Das APK ruft das eigene Agnes-Backend (`https://api.agnes-ai.com`) auf; die
Basis-URL ist fest einkompiliert. Ein Umbiegen auf diesen Service erfordert
entweder einen APK-Rebuild mit angepasster Base-URL oder einen Proxy, der
`api.agnes-ai.com` ersetzt. Der Kern des Austauschs ist die Pipeline in
`src/pipeline.js` – sie lässt sich 1:1 in ein bestehendes Backend übernehmen.
