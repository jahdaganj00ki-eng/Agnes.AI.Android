namespace AgnesEditImage.Models;

public abstract record ChatItem;

public record UserMessage(string Text, List<Attachment> Images) : ChatItem;

public record ThoughtGroup(string DurationSeconds, List<LoadedSkill> Skills, bool Expanded) : ChatItem;

public record AssistantText(string Text) : ChatItem;

public record PromptEnhancement(string Original, string Enhanced) : ChatItem;

public record StatusBanner(string Text, bool Active = true) : ChatItem;

public record ResultImage(byte[] Bytes) : ChatItem;

public record ErrorItem(string Message) : ChatItem;
