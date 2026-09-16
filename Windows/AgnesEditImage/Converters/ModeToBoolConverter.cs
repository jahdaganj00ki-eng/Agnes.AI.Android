using System;
using System.Globalization;
using System.Windows.Data;

namespace AgnesEditImage.Converters;

public class ModeToBoolConverter : IValueConverter
{
    public static ModeToBoolConverter Instance { get; } = new ModeToBoolConverter();

    public object? Convert(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (value is string mode && parameter is string param)
        {
            return string.Equals(mode, param, StringComparison.OrdinalIgnoreCase);
        }
        return false;
    }

    public object? ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture)
    {
        if (parameter is string param && value is bool b && b)
        {
            return param;
        }
        return Binding.DoNothing;
    }
}
