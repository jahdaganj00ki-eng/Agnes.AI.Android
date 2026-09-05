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
The input image(s) are the single source of truth (reference). The output must
keep the same person identity, same face, same body shape and proportions, same
camera angle and same framing as the references. Only apply the requested edit
on top of them. Do not invent new elements, do not crop, and do not change the
aspect ratio."""

    const val PROMPT_CRAFT = """You are the "image-prompt-craft" skill.
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

Also write a short German confirmation sentence telling the user what will be
changed (use informal "du", keep it to one sentence).

Return STRICT JSON only, no markdown, with exactly these keys:
{
  "analysis": "short description of what is visible in the image(s)",
  "edit_prompt": "the precise English edit instruction, explicitly preserving the person's identity, face, body proportions and clothing",
  "preserve": "explicit list of everything that must stay unchanged, including the person's identity, face, body proportions, clothing and aspect ratio",
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
    imageDataUris: List<String>,
    userPrompt: String,
): Analysis {
    val userContent = JSONArray()
        .put(JSONObject().put("type", "text").put("text", "User edit instruction:\n\"\"\"\n$userPrompt\n\"\"\""))
    for (uri in imageDataUris) {
        userContent.put(
            JSONObject()
                .put("type", "image_url")
                .put("image_url", JSONObject().put("url", uri))
        )
    }

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

private const val MAX_CONTENT_POLICY_RETRIES = 4

private fun isContentPolicyViolation(e: Throwable): Boolean =
    e.message?.contains("content_policy_violation", ignoreCase = true) == true

/**
 * Progressively soften a prompt the image model rejected as a content policy
 * violation. Each level asks for a slightly more conservative result so the
 * requested edit still happens while removing whatever triggered the filter.
 */
private fun softenPrompt(prompt: String, level: Int): String = when (level) {
    1 -> "$prompt\n\nKeep the result tasteful and modest; the subject should remain appropriately covered."
    2 -> "$prompt\n\nMake the result tasteful, modest, and fully clothed, with no revealing or suggestive elements."
    3 -> "$prompt\n\nRender a conservative, tasteful, family-friendly version. Keep the subject fully clothed and avoid any skin exposure beyond the face, hands, and neckline."
    else -> "Create a modest, tasteful, fully-clothed version of the requested edit, appropriate for all audiences. Original instruction: $prompt"
}

suspend fun generateEdit(
    api: AgnesApi,
    imageDataUris: List<String>,
    analysis: Analysis,
    ratio: String,
    size: String,
    mode: String,
): ByteArray {
    val preserveClause = if (analysis.preserve.isNotBlank()) {
        " (in particular: ${analysis.preserve})"
    } else {
        ""
    }
    val identityClause = if (imageDataUris.size > 1) {
        "The subject must look exactly like the person in the provided reference images: same face, " +
            "same identity, same hair, same body shape and proportions, and the same clothing. " +
            "Use all reference images together to reconstruct the person faithfully."
    } else {
        "The subject must look exactly like the person in the reference image: same face, same identity, " +
            "same body shape and proportions, and the same clothing."
    }
    val modeClause = when (mode) {
        "full_body" -> " Render the person's full body from head to toe, keeping a full-body framing."
        "enhance" -> " Enhance the image to maximum quality: restore and sharpen fine details, reduce noise, " +
            "artifacts and blur, and improve clarity, lighting and skin texture. Remove any errors or defects. " +
            "Keep it fully photorealistic — do NOT apply a comic, cartoon, illustration, painting or sketch style. " +
            "Preserve the person's identity, face, body proportions, clothing and background exactly."
        "black_bg" -> " Make the background completely solid black (pure black), with no other elements, objects, " +
            "gradients or edges visible. Keep the subject fully unchanged: same person, same face, same body " +
            "proportions, same clothing, same pose and same lighting on the subject."
        else -> ""
    }
    val finalPrompt =
        "${analysis.editPrompt}\n\n" +
            identityClause +
            modeClause +
            " Preserve everything that is not explicitly mentioned in this instruction$preserveClause. " +
            "Do not change the person's identity, face, pose, body proportions, other clothing, background, " +
            "lighting, or composition unless the instruction explicitly asks for it. " +
            "Keep the original aspect ratio and proportions exactly — do not stretch, squash, widen, or narrow " +
            "the subject or the background.\n\n${Skills.IMAGE_GENERATION}\n\n${Skills.REFERENCE_IMAGE}"

    for (attempt in 0..MAX_CONTENT_POLICY_RETRIES) {
        val prompt = if (attempt == 0) finalPrompt else softenPrompt(finalPrompt, attempt)
        try {
            val result = api.generateImage(
                model = EDIT_MODEL,
                prompt = prompt,
                size = size,
                ratio = ratio,
                imageDataUris = imageDataUris,
                responseFormat = "b64_json",
            )
            val b64 = result.b64 ?: throw Exception("Image API returned no image data")
            return android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            if (attempt < MAX_CONTENT_POLICY_RETRIES && isContentPolicyViolation(e)) {
                continue
            }
            throw e
        }
    }
    throw Exception("Image editing failed after multiple attempts")
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
