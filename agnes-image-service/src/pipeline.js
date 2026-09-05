// Reference implementation of the Agnes "Edit Image" workflow, mapped to the
// Agnes AI gateway models:
//
//   Step 3 (skill)      -> built-in "design" skill below
//   Step 4 (analysis +
//         enhancement)  -> agnes-2.5-flash  (vision, /v1/chat/completions)
//   Step 5 (edit)       -> agnes-image-2.1-flash (image-to-image,
//                          /v1/images/generations)
//   Step 6 (result)     -> data URI returned to the caller for download

const ANALYSIS_MODEL = "agnes-2.5-flash";
const EDIT_MODEL = "agnes-image-2.1-flash";

// Aspect ratios supported by agnes-image-2.1-flash (`ratio` parameter).
const SUPPORTED_RATIOS = [
  { name: "1:1", value: 1 },
  { name: "3:4", value: 3 / 4 },
  { name: "2:3", value: 2 / 3 },
  { name: "9:16", value: 9 / 16 },
  { name: "4:3", value: 4 / 3 },
  { name: "3:2", value: 3 / 2 },
  { name: "16:9", value: 16 / 9 },
  { name: "21:9", value: 21 / 9 },
];

// This is the "skill" that the app would load server-side for the `design`
// agent's EditImage mode. It exists only to enforce the core behaviour:
// change ONLY what the user asked for, keep everything else untouched.
const EDIT_IMAGE_SKILL = `You are the "Edit Image" skill of an image editing assistant.
Your job is to edit a photo according to a user instruction while changing AS
LITTLE AS POSSIBLE. Only the elements the user explicitly mentions may change.
Everything else (person identity, face, pose, body proportions, other clothing,
background, lighting, camera angle, composition, aspect ratio) must remain
exactly as it is. Never stretch, squash, widen, or narrow the subject.

The user may write in English or German. Always respond in English JSON.

POSE AND CAMERA RULES (apply to every rewrite, no exceptions):
- The person must keep the same camera angle and framing as the reference
  image(s). Never rotate the subject to a full side profile.
- Keep the body turned only slightly to one side: about 25 to 35 degrees from
  the camera. A full frontal pose is allowed only if the original is frontal.
- Weight shift: let the weight rest softly on one leg with a natural, moderate
  hip accent. No exaggerated hip thrust, no pronounced contrapposto, no
  theatrical pose. A confident, elegant stance is fine.
- Arms stay relaxed: at most ONE arm gesture (one hand near the hip, or lightly
  touching the hair). Never use both arms at once — two simultaneous gestures
  force the shoulders to twist and the body turns sideways.
- Keep the face and gaze toward the camera with a confident, inviting
  expression. A subtle smile is fine.

Analyse the image first (subject, clothing, pose, background, lighting), then
rewrite the user's instruction into ONE precise, self-contained English edit
prompt for an image-to-image model. If the request is ambiguous, choose the
most natural interpretation.

Return STRICT JSON only, no markdown, with exactly these keys:
{
  "analysis": "short description of what is visible in the image",
  "edit_prompt": "the precise English edit instruction",
  "preserve": "explicit list of everything that must stay unchanged, including aspect ratio and body proportions"
}`;

function toDataUri(mimeType, b64) {
  if (!b64) return null;
  if (b64.startsWith("data:")) return b64;
  return `data:${mimeType || "image/png"};base64,${b64}`;
}

/** Parse JSON that may be wrapped in markdown fences or prose. */
function parseJsonObject(text) {
  if (!text) throw new Error("Empty analysis response");
  const trimmed = text.trim();
  try {
    return JSON.parse(trimmed);
  } catch {
    const fence = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
    if (fence) {
      try {
        return JSON.parse(fence[1].trim());
      } catch {
        /* fall through */
      }
    }
    const first = trimmed.indexOf("{");
    const last = trimmed.lastIndexOf("}");
    if (first >= 0 && last > first) {
      try {
        return JSON.parse(trimmed.slice(first, last + 1));
      } catch {
        /* fall through */
      }
    }
    throw new Error(`Could not parse JSON from model output: ${trimmed.slice(0, 400)}`);
  }
}

/** Read width/height from the header of a PNG/JPEG/GIF/WebP buffer. */
export function readDimensions(buf) {
  if (!buf || buf.length < 24) return null;

  // PNG: 8-byte signature, then IHDR (width @16, height @20, big-endian).
  if (
    buf[0] === 0x89 && buf[1] === 0x50 && buf[2] === 0x4e && buf[3] === 0x47 &&
    buf.toString("ascii", 12, 16) === "IHDR"
  ) {
    return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
  }

  // JPEG: scan markers for a SOF segment (height @i+5, width @i+7).
  if (buf[0] === 0xff && buf[1] === 0xd8) {
    let i = 2;
    while (i + 9 < buf.length) {
      if (buf[i] !== 0xff) {
        i++;
        continue;
      }
      const marker = buf[i + 1];
      if (
        marker >= 0xc0 && marker <= 0xcf &&
        marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc
      ) {
        return { height: buf.readUInt16BE(i + 5), width: buf.readUInt16BE(i + 7) };
      }
      const segLen = buf.readUInt16BE(i + 2);
      if (!(segLen >= 2)) break;
      i += 2 + segLen;
    }
    return null;
  }

  // GIF: width @6, height @8 (little-endian).
  if (buf.toString("ascii", 0, 3) === "GIF") {
    return { width: buf.readUInt16LE(6), height: buf.readUInt16LE(8) };
  }

  // WebP (RIFF container): VP8X, VP8, or VP8L chunks.
  if (buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP") {
    const fourCC = buf.toString("ascii", 12, 16);
    if (fourCC === "VP8X" && buf.length >= 30) {
      const width = 1 + (buf[24] | (buf[25] << 8) | (buf[26] << 16));
      const height = 1 + (buf[27] | (buf[28] << 8) | (buf[29] << 16));
      return { width, height };
    }
    if (fourCC === "VP8 " && buf.length >= 30) {
      return {
        width: buf.readUInt16LE(26) & 0x3fff,
        height: buf.readUInt16LE(28) & 0x3fff,
      };
    }
    if (fourCC === "VP8L" && buf.length >= 25) {
      const bits = buf.readUInt32LE(21);
      return { width: (bits & 0x3fff) + 1, height: ((bits >> 14) & 0x3fff) + 1 };
    }
  }

  return null;
}

