using System;
using System.Collections;
using System.Globalization;
using System.Windows;
using System.Windows.Data;

namespace AgnesEditImage.Converters;

public class CollectionVisibilityConverter : IValueConverter
{
    public static CollectionVisibilityConverter Instance { get; } = new CollectionVisibilityConverter();

    public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is ICollection collection && collection.Count > 0)
        {
            return Visibility.Visible;
        }
        return Visibility.Collapsed;
    }

    public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        throw new NotImplementedException();
    }
}
