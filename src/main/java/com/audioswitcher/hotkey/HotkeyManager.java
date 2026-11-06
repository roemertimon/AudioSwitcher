package com.audioswitcher.hotkey;

import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class HotkeyManager {

    public static final int CYCLE_HOTKEY_ID = 1;
    public static final int DEVICE_HOTKEY_BASE_ID = 100;

    public static final int MOD_ALT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_SHIFT = 0x0004;
    public static final int MOD_WIN = 0x0008;
    public static final int MOD_NOREPEAT = 0x4000;
    private static final int WM_HOTKEY = 0x0312;
    private static final int WM_QUIT = 0x0012;

    public static class HotkeyEntry {
        public final int id;
        public final int modifiers;
        public final int keyCode;
        public final Runnable action;

        public HotkeyEntry(int id, int modifiers, int keyCode, Runnable action) {
            this.id = id;
            this.modifiers = modifiers;
            this.keyCode = keyCode;
            this.action = action;
        }

        public String getComboKey() {
            return modifiers + ":" + keyCode;
        }
    }

    private final Map<Integer, HotkeyEntry> activeEntries = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastTriggerTime = new AtomicLong(0);
    private Thread loopThread;
    private int currentThreadId = 0;

    public HotkeyManager() {
    }

    /**
     * Updates and starts all configured hotkeys with deduplication and conflict protection.
     */
    public synchronized void setHotkeys(List<HotkeyEntry> entries) {
        stop();

        activeEntries.clear();
        Set<String> seenCombos = new HashSet<>();

        // Add entries without duplicate key combinations
        for (HotkeyEntry entry : entries) {
            if (entry.keyCode == 0) continue;
            String combo = entry.getComboKey();
            if (seenCombos.add(combo)) {
                activeEntries.put(entry.id, entry);
            } else {
                System.out.println("Skipping duplicate hotkey combination: " + combo + " (ID: " + entry.id + ")");
            }
        }

        if (activeEntries.isEmpty()) {
            return;
        }

        this.running.set(true);

        loopThread = new Thread(() -> {
            currentThreadId = com.sun.jna.platform.win32.Kernel32.INSTANCE.GetCurrentThreadId();

            List<Integer> registeredIds = new ArrayList<>();
            for (HotkeyEntry entry : activeEntries.values()) {
                boolean success = User32.INSTANCE.RegisterHotKey(
                        null,
                        entry.id,
                        entry.modifiers | MOD_NOREPEAT,
                        entry.keyCode
                );

                if (!success) {
                    success = User32.INSTANCE.RegisterHotKey(
                            null,
                            entry.id,
                            entry.modifiers,
                            entry.keyCode
                    );
                }

                if (success) {
                    registeredIds.add(entry.id);
                } else {
                    System.err.println("Could not register hotkey ID " + entry.id + " (key=" + entry.keyCode + ", mods=" + entry.modifiers + ") - conflict with another application");
                }
            }

            MSG msg = new MSG();
            while (running.get()) {
                int res = User32.INSTANCE.GetMessage(msg, null, 0, 0);
                if (res <= 0) {
                    break;
                }
                if (msg.message == WM_HOTKEY) {
                    int id = msg.wParam.intValue();
                    HotkeyEntry entry = activeEntries.get(id);
                    if (entry != null && entry.action != null) {
                        // Debounce rapid repeated triggers (200ms minimum interval)
                        long now = System.currentTimeMillis();
                        if (now - lastTriggerTime.get() >= 200) {
                            lastTriggerTime.set(now);
                            new Thread(entry.action, "HotkeyAction-" + id).start();
                        }
                    }
                }
                User32.INSTANCE.TranslateMessage(msg);
                User32.INSTANCE.DispatchMessage(msg);
            }

            for (int id : registeredIds) {
                User32.INSTANCE.UnregisterHotKey(null, id);
            }
        }, "GlobalHotkeyThread");

        loopThread.setDaemon(true);
        loopThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        if (loopThread != null && loopThread.isAlive()) {
            if (currentThreadId != 0) {
                User32.INSTANCE.PostThreadMessage(currentThreadId, WM_QUIT, null, null);
            }
            try {
                loopThread.join(1000);
            } catch (InterruptedException ignored) {
            }
            loopThread = null;
        }
    }
}
