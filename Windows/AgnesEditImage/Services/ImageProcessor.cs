using System.IO;
using System.Net.Http;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using SixLabors.ImageSharp;
using SixLabors.ImageSharp.Formats.Png;
using SixLabors.ImageSharp.Processing;

namespace AgnesEditImage.Services;

public static class ImageProcessor
{
    public static (int Width, int Height)? ImageDimensions(byte[] bytes)
    {
        if (bytes == null || bytes.Length == 0) return null;

        try
        {
            using var stream = new MemoryStream(bytes);
            var format = Image.DetectFormat(stream);
            if (format == null) return null;

            using var image = Image.Load(format, stream);
            return (image.Width, image.Height);
        }
        catch
        {
            return null;
        }
    }

    public static BitmapSource DecodeBitmap(byte[] bytes, int maxSize = 2048)
    {
        if (bytes == null || bytes.Length == 0) return null;

        using var image = Image.Load(bytes);
        var scale = Math.Min(1f, Math.Min((float)maxSize / image.Width, (float)maxSize / image.Height));
        if (scale < 1f)
        {
            var newWidth = (int)(image.Width * scale);
            var newHeight = (int)(image.Height * scale);
            image.Mutate(x => x.Resize(newWidth, newHeight));
        }

        using var outStream = new MemoryStream();
        image.Save(outStream, new PngEncoder());
        outStream.Seek(0, SeekOrigin.Begin);
        var bitmap = new BitmapImage();
        bitmap.BeginInit();
        bitmap.StreamSource = outStream;
        bitmap.CacheOption = BitmapCacheOption.OnLoad;
        bitmap.EndInit();
        bitmap.Freeze();
        return bitmap;
    }

    public static string SaveToGallery(byte[] bytes)
    {
        if (bytes == null || bytes.Length == 0) return null;

        try
        {
            var pictures = Environment.GetFolderPath(Environment.SpecialFolder.MyPictures);
            Directory.CreateDirectory(pictures);
            var fileName = $"agnes_edit_{DateTime.Now:yyyyMMdd_HHmmss}.png";
            var path = Path.Combine(pictures, fileName);
            File.WriteAllBytes(path, bytes);
            return path;
        }
        catch
        {
            return null;
        }
    }

    public static async Task<byte[]> FetchBytesAsync(string url, CancellationToken ct = default)
    {
        using var response = await SharedHttpClient.GetAsync(url, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadAsByteArrayAsync(ct).ConfigureAwait(false);
    }

    private static readonly HttpClient SharedHttpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(60)
    };
}
