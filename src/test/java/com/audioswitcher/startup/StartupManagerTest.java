package com.audioswitcher.startup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class StartupManagerTest {

    @Test
    public void testGetApplicationLaunchCommand() {
        String cmd = StartupManager.getApplicationLaunchCommand();
        assertNotNull(cmd, "Launch command should not be null");
        assertFalse(cmd.isBlank(), "Launch command should not be blank");
        assertTrue(cmd.contains("--minimized"), "Launch command must include --minimized flag to avoid popping up on Windows startup");
        System.out.println("Generated Windows Startup command: " + cmd);
    }
}
