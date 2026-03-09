package com.viglet.turing.spring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TurBrowserLauncherTest {

    @Test
    void shouldDetectExecutionFromTestFramework() {
        assertTrue(TurBrowserLauncher.isCalledFromTestFramework());
    }
}
