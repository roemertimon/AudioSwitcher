package com.audioswitcher.hotkey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GameDetectorTest {

    @Test
    public void testGameDetectorDoesNotCrash() {
        // Must safely execute without throwing native exceptions, even in headless or CI environments
        assertDoesNotThrow(() -> {
            boolean isGame = GameDetector.isGameOrFullscreenActive();
            String name = GameDetector.getForegroundProcessName();
            assertNotNull(name);
            System.out.println("Current active foreground: " + name + " (isGame=" + isGame + ")");
        });
    }
}
