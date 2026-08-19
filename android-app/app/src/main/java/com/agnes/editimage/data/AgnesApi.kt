package com.agnes.editimage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class GeneratedImage(val b64: String?, val url: String?, val revisedPrompt: String?)

/**
 * Thin OpenAI-compatible client for the Agnes AI gateway
 * (https://apihub.agnes-ai.com/v1).
 */
class AgnesApi(
    private val apiKey: String,
    baseUrl: String,
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val base = baseUrl.trimEnd('/')

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private suspend fun post(path: String, body: JSONObject): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(base + path)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw Exception("Agnes API ${resp.code}: ${text.take(1000)}")
            }
            text
        }
    }

    /**
     * Chat completion returning the assistant text. `userContent` is a JSONArray
     * of content parts (text and/or image_url) as used by OpenAI-compatible APIs.
     */
    suspend fun chat(
        model: String,
        systemPrompt: String,
        userContent: JSONArray,
        maxTokens: Int = 1200,
        jsonMode: Boolean = true,
    ): String {
        val body = JSONObject()
            .put("model", model)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userContent))
            )
            .put("max_tokens", maxTokens)
        if (jsonMode) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
        val text = post("/chat/completions", body)
        val root = JSONObject(text)
        return root.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
    }

    /**
     * Image generation / editing (text-to-image and image-to-image).
     */
    suspend fun generateImage(
        model: String,
        prompt: String,
        size: String,
        ratio: String,
        imageDataUris: List<String>,
        responseFormat: String = "b64_json",
    ): GeneratedImage {
        val extra = JSONObject().put("response_format", responseFormat)
        if (imageDataUris.isNotEmpty()) {
            val images = JSONArray()
            imageDataUris.forEach { images.put(it) }
            extra.put("image", images)
        }
        val body = JSONObject()
            .put("model", model)
            .put("prompt", prompt)
            .put("size", size)
            .put("ratio", ratio)
            .put("extra_body", extra)

        val text = post("/images/generations", body)
        val item = JSONObject(text).getJSONArray("data").getJSONObject(0)
        return GeneratedImage(
            b64 = item.optString("b64_json").ifBlank { null },
            url = item.optString("url").ifBlank { null },
            revisedPrompt = item.optString("revised_prompt").ifBlank { null },
        )
    }
}
