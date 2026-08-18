package com.agnes.editimage.data

import org.json.JSONArray
import org.json.JSONObject

const val ANALYSIS_MODEL = "agnes-2.5-flash"
const val EDIT_MODEL = "agnes-image-2.1-flash"

/**
 * The three "skills" shown in the UI. Their content is also what drives the
 * actual model calls, so the displayed "Loaded N Chars" matches reality.
 */
data class SkillDef(val badge: String, val content: String)

object Skills {
    const val IMAGE_GENERATION = """You are the "image-generation" skill of an image editing assistant.
Edit the photo exactly according to the provided instruction and change AS LITTLE
AS POSSIBLE. Only the elements the user explicitly mentions may change.
Never stretch, squash, widen, or narrow the subject or the background.
Preserve the original aspect ratio, body proportions, identity, face, pose,
clothing that is not mentioned, background, lighting, and composition."""

    const val REFERENCE_IMAGE = """You are the "reference-image" skill.
The input image is the single source of truth (reference). The output must keep
the same person identity, same body shape and proportions, same camera angle and
same framing as the reference. Only apply the requested edit on top of it.
Do not invent new elements, do not crop, and do not change the aspect ratio."""

    const val PROMPT_CRAFT = """You are the "image-prompt-craft" skill.
Analyse the attached image and the user's edit instruction, then rewrite the
instruction into ONE precise, self-contained English edit prompt for an
image-to-image model. The user may write in English or German.

Also write a short German confirmation sentence telling the user what will be
changed (use informal "du", keep it to one sentence).

Return STRICT JSON only, no markdown, with exactly these keys:
{
  "analysis": "short description of what is visible in the image",
  "edit_prompt": "the precise English edit instruction",
  "preserve": "explicit list of everything that must stay unchanged, including aspect ratio and body proportions",
  "reply_de": "short German confirmation sentence"
}"""
}

val SKILLS: List<SkillDef> = listOf(
    SkillDef("image-generation", Skills.IMAGE_GENERATION),
    SkillDef("image-generation / reference-image", Skills.REFERENCE_IMAGE),
    SkillDef("image-prompt-craft", Skills.PROMPT_CRAFT),
)

data class Analysis(
    val analysis: String,
    val editPrompt: String,
    val preserve: String,
    val replyDe: String,
)

private fun extractJson(text: String): JSONObject {
    val trimmed = text.trim()
    return try {
        JSONObject(trimmed)
    } catch (_: Exception) {
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(trimmed)
        if (fence != null) {
            JSONObject(fence.groupValues[1].trim())
        } else {
            val first = trimmed.indexOf('{')
            val last = trimmed.lastIndexOf('}')
            if (first >= 0 && last > first) {
                JSONObject(trimmed.substring(first, last + 1))
            } else {
                throw Exception("Could not parse JSON from model output: ${trimmed.take(300)}")
            }
        }
    }
}

suspend fun analyzeAndEnhance(
    api: AgnesApi,
    imageDataUri: String,
    userPrompt: String,
): Analysis {
    val userContent = JSONArray()
        .put(JSONObject().put("type", "text").put("text", "User edit instruction:\n\"\"\"\n$userPrompt\n\"\"\""))
        .put(
            JSONObject()
                .put("type", "image_url")
                .put("image_url", JSONObject().put("url", imageDataUri))
        )

    val raw = api.chat(
        model = ANALYSIS_MODEL,
        systemPrompt = Skills.PROMPT_CRAFT,
        userContent = userContent,
        maxTokens = 1200,
        jsonMode = true,
    )
    val obj = extractJson(raw)
    return Analysis(
        analysis = obj.optString("analysis").ifBlank { "" },
        editPrompt = obj.optString("edit_prompt").ifBlank { userPrompt },
        preserve = obj.optString("preserve").ifBlank { "" },
        replyDe = obj.optString("reply_de").ifBlank { "Ich bearbeite das Bild entsprechend deiner Anweisung." },
    )
}

suspend fun generateEdit(
    api: AgnesApi,
    imageDataUri: String,
    analysis: Analysis,
    ratio: String,
    size: String,
): ByteArray {
    val preserveClause = if (analysis.preserve.isNotBlank()) {
        " (in particular: ${analysis.preserve})"
    } else {
        ""
    }
    val finalPrompt =
        "${analysis.editPrompt}\n\n" +
            "Preserve everything that is not explicitly mentioned in this instruction$preserveClause. " +
            "Do not change the person's identity, face, pose, body proportions, other clothing, background, " +
            "lighting, or composition unless the instruction explicitly asks for it. " +
            "Keep the original aspect ratio and proportions exactly — do not stretch, squash, widen, or narrow " +
            "the subject or the background.\n\n${Skills.IMAGE_GENERATION}\n\n${Skills.REFERENCE_IMAGE}"

    val result = api.generateImage(
        model = EDIT_MODEL,
        prompt = finalPrompt,
        size = size,
        ratio = ratio,
        imageDataUri = imageDataUri,
        responseFormat = "b64_json",
    )

    val b64 = result.b64 ?: throw Exception("Image API returned no image data")
    return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
}

/** Map pixel dimensions to the closest ratio supported by the image model. */
fun pickRatio(width: Int, height: Int): String {
    if (width <= 0 || height <= 0) return "3:4"
    val aspect = width.toDouble() / height.toDouble()
    val ratios = listOf(
        "1:1" to 1.0,
        "3:4" to 3.0 / 4.0,
        "2:3" to 2.0 / 3.0,
        "9:16" to 9.0 / 16.0,
        "4:3" to 4.0 / 3.0,
        "3:2" to 3.0 / 2.0,
        "16:9" to 16.0 / 9.0,
        "21:9" to 21.0 / 9.0,
    )
    var best = ratios[0]
    var bestDiff = Double.MAX_VALUE
    for (r in ratios) {
        val diff = kotlin.math.abs(kotlin.math.ln(aspect) - kotlin.math.ln(r.second))
        if (diff < bestDiff) {
            bestDiff = diff
            best = r
        }
    }
    return best.first
}