/** Fetch only the first `max` bytes of a URL (enough to read image headers). */
async function fetchHeaderBytes(url, max = 131072) {
  const res = await fetch(url, { headers: { Range: `bytes=0-${max - 1}` } });
  if (!res.ok || !res.body) return null;
  const reader = res.body.getReader();
  const chunks = [];
  let total = 0;
  try {
    while (total < max) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      total += value.length;
    }
  } finally {
    try {
      reader.cancel();
    } catch {
      /* ignore */
    }
  }
  return Buffer.concat(chunks).subarray(0, max);
}

/** Detect the pixel dimensions of a data URI, raw base64, or public URL. */
export async function detectImageDimensions(image, mimeType) {
  if (image.startsWith("data:")) {
    const b64 = image.slice(image.indexOf(",") + 1);
    try {
      return readDimensions(Buffer.from(b64, "base64"));
    } catch {
      return null;
    }
  }
  if (/^https?:\/\//i.test(image)) {
    const head = await fetchHeaderBytes(image);
    return head ? readDimensions(head) : null;
  }
  // Raw base64.
  try {
    return readDimensions(Buffer.from(image, "base64"));
  } catch {
    return null;
  }
}

/** Pick the closest supported `ratio` string for given pixel dimensions. */
export function pickRatio(width, height) {
  if (!width || !height) return "3:4"; // portrait default for person photos
  const aspect = width / height;
  let best = SUPPORTED_RATIOS[0];
  let bestDiff = Infinity;
  for (const r of SUPPORTED_RATIOS) {
    const diff = Math.abs(Math.log(aspect) - Math.log(r.value));
    if (diff < bestDiff) {
      bestDiff = diff;
      best = r;
    }
  }
  return best.name;
}

/**
 * Full Edit Image pipeline.
 *
 * @param {object} opts
 * @param {string} opts.image  data URI, base64, or public URL of the input image
 * @param {string} opts.prompt the user's edit instruction
 * @param {string} [opts.mimeType] image mime type (defaults to image/png)
 * @param {string} [opts.size] output size tier (1K/2K/3K/4K, default 2K)
 * @param {string} [opts.ratio] override detected aspect ratio (e.g. "3:4")
 * @param {AgnesClient} opts.client
 */
export async function editImage({ image, prompt, mimeType, size = "2K", ratio, client }) {
  if (!image || !prompt) {
    throw new Error("Both `image` and `prompt` are required.");
  }
  if (!/^https?:\/\//i.test(image) && !image.startsWith("data:")) {
    // Assume raw base64.
    image = toDataUri(mimeType, image);
  }

  const inputUri = image.startsWith("data:") ? image : image;
  const inputUrl = image.startsWith("data:") ? null : image;

  // Preserve the input's aspect ratio so portrait photos are not stretched.
  const dimensions = await detectImageDimensions(image, mimeType);
  const targetRatio = ratio || (dimensions ? pickRatio(dimensions.width, dimensions.height) : "3:4");

  // ---- Step 4: analyse + enhance the prompt (vision model) ----
  const analysisContent = [
    { type: "text", text: `User edit instruction:\n"""\n${prompt}\n"""` },
    {
      type: "image_url",
      image_url: { url: inputUri },
    },
  ];

  const raw = await client.chatCompletion(
    ANALYSIS_MODEL,
    [
      { role: "system", content: EDIT_IMAGE_SKILL },
      { role: "user", content: analysisContent },
    ],
    { maxTokens: 1200, json: true }
  );

  const parsed = parseJsonObject(raw);
  const analysis = parsed.analysis ?? "";
  const editPrompt = parsed.edit_prompt ?? prompt;
  const preserve = parsed.preserve ?? "";

  // Reinforce "only change what was requested" and "never distort".
  const finalPrompt = `${editPrompt}\n\nPreserve everything that is not explicitly mentioned in this instruction${preserve ? ` (in particular: ${preserve})` : ""}. Do not change the person's identity, face, pose, body proportions, other clothing, background, lighting, or composition unless the instruction explicitly asks for it. Keep the original aspect ratio and proportions exactly — do not stretch, squash, widen, or narrow the subject or the background.`;

  // ---- Step 5: edit the image (image-to-image) ----
  const result = await client.imageGeneration(EDIT_MODEL, finalPrompt, {
    images: [inputUrl ?? inputUri],
    responseFormat: "b64_json",
    size,
    ratio: targetRatio,
  });

  const outputUri = result.b64Json
    ? toDataUri("image/png", result.b64Json)
    : result.url;

  // ---- Step 6: return the result ----
  return {
    image: outputUri,
    analysis,
    editPrompt,
    preserve,
    revisedPrompt: result.revisedPrompt,
    size,
    ratio: targetRatio,
    inputDimensions: dimensions,
  };
}
