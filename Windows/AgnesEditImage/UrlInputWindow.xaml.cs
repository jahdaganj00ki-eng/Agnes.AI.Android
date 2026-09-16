using System.Windows;

namespace AgnesEditImage;

public partial class UrlInputWindow : Window
{
    public string? EnteredUrl { get; private set; }

    public UrlInputWindow()
    {
        InitializeComponent();
    }

    private void Add_Click(object sender, RoutedEventArgs e)
    {
        EnteredUrl = UrlTextBox.Text;
        DialogResult = true;
        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }
}
