package com.audioswitcher.startup;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.io.File;

public class StartupManager {

    private static final String RUN_KEY = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "JavaAudioSwitcher";

    public static boolean isStartupEnabled() {
        try {
            return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, RUN_KEY, APP_NAME);
        } catch (Exception e) {
            return false;
        }
    }

    public static void setStartupEnabled(boolean enable) {
        try {
            if (enable) {
                String launchCommand = getApplicationLaunchCommand();
                Advapi32Util.registrySetStringValue(
                        WinReg.HKEY_CURRENT_USER,
                        RUN_KEY,
                        APP_NAME,
                        launchCommand
                );
            } else {
                if (isStartupEnabled()) {
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

            String javaw = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";

            // 1. If running directly from an executable JAR
            if (codeSourceFile.isFile() && codeSourceFile.getName().toLowerCase().endsWith(".jar")) {
                return "\"" + javaw + "\" -jar \"" + codeSourceFile.getAbsolutePath() + "\" --minimized";
            }

            // 2. If running from IDE / dev classes, look for built fat jar in build/libs
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

        // 3. Fallback for development workspace
        String userDir = System.getProperty("user.dir", ".");
        return "cmd.exe /c \"cd /d \"" + userDir + "\" && gradlew.bat run --args=\"--minimized\"\"";
    }
}
