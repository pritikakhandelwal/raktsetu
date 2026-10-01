package com.raktsetu.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The embedded database in an absolute data folder, as the Windows app uses ({@code ${user.home}/RaktSetu}). Uses the
 * real database address from application.properties: no test override.
 */
@SpringBootTest
@DirtiesContext
class DataDirectoryTest {

    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu absolute data").toAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
        registry.add("raktsetu.alerts.poll-ms", () -> "3600000");
    }

    @Autowired
    Environment environment;

    @Autowired
    NetworkService network;

    @Test
    void theDatabaseLivesInTheAbsoluteFolder() throws IOException {
        assertThat(network.facilities()).isEmpty();
        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:file:" + DATA.toString().replace('\\', '/') + "/raktsetu;");
        try (var files = Files.list(DATA)) {
            assertThat(files.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("raktsetu."));
        }
    }

    @Test
    void relativeFoldersStillResolveFromTheWorkingDirectory() {
        String resolved = DataDirectory.absolute("raktsetu-data");
        assertThat(Path.of(resolved)).isAbsolute();
        assertThat(resolved).endsWith("/raktsetu-data").doesNotContain("\\");
        assertThat(DataDirectory.absolute("a/../b")).endsWith("/b");
        String home = Path.of(System.getProperty("user.home")).toAbsolutePath().toString().replace('\\', '/');
        assertThat(DataDirectory.absolute("~/RaktSetu/data")).isEqualTo(home + "/RaktSetu/data");
        assertThat(DataDirectory.absolute("~")).isEqualTo(home);
    }

    @Test
    void theBrowserIsOpenedWithEachSystemsOwnCommand() {
        assertThat(BrowserOpener.command("Windows 11", "http://localhost:8080"))
                .isEqualTo(List.of("rundll32", "url.dll,FileProtocolHandler", "http://localhost:8080"));
        assertThat(BrowserOpener.command("Mac OS X", "u")).isEqualTo(List.of("open", "u"));
        assertThat(BrowserOpener.command("Linux", "u")).isEqualTo(List.of("xdg-open", "u"));
        new BrowserOpener(false, 8080).open(); // disabled: logs the address and opens nothing
    }
}
