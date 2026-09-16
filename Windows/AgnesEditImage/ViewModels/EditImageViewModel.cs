using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Windows;
using System.Windows.Input;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using AgnesEditImage.Models;
using AgnesEditImage.Services;

namespace AgnesEditImage.ViewModels;

public partial class EditImageViewModel : ObservableObject
{
    private readonly SettingsService _settingsService;
    private AgnesApi _api;

    [ObservableProperty]
    private ObservableCollection<ChatItem> _items = new();

    [ObservableProperty]
    private bool _busy;

    [ObservableProperty]
    private string _input = "";

    [ObservableProperty]
    private List<Attachment> _attachments = new();

    [ObservableProperty]
    private string _mode = "edit";

    [ObservableProperty]
    private string _title = "Edit Image";

    [ObservableProperty]
    private bool _apiKeyConfigured;

    [ObservableProperty]
    private string _savedApiKey = "";

    [ObservableProperty]
    private string _savedBaseUrl = "";

    [ObservableProperty]
    private bool _lastSaved;

    public EditImageViewModel()
    {
        _settingsService = new SettingsService();
        var settings = _settingsService.Load();
        _api = new AgnesApi(settings.ApiKey, settings.BaseUrl);
        _savedApiKey = settings.ApiKey;
        _savedBaseUrl = settings.BaseUrl;
        _apiKeyConfigured = !string.IsNullOrWhiteSpace(settings.ApiKey);
    }

    [RelayCommand]
    private void AddImages()
    {
        var dialog = new Microsoft.Win32.OpenFileDialog
        {
            Multiselect = true,
            Filter = "Bilder|*.jpg;*.jpeg;*.png;*.webp"
        };

        if (dialog.ShowDialog() != true) return;

        foreach (var file in dialog.FileNames.Take(10))
        {
            try
            {
                var bytes = File.ReadAllBytes(file);
                if (bytes.Length == 0) continue;
                var mime = Path.GetExtension(file).ToLowerInvariant() switch
                {
                    ".jpg" or ".jpeg" => "image/jpeg",
                    ".png" => "image/png",
                    ".webp" => "image/webp",
                    _ => "image/jpeg"
                };
                Attachments.Add(new LocalAttachment(bytes, mime));
            }
            catch
            {
                // ignore unreadable files
            }
        }
    }

    [RelayCommand]
    private void AddImageUrl()
    {
        var dialog = new UrlInputWindow();
        if (dialog.ShowDialog() != true) return;

        var url = dialog.EnteredUrl?.Trim();
        if (string.IsNullOrWhiteSpace(url)) return;

        if (Uri.TryCreate(url, UriKind.Absolute, out var uri) &&
            (uri.Scheme == Uri.UriSchemeHttp || uri.Scheme == Uri.UriSchemeHttps))
        {
            Attachments.Add(new RemoteAttachment(url));
        }
    }

    [RelayCommand]
    private void RemoveAttachment(int index)
    {
        if (index < 0 || index >= Attachments.Count) return;
        Attachments.RemoveAt(index);
    }

    [RelayCommand]
    private void UseAsInput(byte[] bytes)
    {
        if (bytes == null || bytes.Length == 0) return;
        Attachments = new List<Attachment> { new LocalAttachment(bytes, "image/png") };
    }

    [RelayCommand]
    private void SetMode(string mode)
    {
        Mode = mode;
    }

    [RelayCommand]
    private void Reset()
    {
        var settings = _settingsService.Load();
        Items = new ObservableCollection<ChatItem>();
        Busy = false;
        Input = "";
        Attachments = new List<Attachment>();
        Mode = "edit";
        Title = "Edit Image";
        ApiKeyConfigured = !string.IsNullOrWhiteSpace(settings.ApiKey);
        SavedApiKey = settings.ApiKey;
        SavedBaseUrl = settings.BaseUrl;
        LastSaved = false;
    }

    public void UpdateSettings(string apiKey, string baseUrl)
    {
        var newKey = apiKey?.Trim() ?? "";
        var newBase = baseUrl?.Trim() ?? "";
        if (string.IsNullOrWhiteSpace(newBase))
        {
            newBase = "https://apihub.agnes-ai.com/v1";
        }

        _settingsService.Save(newKey, newBase);
        _api = new AgnesApi(newKey, newBase);
        SavedApiKey = newKey;
        SavedBaseUrl = newBase;
        ApiKeyConfigured = !string.IsNullOrWhiteSpace(newKey);
        LastSaved = true;
    }

