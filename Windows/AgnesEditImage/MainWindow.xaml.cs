using System.Windows;
using System.Windows.Media.Imaging;
using AgnesEditImage.ViewModels;

namespace AgnesEditImage;

public partial class MainWindow : Window
{
    private readonly EditImageViewModel _viewModel;

    public MainWindow()
    {
        InitializeComponent();
        _viewModel = new EditImageViewModel();
        DataContext = _viewModel;
    }

    private void Menu_Click(object sender, RoutedEventArgs e)
    {
        var settings = new SettingsWindow
        {
            Owner = this
        };

        if (_viewModel.SavedApiKey is not null)
        {
            settings.ApiKeyBox.Password = _viewModel.SavedApiKey;
        }
        settings.BaseUrlBox.Text = _viewModel.SavedBaseUrl;

        if (settings.ShowDialog() == true)
        {
            _viewModel.UpdateSettings(settings.ApiKey, settings.BaseUrl);
        }
    }

    private void NewChat_Click(object sender, RoutedEventArgs e)
    {
        _viewModel.ResetCommand.Execute(null);
    }

    private void Attach_Click(object sender, RoutedEventArgs e)
    {
        _viewModel.AddImagesCommand.Execute(null);
    }

    private void AttachUrl_Click(object sender, RoutedEventArgs e)
    {
        _viewModel.AddImageUrlCommand.Execute(null);
    }
}
