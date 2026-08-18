import { createServer } from "node:http";
import { AgnesClient } from "./agnesClient.js";
import { editImage } from "./pipeline.js";

const PORT = Number(process.env.PORT || 8080);
const HOST = "0.0.0.0";
const MAX_BODY = 30 * 1024 * 1024; // 30 MB, enough for data-URI images

function getClient() {
  return new AgnesClient({ apiKey: process.env.AGNES_API_KEY });
}

function json(res, status, payload) {
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(payload));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(new Error("request body too large"));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    req.on("error", reject);
  });
}

function sse(res) {
  res.writeHead(200, {
    "Content-Type": "text/event-stream; charset=utf-8",
    "Cache-Control": "no-cache",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });
}

function emit(res, event) {
  res.write(`data: ${JSON.stringify(event)}\n\n`);
}

function uid(prefix) {
  return `${prefix}_${Date.now()}_${Math.floor(Math.random() * 1e6)}`;
}

async function handleEditImage(req, res) {
  let body;
  try {
    body = JSON.parse(await readBody(req));
  } catch (e) {
    return json(res, 400, { error: `Invalid JSON: ${e.message}` });
  }

  const { image, prompt, mime_type: mimeType, size, ratio } = body;
  if (!image || !prompt) {
    return json(res, 400, { error: "Both `image` and `prompt` are required." });
  }

  try {
    const result = await editImage({
      image,
      prompt,
      mimeType,
      size,
      ratio,
      client: getClient(),
    });
    return json(res, 200, {
      ok: true,
      image: result.image, // data URI, ready for <img> and download
      analysis: result.analysis,
      edit_prompt: result.editPrompt,
      preserve: result.preserve,
      revised_prompt: result.revisedPrompt,
      size: result.size,
      ratio: result.ratio,
      input_dimensions: result.inputDimensions,
    });
  } catch (e) {
    return json(res, 502, { error: e.message });
  }
}

// Best-effort compatibility with the app's `POST /api/v1/agnes/chat/stream`
// contract. Runs the same Edit Image pipeline for `agent_type: "design"`.
async function handleChatStream(req, res) {
  let body;
  try {
    body = JSON.parse(await readBody(req));
  } catch {
    return json(res, 400, { error: "Invalid JSON" });
  }

  const { agent_type: agentType = "super", query = "", files = [], conversation_id: conversationId } = body;

  sse(res);

  const ts = () => Date.now();
  const msgId = uid("msg");
  const traceId = uid("trace");
  const toolId = uid("tool");

  emit(res, {
    message_id: msgId,
    type: "start_of_agent",
    data: { agent_type: agentType },
    ts: ts(),
    event_id: traceId,
  });

  emit(res, {
    message_id: msgId,
    type: "AgentStart",
    data: { content: "" },
    ts: ts(),
    event_id: traceId,
  });

  // Only the image/design flow is implemented in this reference.
  const fileUrl =
    (files[0] && (files[0].url || files[0].image)) || null;

  if (agentType === "design" && fileUrl && query) {
    emit(res, {
      message_id: msgId,
      type: "Message",
      data: { content: "Loading skill…" },
      ts: ts(),
      event_id: traceId,
    });

    emit(res, {
      message_id: msgId,
      type: "tool_call",
      tool_call_id: toolId,
      data: {
        worker_id: "design",
        description: "Load the Edit Image skill",
        tool_call_id: toolId,
        tool_name: "load_skill",
        tool_input: { name: "Edit Image" },
        tool_result: { skill_name: "Edit Image" },
        summary: "Skill loaded",
        message_id: msgId,
      },
      ts: ts(),
      event_id: traceId,
    });

    try {
      const result = await editImage({
        image: fileUrl,
        prompt: query,
        client: getClient(),
      });

      emit(res, {
        message_id: msgId,
        type: "Message",
        data: { content: `Analysis: ${result.analysis}` },
        ts: ts(),
        event_id: traceId,
      });

      emit(res, {
        message_id: msgId,
        type: "tool_call",
        tool_call_id: toolId,
        data: {
          worker_id: "design",
          description: "Edit the image",
          tool_call_id: toolId,
          tool_name: "generate_image",
          tool_input: { prompt: result.editPrompt },
          tool_result: { image: result.image },
          summary: "Image edited",
          message_id: msgId,
        },
        ts: ts(),
        event_id: traceId,
      });

      emit(res, {
        message_id: msgId,
        type: "artifact",
        data: { kind: "image", image: result.image, mime_type: "image/png" },
        ts: ts(),
        event_id: traceId,
      });
    } catch (e) {
      emit(res, {
        message_id: msgId,
        type: "AgentError",
        data: { message: e.message },
        ts: ts(),
        event_id: traceId,
      });
    }
  } else {
    emit(res, {
      message_id: msgId,
      type: "Message",
      data: {
        content:
          "This reference service only implements the image editing ('design') flow.",
      },
      ts: ts(),
      event_id: traceId,
    });
  }

  emit(res, {
    message_id: msgId,
    type: "AgentEnd",
    data: { content: "" },
    ts: ts(),
    event_id: traceId,
  });

  emit(res, {
    message_id: msgId,
    type: "final_session_state",
    data: { status: "succeeded" },
    ts: ts(),
    event_id: traceId,
  });

  emit(res, {
    app_id: "agnes",
    status: "succeeded",
    finished_at: new Date().toISOString(),
    conversation_id: conversationId || uid("conv"),
    rpc: "ChatStream",
    request_id: traceId,
    event: "agent.turn.completed",
    agent_type: agentType,
    type: "agent_turn_completed",
  });

  res.end();
}