    [RelayCommand]
    private async Task Submit()
    {
        if (Attachments.Count == 0) return;
        var prompt = Input.Trim();
        if (prompt.Length == 0 || Busy) return;

        var images = Attachments.ToList();
        var skillLoads = Skills.All.Select(s => new LoadedSkill(s.Badge, s.Content.Length)).ToList();

        Items.Clear();
        Items.Add(new UserMessage(prompt, images));
        Items.Add(new ThoughtGroup("…", Skills: new List<LoadedSkill>(), Expanded: false));
        Items.Add(new ThoughtGroup("…", skillLoads, Expanded: true));
        Items.Add(new AssistantText(""));
        Items.Add(new PromptEnhancement(prompt, ""));
        Items.Add(new StatusBanner("Das dauert etwa 15–45 Sekunden, bitte habe einen Moment Geduld.", Active: true));

        Busy = true;
        Input = "";
        Attachments = new List<Attachment>();
        Title = prompt;

        try
        {
            var imageDataUris = images.Select(att =>
            {
                if (att is LocalAttachment local)
                {
                    var mime = string.IsNullOrWhiteSpace(local.Mime) ? "image/jpeg" : local.Mime;
                    return $"data:{mime};base64,{Convert.ToBase64String(local.Bytes)}";
                }
                return ((RemoteAttachment)att).Url;
            }).ToList();

            var t0 = Environment.TickCount;
            var analysis = await EditImagePipeline.AnalyzeAndEnhanceAsync(_api, imageDataUris, prompt);
            var analysisSeconds = (Environment.TickCount - t0) / 1000.0;

            Items[1] = new ThoughtGroup($"{analysisSeconds:F2}", Skills: new List<LoadedSkill>(), Expanded: false);
            Items[3] = new AssistantText(analysis.ReplyDe);
            Items[4] = new PromptEnhancement(prompt, analysis.EditPrompt);

            var t1 = Environment.TickCount;
            var firstLocal = images.OfType<LocalAttachment>().FirstOrDefault()?.Bytes;
            var dims = firstLocal != null ? ImageProcessor.ImageDimensions(firstLocal) : null;
            var ratio = dims.HasValue ? EditImagePipeline.PickRatio(dims.Value.Width, dims.Value.Height) : "3:4";
            var resultBytes = await EditImagePipeline.GenerateEditAsync(_api, imageDataUris, analysis, ratio, "2K", Mode);
            var genSeconds = (Environment.TickCount - t1) / 1000.0;

            Items[2] = new ThoughtGroup($"{genSeconds:F2}", skillLoads, Expanded: true);
            Items[5] = new StatusBanner("Bearbeitung abgeschlossen.", Active: false);
            Items.Add(new AssistantText("Ich habe die gewünschte Änderung vorgenommen."));
            Items.Add(new ResultImage(resultBytes));
        }
        catch (Exception ex)
        {
            if (Items.Count > 5)
            {
                Items[5] = new StatusBanner("Bearbeitung fehlgeschlagen.", Active: false);
            }
            Items.Add(new ErrorItem(ex.Message ?? "Unbekannter Fehler"));
        }
        finally
        {
            Busy = false;
        }
    }

    [RelayCommand]
    private void Download(byte[] bytes)
    {
        if (bytes == null || bytes.Length == 0) return;

        var path = ImageProcessor.SaveToGallery(bytes);
        if (path != null)
        {
            MessageBox.Show($"Bild gespeichert unter:\n{path}", "Gespeichert", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        else
        {
            MessageBox.Show("Speichern fehlgeschlagen.", "Fehler", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    [RelayCommand]
    private void OpenFullscreen(byte[] bytes)
    {
        if (bytes == null || bytes.Length == 0) return;

        var bitmap = ImageProcessor.DecodeBitmap(bytes);
        if (bitmap == null) return;

        var window = new FullscreenWindow(bitmap);
        window.ShowDialog();
    }
}
