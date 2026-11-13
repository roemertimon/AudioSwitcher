using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: AssemblyTitle("Audio Switcher")]
[assembly: AssemblyDescription("Fast, distraction-free audio playback switching on Windows with global shortcuts.")]
[assembly: AssemblyConfiguration("")]
[assembly: AssemblyCompany("Timon Roemer")]
[assembly: AssemblyProduct("Audio Switcher")]
[assembly: AssemblyCopyright("Copyright © 2026 Timon Roemer")]
[assembly: AssemblyTrademark("")]
[assembly: AssemblyCulture("")]
[assembly: AssemblyVersion("1.0.0.0")]
[assembly: AssemblyFileVersion("1.0.0.0")]

namespace AudioSwitcher.Launcher
{
    static class Program
    {
        [STAThread]
        static void Main(string[] args)
        {
            try
            {
                string baseDir = AppDomain.CurrentDomain.BaseDirectory;
                string jarPath = FindJar(baseDir);
                if (string.IsNullOrEmpty(jarPath))
                {
                    MessageBox.Show(
                        "Audio Switcher JAR file could not be found in:\n" + baseDir + "\n\nPlease ensure AudioSwitcher.jar or audio-switcher-1.0.0-all.jar is placed in the same folder as this executable.",
                        "Audio Switcher - Missing File",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Error
                    );
                    return;
                }

                string javaw = FindJavaw();
                if (string.IsNullOrEmpty(javaw))
                {
                    var result = MessageBox.Show(
                        "Java 17 or higher (javaw.exe) was not found on your system.\n\nWould you like to open the Eclipse Adoptium download page to install Java?",
                        "Audio Switcher - Java Required",
                        MessageBoxButtons.YesNo,
                        MessageBoxIcon.Warning
                    );
                    if (result == DialogResult.Yes)
                    {
                        Process.Start(new ProcessStartInfo("https://adoptium.net/") { UseShellExecute = true });
                    }
                    return;
                }

                string arguments = "-jar \"" + jarPath + "\"";
                if (args != null && args.Length > 0)
                {
                    arguments += " " + string.Join(" ", args);
                }

                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaw,
                    Arguments = arguments,
                    WorkingDirectory = baseDir,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };

                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "Error launching Audio Switcher:\n" + ex.Message,
                    "Audio Switcher",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        private static string FindJar(string baseDir)
        {
            string p1 = Path.Combine(baseDir, "AudioSwitcher.jar");
            if (File.Exists(p1)) return p1;

            string p2 = Path.Combine(baseDir, "audio-switcher-1.0.0-all.jar");
            if (File.Exists(p2)) return p2;

            if (Directory.Exists(baseDir))
            {
                string[] jars = Directory.GetFiles(baseDir, "*.jar");
                if (jars != null && jars.Length > 0)
                {
                    foreach (string j in jars)
                    {
                        string name = Path.GetFileName(j).ToLowerInvariant();
                        if (name.Contains("audio") && name.Contains("switcher"))
                        {
                            return j;
                        }
                    }
                    return jars[0];
                }
            }

            return null;
        }

        private static string FindJavaw()
        {
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string p = Path.Combine(javaHome, "bin", "javaw.exe");
                if (File.Exists(p)) return p;
            }

            string[] regRoots = {
                @"SOFTWARE\JavaSoft\JDK",
                @"SOFTWARE\JavaSoft\Java Development Kit",
                @"SOFTWARE\JavaSoft\JRE",
                @"SOFTWARE\JavaSoft\Java Runtime Environment",
                @"SOFTWARE\Eclipse Adoptium\JDK"
            };

            foreach (string root in regRoots)
            {
                string fromReg = CheckRegistryJava(Registry.LocalMachine, root);
                if (!string.IsNullOrEmpty(fromReg) && File.Exists(fromReg)) return fromReg;
                fromReg = CheckRegistryJava(Registry.CurrentUser, root);
                if (!string.IsNullOrEmpty(fromReg) && File.Exists(fromReg)) return fromReg;
            }

            string[] programFilesRoots = {
                Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles),
                Environment.GetEnvironmentVariable("ProgramW6432") ?? @"C:\Program Files"
            };

            foreach (string pf in programFilesRoots)
            {
                if (string.IsNullOrEmpty(pf) || !Directory.Exists(pf)) continue;

                string javaDir = Path.Combine(pf, "Java");
                if (Directory.Exists(javaDir))
                {
                    foreach (string sub in Directory.GetDirectories(javaDir))
                    {
                        string p = Path.Combine(sub, "bin", "javaw.exe");
                        if (File.Exists(p)) return p;
                    }
                }

                string adoptiumDir = Path.Combine(pf, "Eclipse Adoptium");
                if (Directory.Exists(adoptiumDir))
                {
                    foreach (string sub in Directory.GetDirectories(adoptiumDir))
                    {
                        string p = Path.Combine(sub, "bin", "javaw.exe");
                        if (File.Exists(p)) return p;
                    }
                }
            }

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo("where.exe", "javaw.exe")
                {
                    UseShellExecute = false,
                    RedirectStandardOutput = true,
                    CreateNoWindow = true
                };
                using (Process proc = Process.Start(psi))
                {
                    string output = proc.StandardOutput.ReadLine();
                    proc.WaitForExit();
                    if (!string.IsNullOrEmpty(output) && File.Exists(output.Trim()))
                    {
                        return output.Trim();
                    }
                }
            }
            catch { }

            return null;
        }

        private static string CheckRegistryJava(RegistryKey rootKey, string subKeyPath)
        {
            try
            {
                using (RegistryKey key = rootKey.OpenSubKey(subKeyPath))
                {
                    if (key == null) return null;
                    string currentVersion = key.GetValue("CurrentVersion") as string;
                    if (!string.IsNullOrEmpty(currentVersion))
                    {
                        using (RegistryKey verKey = key.OpenSubKey(currentVersion))
                        {
                            if (verKey != null)
                            {
                                string jh = verKey.GetValue("JavaHome") as string;
                                if (!string.IsNullOrEmpty(jh))
                                {
                                    string p = Path.Combine(jh, "bin", "javaw.exe");
                                    if (File.Exists(p)) return p;
                                }
                            }
                        }
                    }

                    foreach (string sub in key.GetSubKeyNames())
                    {
                        using (RegistryKey sk = key.OpenSubKey(sub))
                        {
                            if (sk != null)
                            {
                                string jh = sk.GetValue("JavaHome") as string;
                                if (!string.IsNullOrEmpty(jh))
                                {
                                    string p = Path.Combine(jh, "bin", "javaw.exe");
                                    if (File.Exists(p)) return p;
                                }
                            }
                        }
                    }
                }
            }
            catch { }
            return null;
        }
    }
}
