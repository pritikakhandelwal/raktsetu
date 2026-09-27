package com.raktsetu.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * RaktSetu: expiry-aware blood redistribution for a network of blood centres and hospitals. A REST API over the
 * engine, with the single-page website served from the same server.
 *
 * <pre>
 * java -jar raktsetu-server.jar
 * </pre>
 * Then open http://localhost:8080; the first visit creates the first administrator.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class RaktSetuServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaktSetuServerApplication.class, args);
    }

    /** The real clock in the network's time zone (pinned only when a test sets {@code raktsetu.today}). */
    @Bean
    Clock clock(RaktSetuProperties properties) {
        if (properties.today() != null) {
            return Clock.fixed(properties.today().atStartOfDay(properties.zone()).toInstant(), properties.zone());
        }
        return Clock.system(properties.zone());
    }
}
