package com.raktsetu.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Opens RaktSetu in the default browser once the server is ready, when {@code raktsetu.open-browser} is true (the
 * Windows app turns it on, so a double-click is all it takes). Uses the operating system's own "open this address"
 * command rather than java.awt, which a server runs without.
 */
@Component
public class BrowserOpener {

    private static final Logger LOG = LoggerFactory.getLogger(BrowserOpener.class);

    private final boolean enabled;
    private final int port;

    public BrowserOpener(@Value("${raktsetu.open-browser:false}") boolean enabled,
                         @Value("${server.port:8080}") int port) {
        this.enabled = enabled;
        this.port = port;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void open() {
        String url = "http://localhost:" + port;
        LOG.info("RaktSetu is running at {}. Close this window to stop it.", url);
        if (!enabled) {
            return;
        }
        try {
            new ProcessBuilder(command(System.getProperty("os.name", ""), url)).start();
        } catch (IOException | RuntimeException e) {
            LOG.warn("Could not open a browser ({}); open {} yourself", e.getMessage(), url);
        }
    }

    /** The command that opens {@code url} in the default browser on this operating system. */
    static List<String> command(String osName, String url) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of("rundll32", "url.dll,FileProtocolHandler", url);
        }
        if (os.contains("mac")) {
            return List.of("open", url);
        }
        return List.of("xdg-open", url);
    }
}
