using System;
using System.Globalization;
using System.Windows.Data;
using System.Windows.Media.Imaging;
using AgnesEditImage.Models;

namespace AgnesEditImage.Converters;

public class AttachmentToImageConverter : IValueConverter
{
    public static AttachmentToImageConverter Instance { get; } = new AttachmentToImageConverter();

    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is LocalAttachment local)
        {
            if (local.Bytes is not null && local.Bytes.Length > 0)
            {
                return ImageProcessor.DecodeBitmap(local.Bytes);
            }
            return null;
        }

        if (value is RemoteAttachment remote)
        {
            try
            {
                var uri = new Uri(remote.Url);
                var bitmap = new BitmapImage();
                bitmap.BeginInit();
                bitmap.UriSource = uri;
                bitmap.CacheOption = BitmapCacheOption.OnLoad;
                bitmap.EndInit();
                bitmap.Freeze();
                return bitmap;
            }
            catch
            {
                return null;
            }
        }

        return null;
    }

    public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
