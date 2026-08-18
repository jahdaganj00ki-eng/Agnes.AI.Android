// Minimal OpenAI-compatible client for the Agnes AI gateway.
// Base URL: https://apihub.agnes-ai.com/v1
// Auth:    Authorization: Bearer <AGNES_API_KEY>

const DEFAULT_BASE_URL = "https://apihub.agnes-ai.com/v1";

export class AgnesClient {
  constructor({ apiKey, baseUrl = DEFAULT_BASE_URL, fetchImpl = fetch } = {}) {
    if (!apiKey) {
      throw new Error(
        "AGNES_API_KEY is required. Set it via the environment or pass it explicitly."
      );
    }
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.replace(/\/+$/, "");
    this.fetchImpl = fetchImpl;
  }

  /**
   * Chat completion. `messages` is an OpenAI-style array.
   * Supports vision input via `{ type: "image_url", image_url: { url } }`.
   */
  async chatCompletion(model, messages, { maxTokens, temperature, json } = {}) {
    const body = {
      model,
      messages,
      ...(maxTokens != null ? { max_tokens: maxTokens } : {}),
      ...(temperature != null ? { temperature } : {}),
      ...(json ? { response_format: { type: "json_object" } } : {}),
    };

    const res = await this.fetchImpl(`${this.baseUrl}/chat/completions`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`Agnes chat error ${res.status}: ${text.slice(0, 2000)}`);
    }

    const data = await res.json();
    return data.choices?.[0]?.message?.content ?? "";
  }

  /**
   * Image generation / editing (text-to-image and image-to-image).
   * `images` is an optional array of public URLs or data URIs.
   */
  async imageGeneration(
    model,
    prompt,
    { size = "1K", ratio = "1:1", images = [], responseFormat = "b64_json" } = {}
  ) {
    const extraBody = {};
    if (images.length > 0) extraBody.image = images;
    extraBody.response_format = responseFormat;

    const body = {
      model,
      prompt,
      size,
      ratio,
      extra_body: extraBody,
    };

    const res = await this.fetchImpl(`${this.baseUrl}/images/generations`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify(body),
    });

    if (!res.ok) {
      const text = await res.text().catch(() => "");
      throw new Error(`Agnes image error ${res.status}: ${text.slice(0, 2000)}`);
    }

    const data = await res.json();
    const item = data.data?.[0] ?? {};
    return {
      url: item.url ?? null,
      b64Json: item.b64_json ?? null,
      revisedPrompt: item.revised_prompt ?? null,
    };
  }
}
