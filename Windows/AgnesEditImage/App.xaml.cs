using System;
using System.IO;
using System.Windows;
using AgnesEditImage.Services;

namespace AgnesEditImage;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        AppDomain.CurrentDomain.UnhandledException += (s, args) =>
        {
            var ex = args.ExceptionObject as Exception;
            Logger.Error("Unhandled exception in AppDomain: " + ex);
        };

        DispatcherUnhandledException += (s, args) =>
        {
            Logger.Error("Dispatcher unhandled exception: " + args.Exception);
            args.Handled = true;
        };

        try
        {
            Logger.Init();
            Logger.Info("Application starting");
        }
        catch (Exception ex)
        {
            Logger.Error(ex);
        }

        base.OnStartup(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        Logger.Info("Application exiting");
        base.OnExit(e);
    }
}
