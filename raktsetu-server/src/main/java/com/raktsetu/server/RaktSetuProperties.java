package com.raktsetu.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.LocalDate;

/**
 * Settings, from {@code application.properties} or the command line ({@code --raktsetu.today=2026-10-01}).
 *
 * @param dataDir folder holding the network's data files; a sample network is created there if it is empty
 * @param today   the date the app treats as today; the system date when not set
 * @param seed    seed for the sample network and the simulation
 */
@ConfigurationProperties("raktsetu")
public record RaktSetuProperties(Path dataDir, LocalDate today, Long seed) {

    public RaktSetuProperties {
        dataDir = dataDir == null ? Path.of("raktsetu-data") : dataDir;
        seed = seed == null ? 42L : seed;
    }

    /** The configured date, or the system date. This is the only place the server reads the real clock. */
    public LocalDate startDate() {
        return today != null ? today : LocalDate.now();
    }
}
