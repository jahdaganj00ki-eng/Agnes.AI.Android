using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using AgnesEditImage.Models;

namespace AgnesEditImage.Services;

public class AgnesApi
{
    private static readonly HttpClient SharedClient = new()
    {
        Timeout = TimeSpan.FromSeconds(300)
    };

    private readonly string _apiKey;
    private readonly string _baseUrl;

    public AgnesApi(string apiKey, string baseUrl)
    {
        _apiKey = apiKey ?? string.Empty;
        _baseUrl = baseUrl?.TrimEnd('/') ?? "https://apihub.agnes-ai.com/v1";
    }

    public async Task<string> ChatAsync(string model, string systemPrompt, JsonNode userContent, int maxTokens = 1200, bool jsonMode = true, CancellationToken ct = default)
    {
        var body = new JsonObject
        {
            ["model"] = model,
            ["messages"] = new JsonArray
            {
                new JsonObject { ["role"] = "system", ["content"] = systemPrompt },
                new JsonObject { ["role"] = "user", ["content"] = userContent }
            },
            ["max_tokens"] = maxTokens
        };

        if (jsonMode)
        {
            body["response_format"] = new JsonObject { ["type"] = "json_object" };
        }

        using var request = new HttpRequestMessage(HttpMethod.Post, $"{_baseUrl}/chat/completions")
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json")
        };

        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);

        using var response = await SharedClient.SendAsync(request, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Agnes API {(int)response.StatusCode}: {text.Take(1000)}");
        }

        var root = JsonNode.Parse(text);
        var content = root?["choices"]?[0]?["message"]?["content"]?.GetValue<string>() ?? string.Empty;
        return content;
    }

    public async Task<GeneratedImage> GenerateImageAsync(string model, string prompt, string size, string ratio, List<string> imageDataUris, string responseFormat = "b64_json", CancellationToken ct = default)
    {
        var extra = new JsonObject
        {
            ["response_format"] = responseFormat
        };

        if (imageDataUris.Count > 0)
        {
            var images = new JsonArray();
            foreach (var uri in imageDataUris)
            {
                images.Add(uri);
            }
            extra["image"] = images;
        }

        var body = new JsonObject
        {
            ["model"] = model,
            ["prompt"] = prompt,
            ["size"] = size,
            ["ratio"] = ratio,
            ["extra_body"] = extra
        };

        using var request = new HttpRequestMessage(HttpMethod.Post, $"{_baseUrl}/images/generations")
        {
            Content = new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json")
        };

        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);

        using var response = await SharedClient.SendAsync(request, ct).ConfigureAwait(false);
        var text = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false);

        if (!response.IsSuccessStatusCode)
        {
            throw new Exception($"Agnes API {(int)response.StatusCode}: {text.Take(1000)}");
        }

        var root = JsonNode.Parse(text);
        var item = root?["data"]?[0];
        if (item is null)
        {
            return new GeneratedImage(null, null, null);
        }

        var b64 = item["b64_json"]?.GetValue<string>();
        var url = item["url"]?.GetValue<string>();
        var revised = item["revised_prompt"]?.GetValue<string>();

        return new GeneratedImage(
            B64: string.IsNullOrWhiteSpace(b64) ? null : b64,
            Url: string.IsNullOrWhiteSpace(url) ? null : url,
            RevisedPrompt: string.IsNullOrWhiteSpace(revised) ? null : revised
        );
    }
}
