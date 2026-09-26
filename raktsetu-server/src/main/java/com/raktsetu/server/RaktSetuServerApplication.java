package com.raktsetu.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Web version of RaktSetu: a REST API over the same engine as the console, plus the single-page front end.
 *
 * <pre>
 * java -jar raktsetu-server.jar --raktsetu.today=2026-10-01 --raktsetu.data-dir=raktsetu-data
 * </pre>
 * Then open http://localhost:8080.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RaktSetuServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RaktSetuServerApplication.class, args);
    }
}
