package com.raktsetu.server;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Chooses the database for a test class: an in-memory H2 database by default, or PostgreSQL when
 * {@code RAKTSETU_TEST_DB_URL} is set (CI runs the tests both ways). Each test class gets its own database or schema,
 * so classes never see each other's data.
 */
final class TestDatabase {

    private TestDatabase() {
    }

    static void register(DynamicPropertyRegistry registry, String name) {
        // Tests send queued alerts themselves (AlertDispatcher.deliverDue), so the background pass must not race them.
        registry.add("raktsetu.alerts.poll-ms", () -> "3600000");
        String postgres = System.getenv("RAKTSETU_TEST_DB_URL");
        if (postgres == null || postgres.isBlank()) {
            registry.add("spring.datasource.url",
                    () -> "jdbc:h2:mem:" + name + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            return;
        }
        registry.add("spring.datasource.url", () -> postgres + (postgres.contains("?") ? "&" : "?")
                + "currentSchema=" + name);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("RAKTSETU_TEST_DB_USER",
                "raktsetu"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("RAKTSETU_TEST_DB_PASSWORD",
                "raktsetu"));
        registry.add("spring.flyway.schemas", () -> name);
        registry.add("spring.flyway.default-schema", () -> name);
    }
}
