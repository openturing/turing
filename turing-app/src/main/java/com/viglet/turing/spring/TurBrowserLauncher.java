package com.viglet.turing.spring;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Profile("!test")
@Slf4j
@Component
public class TurBrowserLauncher {
    @Value("${turing.open-browser:true}")
    private boolean openBrowser;
    @Value("${turing.url:'http://localhost:2700'}")
    private String turingUrl;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        if (!openBrowser || isCalledFromTestFramework()) {
            return;
        }
        // Skip in headless environments (e.g. Docker, CI, servers without a display).
        // Touching AWT/Desktop there throws UnsatisfiedLinkError when libX11 is absent,
        // so guard with the headless flag and swallow any native/runtime failure rather
        // than letting it abort application startup.
        if (GraphicsEnvironment.isHeadless()) {
            log.info("Headless environment detected, skipping automatic browser launch.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(turingUrl));
            } else {
                log.info("Desktop is not supported, cannot open browser automatically.");
            }
        } catch (Throwable e) {
            log.warn("Could not open browser automatically: {}", e.getMessage());
        }
    }

    public static boolean isCalledFromTestFramework() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            if (element.getClassName().contains("org.junit")
                    || element.getClassName().contains("org.testng")) {
                return true;
            }
        }
        return false;
    }

}
