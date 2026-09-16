using System;
using System.Globalization;
using System.Windows.Data;
using System.Windows.Media.Imaging;
using AgnesEditImage.Services;

namespace AgnesEditImage.Converters;

public class ByteArrayToImageConverter : IValueConverter
{
    public static ByteArrayToImageConverter Instance { get; } = new ByteArrayToImageConverter();

    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is byte[] bytes && bytes.Length > 0)
        {
            return ImageProcessor.DecodeBitmap(bytes);
        }
        return null;
    }

    public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
