// End-to-end test for the Edit Image pipeline.
//
// Usage:
//   AGNES_API_KEY=sk-... node test/edit-image.test.js [input.png] ["edit prompt"]
//
// Without arguments it generates a tiny in-memory test image and edits it.

import { existsSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import zlib from "node:zlib";
import { AgnesClient } from "../src/agnesClient.js";
import { editImage, readDimensions } from "../src/pipeline.js";

const __dirname = dirname(fileURLToPath(import.meta.url));

// --- minimal PNG encoder (RGB, no dependencies) ---------------------------
function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}

function chunk(type, data) {
  const out = Buffer.alloc(8 + data.length + 4);
  out.writeUInt32BE(data.length, 0);
  out.write(type, 4, "ascii");
  data.copy(out, 8);
  out.writeUInt32BE(crc32(Buffer.concat([Buffer.from(type, "ascii"), data])), 8 + data.length);
  return out;
}

function makeTestPng() {
  // Portrait image (2:3) with a vertical red rectangle as a standing figure.
  const W = 600;
  const H = 900;
  const raw = Buffer.alloc(H * (1 + W * 3));
  let o = 0;
  for (let y = 0; y < H; y++) {
    raw[o++] = 0; // filter: none
    for (let x = 0; x < W; x++) {
      const inFigure = x >= W * 0.35 && x <= W * 0.65 && y >= H * 0.2 && y <= H * 0.9;
      if (inFigure) {
        raw[o++] = 200; raw[o++] = 30; raw[o++] = 30; // red figure
      } else {
        raw[o++] = 255; raw[o++] = 255; raw[o++] = 255; // white
      }
    }
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(W, 0);
  ihdr.writeUInt32BE(H, 4);
  ihdr[8] = 8;  // bit depth
  ihdr[9] = 2;  // color type RGB
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

async function main() {
  const apiKey = process.env.AGNES_API_KEY;
  if (!apiKey) {
    console.error("Set AGNES_API_KEY to run this test.");
    process.exit(1);
  }

  const [, , argPath, argPrompt] = process.argv;
  let image = argPath && existsSync(argPath)
    ? "data:image/png;base64," + readFileSync(argPath).toString("base64")
    : "data:image/png;base64," + makeTestPng().toString("base64");

  const prompt = argPrompt || "Change the red figure to blue and keep the white background unchanged";

  console.log("Running Edit Image pipeline…");
  const client = new AgnesClient({ apiKey });
  const result = await editImage({ image, prompt, mimeType: "image/png", client });

  console.log("analysis:", result.analysis);
  console.log("edit_prompt:", result.editPrompt);
  console.log("preserve:", result.preserve);
  console.log("revised_prompt:", result.revisedPrompt);
  console.log("ratio:", result.ratio, "size:", result.size, "input_dimensions:", JSON.stringify(result.inputDimensions));

  if (result.image && result.image.startsWith("data:")) {
    const b64 = result.image.split(",")[1];
    const outDims = readDimensions(Buffer.from(b64, "base64"));
    console.log("output_dimensions:", JSON.stringify(outDims));
    const outPath = join(__dirname, "output.png");
    writeFileSync(outPath, Buffer.from(b64, "base64"));
    console.log("Wrote edited image to", outPath);
  } else {
    console.log("Result image URL:", result.image);
  }
  console.log("OK");
}

main().catch((e) => {
  console.error("FAILED:", e.message);
  process.exit(1);
});
