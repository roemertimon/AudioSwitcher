package com.audioswitcher.hotkey;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.ptr.IntByReference;

import java.util.Set;

/**
 * Detects whether a game or exclusive/borderless fullscreen application is active in the foreground,
 * preventing accidental audio switching when key combinations (like Ctrl+Alt+S or Ctrl+Alt+1..4)
 * overlap with in-game controls (crouch, walk, ping, weapon selection, movement).
 */
public class GameDetector {

    private static final Set<String> KNOWN_GAME_IDENTIFIERS = Set.of(
            "valorant",
            "valorant-win64-shipping",
            "vgc",
            "riotclientux",
            "cs2",
            "csgo",
            "overwatch",
            "apex",
            "r5apex",
            "fortniteclient-win64-shipping",
            "fortnite",
            "leagueclient",
            "league of legends",
            "dota2",
            "rocketleague",
            "rainbowsix",
            "gta5",
            "eldenring",
            "steamapps"
    );

    /**
     * Checks if a game or fullscreen exclusive/borderless window is currently focused.
     */
    public static boolean isGameOrFullscreenActive() {
        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) {
                return false;
            }

            // Exclude desktop window
            HWND desktopHwnd = User32.INSTANCE.GetDesktopWindow();
            if (hwnd.equals(desktopHwnd)) {
                return false;
            }

            // 1. Process name inspection
            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            int pid = pidRef.getValue();

            if (pid > 0) {
                String procName = getProcessName(pid);
                if (procName != null && !procName.isBlank()) {
                    String lower = procName.toLowerCase();
                    for (String game : KNOWN_GAME_IDENTIFIERS) {
                        if (lower.contains(game)) {
                            return true;
                        }
                    }
                }
            }

            // 2. Fullscreen window coverage check (games running fullscreen borderless or exclusive)
            RECT windowRect = new RECT();
            if (User32.INSTANCE.GetWindowRect(hwnd, windowRect)) {
                int screenW = User32.INSTANCE.GetSystemMetrics(0); // SM_CXSCREEN
                int screenH = User32.INSTANCE.GetSystemMetrics(1); // SM_CYSCREEN

                // If window bounds match or cover the entire primary screen
                if (windowRect.left <= 0 && windowRect.top <= 0
                        && windowRect.right >= screenW && windowRect.bottom >= screenH) {
                    char[] className = new char[256];
                    User32.INSTANCE.GetClassName(hwnd, className, 256);
                    String cls = new String(className).trim();

                    // Ignore Windows shell components that occupy the screen
                    if (!cls.equalsIgnoreCase("Progman") && !cls.equalsIgnoreCase("WorkerW")
                            && !cls.equalsIgnoreCase("Shell_TrayWnd") && !cls.equalsIgnoreCase("Windows.UI.Core.CoreWindow")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns the name of the foreground process for logging purposes.
     */
    public static String getForegroundProcessName() {
        try {
            HWND hwnd = User32.INSTANCE.GetForegroundWindow();
            if (hwnd == null) return "None";

            IntByReference pidRef = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef);
            int pid = pidRef.getValue();

            if (pid > 0) {
                String name = getProcessName(pid);
                if (name != null && !name.isBlank()) {
                    return name;
                }
                return "PID:" + pid;
            }
        } catch (Exception ignored) {
        }
        return "Unknown";
    }

    private static String getProcessName(int pid) {
        try {
            return ProcessHandle.of(pid)
                    .flatMap(p -> p.info().command())
                    .map(cmd -> {
                        int lastSlash = Math.max(cmd.lastIndexOf('/'), cmd.lastIndexOf('\\'));
                        return lastSlash >= 0 ? cmd.substring(lastSlash + 1) : cmd;
                    })
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
