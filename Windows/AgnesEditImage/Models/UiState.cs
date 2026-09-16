namespace AgnesEditImage.Models;

public record UiState(
    List<ChatItem> Items,
    bool Busy,
    string Input,
    List<Attachment> Attachments,
    string Mode,
    string Title,
    bool ApiKeyConfigured,
    string SavedApiKey,
    string SavedBaseUrl,
    bool LastSaved
);
