using System.IO;
using System.Text.Json;
using AgnesEditImage.Models;

namespace AgnesEditImage.Services;

public class SettingsService
{
    private readonly string _settingsPath;

    public SettingsService()
    {
        var appData = Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
        var folder = Path.Combine(appData, "AgnesEditImage");
        Directory.CreateDirectory(folder);
        _settingsPath = Path.Combine(folder, "settings.json");
    }

    public AppSettings Load()
    {
        try
        {
            if (!File.Exists(_settingsPath))
            {
                return new AppSettings(GetFallbackApiKey(), "https://apihub.agnes-ai.com/v1");
            }

            var json = File.ReadAllText(_settingsPath);
            var settings = JsonSerializer.Deserialize<AppSettings>(json);
            if (settings is null)
            {
                return new AppSettings(GetFallbackApiKey(), "https://apihub.agnes-ai.com/v1");
            }

            var apiKey = string.IsNullOrWhiteSpace(settings.ApiKey) ? GetFallbackApiKey() : settings.ApiKey;
            var baseUrl = string.IsNullOrWhiteSpace(settings.BaseUrl) ? "https://apihub.agnes-ai.com/v1" : settings.BaseUrl;

            return new AppSettings(apiKey, baseUrl);
        }
        catch
        {
            return new AppSettings(GetFallbackApiKey(), "https://apihub.agnes-ai.com/v1");
        }
    }

    public void Save(string apiKey, string baseUrl)
    {
        var settings = new AppSettings(apiKey, baseUrl);
        var json = JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true });
        File.WriteAllText(_settingsPath, json);
    }

    private static string GetFallbackApiKey()
    {
        return Environment.GetEnvironmentVariable("AGNES_API_KEY") ?? string.Empty;
    }
}
