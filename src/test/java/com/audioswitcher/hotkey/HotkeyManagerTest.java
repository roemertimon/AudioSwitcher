package com.audioswitcher.hotkey;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HotkeyManagerTest {

    @Test
    public void testHotkeyEntryComboKey() {
        HotkeyManager.HotkeyEntry entry1 = new HotkeyManager.HotkeyEntry(1, 3, KeyEvent.VK_S, () -> {});
        assertEquals("3:83", entry1.getComboKey());

        HotkeyManager.HotkeyEntry entry2 = new HotkeyManager.HotkeyEntry(101, 3, KeyEvent.VK_1, () -> {});
        assertEquals("3:49", entry2.getComboKey());
    }

    @Test
    public void testSetHotkeysDeduplication() {
        HotkeyManager manager = new HotkeyManager();

        List<HotkeyManager.HotkeyEntry> entries = new ArrayList<>();
        // Duplicate combo (Ctrl + Alt + 1)
        entries.add(new HotkeyManager.HotkeyEntry(101, 3, KeyEvent.VK_1, () -> {}));
        entries.add(new HotkeyManager.HotkeyEntry(102, 3, KeyEvent.VK_1, () -> {})); // duplicate
        entries.add(new HotkeyManager.HotkeyEntry(103, 3, KeyEvent.VK_2, () -> {}));

        assertDoesNotThrow(() -> manager.setHotkeys(entries));
        manager.stop();
    }
}
