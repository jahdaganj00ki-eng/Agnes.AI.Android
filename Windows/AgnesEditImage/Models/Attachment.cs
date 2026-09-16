namespace AgnesEditImage.Models;

public abstract record Attachment;

public record LocalAttachment(byte[] Bytes, string Mime) : Attachment;

public record RemoteAttachment(string Url) : Attachment;
