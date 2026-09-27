package com.raktsetu.server.db;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Small named values the application keeps between restarts, such as the next unit serial and the demo date. */
@Repository
public class StateRepository {

    public static final String NEXT_UNIT_SERIAL = "next_unit_serial";
    public static final String TODAY = "today";
    public static final String LAST_DIGEST = "last_digest";

    private final JdbcClient jdbc;

    public StateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> get(String name) {
        return jdbc.sql("select state_value from app_state where state_key = ?").param(name).query(String.class).optional();
    }

    public void put(String name, String value) {
        int updated = jdbc.sql("update app_state set state_value = ? where state_key = ?").params(value, name).update();
        if (updated == 0) {
            jdbc.sql("insert into app_state (state_key, state_value) values (?, ?)").params(name, value).update();
        }
    }
}
