package com.audioswitcher.startup;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class StartupManager {

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "AudioSwitcher";
    private static final String LEGACY_APP_NAME = "JavaAudioSwitcher";

    public static boolean isStartupEnabled() {
        try {
            return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)
                    || Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, LEGACY_APP_NAME);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setStartupEnabled(boolean enable) {
        try {
            // Clean up any legacy registry key
            if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, LEGACY_APP_NAME)) {
                Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, RUN_KEY, LEGACY_APP_NAME);
            }

            if (enable) {
                String launchCommand = getApplicationLaunchCommand();
                Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        RUN_KEY,
                        APP_NAME,
                        launchCommand
                );
            } else {
                if (Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME)) {
                    Advapi32Util.registryDeleteValue(
                            WinReg.HKEY_CURRENT_USER,
                            RUN_KEY,
                            APP_NAME
                    );
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update Windows startup registry key: " + e.getMessage());
        }
    }

    public static String getApplicationLaunchCommand() {
        try {
            File codeSourceFile = new File(StartupManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());

            File appDir = codeSourceFile.isFile() ? codeSourceFile.getParentFile() : codeSourceFile;
            if (appDir == null) {
                appDir = new File(System.getProperty("user.dir", "."));
            }

            // 1. If AudioSwitcher.exe already exists or can be extracted next to the JAR
            File exeFile = new File(appDir, "AudioSwitcher.exe");
            if (!exeFile.exists()) {
                try (InputStream in = StartupManager.class.getResourceAsStream("/AudioSwitcher.exe")) {
                    if (in != null) {
                        Files.copy(in, exeFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {
                }
            }

            if (exeFile.exists() && exeFile.isFile()) {
                return "\"" + exeFile.getAbsolutePath() + "\" --minimized";
            }

            String javaw = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";

            // 2. If running directly from an executable JAR without exe wrapper
            if (codeSourceFile.isFile() && codeSourceFile.getName().toLowerCase().endsWith(".jar")) {
                return "\"" + javaw + "\" -jar \"" + codeSourceFile.getAbsolutePath() + "\" --minimized";
            }

            // 3. If running from IDE / dev classes, look for built fat jar in build/libs
            File projectDir = new File(System.getProperty("user.dir", "."));
            File libsDir = new File(projectDir, "build" + File.separator + "libs");
            if (libsDir.exists() && libsDir.isDirectory()) {
                File[] jars = libsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    File targetJar = jars[0];
                    for (File j : jars) {
                        if (j.lastModified() > targetJar.lastModified()) {
                            targetJar = j;
                        }
                    }
                    return "\"" + javaw + "\" -jar \"" + targetJar.getAbsolutePath() + "\" --minimized";
                }
            }
        } catch (Exception ignored) {
        }

        // 4. Fallback for development workspace
        String userDir = System.getProperty("user.dir", ".");
        return "cmd.exe /c \"cd /d \"" + userDir + "\" && gradlew.bat run --args=\"--minimized\"\"";
    }
}
