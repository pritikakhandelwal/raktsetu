package com.raktsetu.server.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Accounts in the {@code app_user} table. */
@Repository
public class UserRepository {

    /** An account with its password hash, for signing in. */
    public record Account(AppUser user, String passwordHash, boolean enabled) {
    }

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int count() {
        return jdbc.sql("select count(*) from app_user").query(Integer.class).single();
    }

    public Optional<Account> find(String username) {
        return jdbc.sql("select * from app_user where username = ?").param(username)
                .query((rs, n) -> new Account(new AppUser(rs.getString("username"), rs.getString("display_name"),
                        Role.valueOf(rs.getString("role")), rs.getString("facility_id")),
                        rs.getString("password_hash"), rs.getBoolean("enabled")))
                .optional();
    }

    public List<AppUser> all() {
        return jdbc.sql("select username, display_name, role, facility_id from app_user order by role, username")
                .query((rs, n) -> new AppUser(rs.getString("username"), rs.getString("display_name"),
                        Role.valueOf(rs.getString("role")), rs.getString("facility_id")))
                .list();
    }

    public void insert(AppUser user, String passwordHash) {
        jdbc.sql("insert into app_user (username, display_name, password_hash, role, facility_id, enabled) "
                        + "values (?, ?, ?, ?, ?, ?)")
                .params(user.username(), user.displayName(), passwordHash, user.role().name(), user.facilityId(), true)
                .update();
    }
}
