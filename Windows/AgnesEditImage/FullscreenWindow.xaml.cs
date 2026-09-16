using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace AgnesEditImage;

public partial class FullscreenWindow : Window
{
    private Point _lastMouse;
    private double _scale = 1.0;

    public FullscreenWindow(ImageSource image)
    {
        InitializeComponent();
        FullscreenImage.Source = image;
        UpdateTransform();
    }

    private void Window_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape) Close();
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    protected override void OnPreviewMouseWheel(MouseWheelEventArgs e)
    {
        var delta = e.Delta > 0 ? 1.1 : 0.9;
        _scale *= delta;
        _scale = Math.Max(1.0, Math.Min(6.0, _scale));
        UpdateTransform();
        e.Handled = true;
    }

    protected override void OnPreviewMouseLeftButtonDown(MouseButtonEventArgs e)
    {
        _lastMouse = e.GetPosition(this);
        CaptureMouse();
        e.Handled = true;
    }

    protected override void OnPreviewMouseMove(MouseEventArgs e)
    {
        if (IsMouseCaptured)
        {
            var pos = e.GetPosition(this);
            var dx = pos.X - _lastMouse.X;
            var dy = pos.Y - _lastMouse.Y;
            var matrix = ImageTransform.Matrix;
            matrix.OffsetX += dx;
            matrix.OffsetY += dy;
            ImageTransform.Matrix = matrix;
            _lastMouse = pos;
        }
    }

    protected override void OnPreviewMouseLeftButtonUp(MouseButtonEventArgs e)
    {
        ReleaseMouseCapture();
        e.Handled = true;
    }

    private void UpdateTransform()
    {
        ImageTransform.Matrix = new Matrix(_scale, 0, 0, _scale, 0, 0);
    }
}
