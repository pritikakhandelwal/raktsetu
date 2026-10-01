package com.raktsetu.server.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Accounts in the {@code app_user} table. */
@Repository
public class UserRepository {

    /** An account with its password hash and status. */
    public record Account(AppUser user, String passwordHash, boolean enabled, boolean mustChangePassword,
                          LocalDate createdOn) {
    }

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public int count() {
        return jdbc.sql("select count(*) from app_user").query(Integer.class).single();
    }

    public int enabledAdmins() {
        return jdbc.sql("select count(*) from app_user where role = 'ADMIN' and enabled = true").query(Integer.class)
                .single();
    }

    public Optional<Account> find(String username) {
        return jdbc.sql("select * from app_user where username = ?").param(username)
                .query(UserRepository::account).optional();
    }

    public List<Account> all() {
        return jdbc.sql("select * from app_user order by role, facility_id, username")
                .query(UserRepository::account).list();
    }

    public void insert(AppUser user, String passwordHash, boolean mustChangePassword, LocalDate createdOn) {
        jdbc.sql("insert into app_user (username, display_name, password_hash, role, facility_id, enabled, "
                        + "must_change_password, created_on) values (?, ?, ?, ?, ?, ?, ?, ?)")
                .params(user.username(), user.displayName(), passwordHash, user.role().name(), user.facilityId(), true,
                        mustChangePassword, createdOn)
                .update();
    }

    public void setPassword(String username, String passwordHash, boolean mustChangePassword) {
        jdbc.sql("update app_user set password_hash = ?, must_change_password = ? where username = ?")
                .params(passwordHash, mustChangePassword, username).update();
    }

    public void setEnabled(String username, boolean enabled) {
        jdbc.sql("update app_user set enabled = ? where username = ?").params(enabled, username).update();
    }

    private static Account account(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Account(new AppUser(rs.getString("username"), rs.getString("display_name"),
                Role.valueOf(rs.getString("role")), rs.getString("facility_id")), rs.getString("password_hash"),
                rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
                rs.getObject("created_on", LocalDate.class));
    }
}
