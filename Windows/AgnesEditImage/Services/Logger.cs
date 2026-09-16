using System;
using System.IO;
using System.Diagnostics;
using System.Windows;

namespace AgnesEditImage.Services;

public static class Logger
{
    private static readonly object _sync = new();
    private static string? _path;
    private static bool _initialized;

    public static void Init()
    {
        if (_initialized) return;

        lock (_sync)
        {
            if (_initialized) return;

            try
            {
                string exeDir;
                try
                {
                    exeDir = Path.GetDirectoryName(Environment.ProcessPath ?? string.Empty) ?? string.Empty;
                }
                catch
                {
                    exeDir = AppDomain.CurrentDomain.BaseDirectory;
                }

                if (string.IsNullOrWhiteSpace(exeDir))
                {
                    exeDir = AppDomain.CurrentDomain.BaseDirectory;
                }

                var logPath = Path.Combine(exeDir, "agnes-edit-image.log");
                _path = logPath;

                File.WriteAllText(_path, $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] Logger initialized. BaseDirectory={AppDomain.CurrentDomain.BaseDirectory}{Environment.NewLine}");
            }
            catch
            {
            }

            _initialized = true;
        }
    }

    public static void Info(string message)
    {
        Write("INFO", message);
    }

    public static void Error(string message)
    {
        Write("ERROR", message);
    }

    public static void Error(Exception ex)
    {
        Write("ERROR", ex.ToString());
    }

    private static void Write(string level, string message)
    {
        if (_path == null) return;

        try
        {
            var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}] [{level}] {message}";
            File.AppendAllText(_path, line + Environment.NewLine);
        }
        catch
        {
        }
    }
}
