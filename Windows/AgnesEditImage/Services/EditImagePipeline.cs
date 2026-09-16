using System.Text.Json;
using System.Text.RegularExpressions;
using AgnesEditImage.Models;

namespace AgnesEditImage.Services;

public static class EditImagePipeline
{
    private const string ANALYSIS_MODEL = "agnes-2.5-flash";
    private const string EDIT_MODEL = "agnes-image-2.1-flash";
    private const int MAX_CONTENT_POLICY_RETRIES = 4;

    private static JsonNode ExtractJson(string text)
    {
        var trimmed = text.Trim();
        try
        {
            return JsonNode.Parse(trimmed) ?? throw new Exception("Empty JSON");
        }
        catch
        {
            var fence = Regex.Match(trimmed, @"```(?:json)?\s*([\s\S]*?)```", RegexOptions.IgnoreCase);
            if (fence.Success)
            {
                try
                {
                    return JsonNode.Parse(fence.Groups[1].Value.Trim()) ?? throw new Exception("Empty JSON in fence");
                }
                catch
                {
                    // fall through
                }
            }

            var first = trimmed.IndexOf('{');
            var last = trimmed.LastIndexOf('}');
            if (first >= 0 && last > first)
            {
                try
                {
                    return JsonNode.Parse(trimmed.Substring(first, last - first + 1)) ?? throw new Exception("Empty JSON in fallback");
                }
                catch
                {
                    // fall through
                }
            }

            throw new Exception($"Could not parse JSON from model output: {trimmed.Take(300)}");
        }
    }

    public static async Task<Analysis> AnalyzeAndEnhanceAsync(AgnesApi api, List<string> imageDataUris, string userPrompt, CancellationToken ct = default)
    {
        var userContent = new JsonArray
        {
            new JsonObject { ["type"] = "text", ["text"] = $"User edit instruction:\n\"\"\"\n{userPrompt}\n\"\"\"" }
        };

        foreach (var uri in imageDataUris)
        {
            userContent.Add(new JsonObject
            {
                ["type"] = "image_url",
                ["image_url"] = new JsonObject { ["url"] = uri }
            });
        }

        var raw = await api.ChatAsync(ANALYSIS_MODEL, Skills.PROMPT_CRAFT, userContent, ct: ct).ConfigureAwait(false);
        var obj = ExtractJson(raw);

        return new Analysis(
            AnalysisText: obj["analysis"]?.GetValue<string>() ?? string.Empty,
            EditPrompt: obj["edit_prompt"]?.GetValue<string>() ?? string.Empty,
            Preserve: obj["preserve"]?.GetValue<string>() ?? string.Empty,
            ReplyDe: obj["reply_de"]?.GetValue<string>() ?? "Ich bearbeite das Bild entsprechend deiner Anweisung."
        );
    }

    public static async Task<byte[]> GenerateEditAsync(AgnesApi api, List<string> imageDataUris, Analysis analysis, string ratio, string size, string mode, CancellationToken ct = default)
    {
        var preserveClause = !string.IsNullOrWhiteSpace(analysis.Preserve)
            ? $" (in particular: {analysis.Preserve})"
            : "";

        var identityClause = imageDataUris.Count > 1
            ? "The subject must look exactly like the person in the provided reference images: same face, same identity, same hair, same body shape and proportions, and the same clothing. Use all reference images together to reconstruct the person faithfully."
            : "The subject must look exactly like the person in the reference image: same face, same identity, same body shape and proportions, and the same clothing.";

        var modeClause = mode switch
        {
            "full_body" => " Render the person's full body from head to toe, keeping a full-body framing.",
            "enhance" => " Enhance the image to maximum quality: restore and sharpen fine details, reduce noise, artifacts and blur, and improve clarity, lighting and skin texture. Remove any errors or defects. Keep it fully photorealistic — do NOT apply a comic, cartoon, illustration, painting or sketch style. Preserve the person's identity, face, body proportions, clothing and background exactly.",
            "black_bg" => " Make the background completely solid black (pure black), with no other elements, objects, gradients or edges visible. Keep the subject fully unchanged: same person, same face, same body proportions, same clothing, same pose and same lighting on the subject.",
            _ => ""
        };

        var finalPrompt = $"{analysis.EditPrompt}\n\n" +
            identityClause +
            modeClause +
            $" Preserve everything that is not explicitly mentioned in this instruction{preserveClause}. " +
            "Do not change the person's identity, face, body proportions, other clothing, background, lighting, or composition unless the instruction explicitly asks for it. " +
            "Keep the original aspect ratio and proportions exactly — do not stretch, squash, widen, or narrow the subject or the background. " +
            "Keep the arms in relaxed, natural positions. Do NOT raise both arms above shoulder height. Do NOT lift the arms above the head. At most one arm may be slightly raised. " +
            "Avoid any gymnastic, theatrical, or exaggerated arm positions. " +
            "Do NOT make the person fully frontal; keep only a slight quarter turn, at most about 20 degrees from the camera, and never turn into a full side profile.\n\n{Skills.IMAGE_GENERATION}\n\n{Skills.REFERENCE_IMAGE}";

        for (var attempt = 0; attempt <= MAX_CONTENT_POLICY_RETRIES; attempt++)
        {
            var prompt = attempt == 0 ? finalPrompt : SoftenPrompt(finalPrompt, attempt);
            try
            {
                var result = await api.GenerateImageAsync(EDIT_MODEL, prompt, size, ratio, imageDataUris, ct: ct).ConfigureAwait(false);
                var b64 = result.B64 ?? throw new Exception("Image API returned no image data");
                return Convert.FromBase64String(b64);
            }
            catch (Exception ex)
            {
                if (attempt < MAX_CONTENT_POLICY_RETRIES && IsContentPolicyViolation(ex))
                {
                    continue;
                }

                throw;
            }
        }

        throw new Exception("Image editing failed after multiple attempts");
    }

    private static string SoftenPrompt(string prompt, int level)
    {
        return level switch
        {
            1 => $"{prompt}\n\nKeep the result tasteful and modest; the subject should remain appropriately covered.",
            2 => $"{prompt}\n\nMake the result tasteful, modest, and fully clothed, with no revealing or suggestive elements.",
            3 => $"{prompt}\n\nRender a conservative, tasteful, family-friendly version. Keep the subject fully clothed and avoid any skin exposure beyond the face, hands, and neckline.",
            _ => $"Create a modest, tasteful, fully-clothed version of the requested edit, appropriate for all audiences. Original instruction: {prompt}"
        };
    }

    private static bool IsContentPolicyViolation(Exception ex)
    {
        return ex.Message?.Contains("content_policy_violation", StringComparison.OrdinalIgnoreCase) == true;
    }

    public static string PickRatio(int width, int height)
    {
        if (width <= 0 || height <= 0) return "3:4";

        var aspect = (double)width / height;
        var ratios = new List<(string Name, double Value)>
        {
            ("1:1", 1.0),
            ("3:4", 3.0 / 4.0),
            ("2:3", 2.0 / 3.0),
            ("9:16", 9.0 / 16.0),
            ("4:3", 4.0 / 3.0),
            ("3:2", 3.0 / 2.0),
            ("16:9", 16.0 / 9.0),
            ("21:9", 21.0 / 9.0)
        };

        var best = ratios[0];
        var bestDiff = double.MaxValue;
        foreach (var r in ratios)
        {
            var diff = Math.Abs(Math.Log(aspect) - Math.Log(r.Value));
            if (diff < bestDiff)
            {
                bestDiff = diff;
                best = r;
            }
        }

        return best.Name;
    }
}
