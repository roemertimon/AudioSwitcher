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

    private static String getApplicationLaunchCommand() {
        try {
            // Check if running from JAR
            String jarPath = StartupManager.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            if (jarPath != null && jarPath.endsWith(".jar")) {
                if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
                    jarPath = jarPath.substring(1); // remove leading slash on Windows
                }
                String javaw = System.getProperty("java.home") + File.separator + "bin" + File.separator + "javaw.exe";
                return "\"" + javaw + "\" -jar \"" + jarPath + "\"";
            }
        } catch (Exception ignored) {
        }

        // Fallback: point to current working directory gradlew.bat
        String userDir = System.getProperty("user.dir");
        return "\"" + userDir + File.separator + "gradlew.bat\" run";
    }
}