const PAGE = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Agnes Edit Image — reference</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 760px; margin: 40px auto; padding: 0 16px; color: #111; }
  h1 { font-size: 22px; }
  textarea, input { width: 100%; box-sizing: border-box; margin: 8px 0; font: inherit; }
  textarea { height: 80px; }
  button { font: inherit; padding: 8px 16px; }
  img { max-width: 100%; border-radius: 8px; margin-top: 12px; border: 1px solid #ddd; }
  pre { background: #f5f5f5; padding: 12px; border-radius: 8px; white-space: pre-wrap; }
  .grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  @media (max-width: 600px) { .grid { grid-template-columns: 1fr; } }
</style>
</head>
<body>
<h1>Agnes Edit Image — reference (agnes-image-2.1-flash)</h1>
<p>Upload an image, describe the edit, and only the requested change is applied.</p>
<input id="file" type="file" accept="image/*" />
<textarea id="prompt" placeholder="e.g. Change color of shorts to black"></textarea>
<button id="run">Edit image</button>
<div id="status"></div>
<div class="grid">
  <div><h3>Original</h3><img id="src" hidden /></div>
  <div><h3>Edited</h3><img id="out" hidden /><a id="dl" hidden download="edited.png">Download</a></div>
</div>
<pre id="meta" hidden></pre>
<script>
  const run = document.getElementById("run");
  run.onclick = async () => {
    const file = document.getElementById("file").files[0];
    const prompt = document.getElementById("prompt").value;
    if (!file || !prompt) { alert("Choose an image and enter a prompt"); return; }
    const status = document.getElementById("status");
    status.textContent = "Working… (analysis + edit can take a while)";
    const dataUri = await new Promise((res) => {
      const r = new FileReader(); r.onload = () => res(r.result); r.readAsDataURL(file);
    });
    document.getElementById("src").src = dataUri;
    document.getElementById("src").hidden = false;
    const resp = await fetch("/api/edit-image", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ image: dataUri, prompt, mime_type: file.type })
    });
    const data = await resp.json();
    if (!resp.ok) { status.textContent = "Error: " + (data.error || resp.status); return; }
    status.textContent = "";
    document.getElementById("out").src = data.image;
    document.getElementById("out").hidden = false;
    document.getElementById("dl").href = data.image;
    document.getElementById("dl").hidden = false;
    document.getElementById("meta").textContent = JSON.stringify(
      { analysis: data.analysis, edit_prompt: data.edit_prompt, preserve: data.preserve, revised_prompt: data.revised_prompt },
      null, 2);
    document.getElementById("meta").hidden = false;
  };
</script>
</body>
</html>`;

async function handleRoot(req, res) {
  res.writeHead(200, { "Content-Type": "text/html; charset=utf-8" });
  res.end(PAGE);
}

const server = createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || "localhost"}`);
    if (req.method === "GET" && (url.pathname === "/" || url.pathname === "/index.html")) {
      return await handleRoot(req, res);
    }
    if (req.method === "POST" && url.pathname === "/api/edit-image") {
      return await handleEditImage(req, res);
    }
    if (req.method === "POST" && url.pathname === "/api/v1/agnes/chat/stream") {
      return await handleChatStream(req, res);
    }
    if (req.method === "GET" && url.pathname === "/health") {
      return json(res, 200, { ok: true, hasKey: Boolean(process.env.AGNES_API_KEY) });
    }
    return json(res, 404, { error: "Not found" });
  } catch (e) {
    return json(res, 500, { error: e.message });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`Agnes image service listening on http://${HOST}:${PORT}`);
  if (!process.env.AGNES_API_KEY) {
    console.warn("WARNING: AGNES_API_KEY is not set. Set it before calling the API.");
  }
});
