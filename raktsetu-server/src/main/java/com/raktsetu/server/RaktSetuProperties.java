package com.raktsetu.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Settings, from {@code application.properties}, environment variables or the command line.
 *
 * @param dataDir    folder for the embedded database
 * @param timeZone   the network's time zone; "today" and the midnight jobs follow it (default Asia/Kolkata)
 * @param routingUrl OSRM routing service used to work out road routes between facilities; blank means offline
 *                   estimates only
 * @param today      for automated tests only: pins the date instead of using the real clock
 */
@ConfigurationProperties("raktsetu")
public record RaktSetuProperties(Path dataDir, String timeZone, String routingUrl, LocalDate today) {

    public RaktSetuProperties {
        dataDir = dataDir == null ? Path.of("raktsetu-data") : dataDir;
        timeZone = timeZone == null || timeZone.isBlank() ? "Asia/Kolkata" : timeZone;
        routingUrl = routingUrl == null ? "https://router.project-osrm.org" : routingUrl.trim();
    }

    public ZoneId zone() {
        return ZoneId.of(timeZone);
    }
}
