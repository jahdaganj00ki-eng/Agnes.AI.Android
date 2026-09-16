using System.Windows;

namespace AgnesEditImage;

public partial class SettingsWindow : Window
{
    public string ApiKey => ApiKeyBox.Password;
    public string BaseUrl => BaseUrlBox.Text;

    public SettingsWindow()
    {
        InitializeComponent();
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = true;
        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e)
    {
        DialogResult = false;
        Close();
    }
}
