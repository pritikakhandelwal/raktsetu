package com.raktsetu.server.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Accounts in the {@code app_user} table. */
@Repository
public class UserRepository {

    /**
     * An account with its password hash and status.
     *
     * @param phone         mobile number for urgent alerts (E.164), or null
     * @param alertsEnabled whether the person wants urgent alerts on that number
     * @param anonymisedOn  when a former member of staff's details were erased, or null
     */
    public record Account(AppUser user, String passwordHash, boolean enabled, boolean mustChangePassword,
                          LocalDate createdOn, String phone, boolean alertsEnabled, LocalDate anonymisedOn) {
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

    public void setContact(String username, String phone, boolean alertsEnabled) {
        jdbc.sql("update app_user set phone = ?, alerts_enabled = ? where username = ?")
                .params(phone, alertsEnabled && phone != null, username).update();
    }

    /**
     * Why a mobile number is held and since when (see V6__privacy.sql). Clears the record when there is no number.
     *
     * @param basis CONSENT or EMPLOYER
     */
    public void recordContactBasis(String username, String basis, long recordedAt, String noticeVersion) {
        jdbc.sql("update app_user set contact_basis = case when phone is null then null else ? end, "
                        + "contact_recorded_at = case when phone is null then null else ? end, "
                        + "notice_version = case when phone is null then null else ? end where username = ?")
                .params(basis, recordedAt, noticeVersion, username).update();
    }

    /** What is recorded about why someone's number is held, for their own copy of their data. */
    public record ContactRecord(String basis, Long recordedAt, String noticeVersion, LocalDate anonymisedOn) {
    }

    public Optional<ContactRecord> contactRecord(String username) {
        return jdbc.sql("select contact_basis, contact_recorded_at, notice_version, anonymised_on from app_user "
                        + "where username = ?").param(username)
                .query((rs, n) -> {
                    long at = rs.getLong("contact_recorded_at");
                    return new ContactRecord(rs.getString("contact_basis"), rs.wasNull() ? null : at,
                            rs.getString("notice_version"), rs.getObject("anonymised_on", LocalDate.class));
                })
                .optional();
    }

    /** Erases a former member of staff's name and number; the username stays for the audit log. */
    public void anonymise(String username, LocalDate on) {
        jdbc.sql("update app_user set display_name = 'Former staff member', phone = null, alerts_enabled = false, "
                        + "contact_basis = null, contact_recorded_at = null, notice_version = null, enabled = false, "
                        + "anonymised_on = ? where username = ?")
                .params(on, username).update();
    }

    public void setEnabled(String username, boolean enabled) {
        jdbc.sql("update app_user set enabled = ? where username = ?").params(enabled, username).update();
    }

    private static Account account(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Account(new AppUser(rs.getString("username"), rs.getString("display_name"),
                Role.valueOf(rs.getString("role")), rs.getString("facility_id")), rs.getString("password_hash"),
                rs.getBoolean("enabled"), rs.getBoolean("must_change_password"),
                rs.getObject("created_on", LocalDate.class), rs.getString("phone"), rs.getBoolean("alerts_enabled"),
                rs.getObject("anonymised_on", LocalDate.class));
    }
}
