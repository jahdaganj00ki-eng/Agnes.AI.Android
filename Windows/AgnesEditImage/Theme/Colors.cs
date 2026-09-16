using System.Windows.Media;

namespace AgnesEditImage.Theme;

public static class ThemeColors
{
    public static readonly Color AppBackgroundColor = Color.FromRgb(0x12, 0x12, 0x12);
    public static readonly Color CardBackgroundColor = Color.FromRgb(0x1E, 0x1E, 0x1E);
    public static readonly Color CardBackground2Color = Color.FromRgb(0x2A, 0x2A, 0x2E);
    public static readonly Color UserBubbleColor = Color.FromRgb(0x81, 0x84, 0xFD);
    public static readonly Color TealBadgeColor = Color.FromRgb(0x2D, 0xD4, 0xBF);
    public static readonly Color TextPrimaryColor = Color.FromRgb(0xF5, 0xF5, 0xF5);
    public static readonly Color TextMutedColor = Color.FromRgb(0x9E, 0x9E, 0x9E);
    public static readonly Color SparkleCyanColor = Color.FromRgb(0x8A, 0xD8, 0xF0);
    public static readonly Color ErrorRedColor = Color.FromRgb(0xEF, 0x6A, 0x6A);

    public static readonly Brush AppBackground = new SolidColorBrush(AppBackgroundColor);
    public static readonly Brush CardBackground = new SolidColorBrush(CardBackgroundColor);
    public static readonly Brush CardBackground2 = new SolidColorBrush(CardBackground2Color);
    public static readonly Brush UserBubble = new SolidColorBrush(UserBubbleColor);
    public static readonly Brush TealBadge = new SolidColorBrush(TealBadgeColor);
    public static readonly Brush TextPrimary = new SolidColorBrush(TextPrimaryColor);
    public static readonly Brush TextMuted = new SolidColorBrush(TextMutedColor);
    public static readonly Brush SparkleCyan = new SolidColorBrush(SparkleCyanColor);
    public static readonly Brush ErrorRed = new SolidColorBrush(ErrorRedColor);
}
