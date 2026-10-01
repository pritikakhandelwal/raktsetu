package com.raktsetu.server;

import com.raktsetu.server.alerts.AlertOutbox;
import com.raktsetu.server.privacy.PrivacyService;
import com.raktsetu.server.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Data protection duties (DPDP Act, 2023) and the browser security headers. */
@SpringBootTest
@DirtiesContext
class PrivacyTest {

    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-privacy-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "privacytest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
        registry.add("raktsetu.privacy.organisation", () -> "Pune District Blood Network");
        registry.add("raktsetu.privacy.contact", () -> "privacy@example.org");
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    PrivacyService privacy;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Clock clock;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("delete from alert_outbox").update();
    }

    private void alert(String username, String phone, String status, long createdAt) {
        jdbc.sql("insert into alert_outbox (username, phone, channel, message, status, attempts, next_attempt_at, "
                        + "created_at) values (?, ?, 'LOG', 'RaktSetu: test', ?, 0, ?, ?)")
                .params(username, phone, status, createdAt, createdAt).update();
    }

    private void contact(String username, String body) throws Exception {
        mvc.perform(put("/api/auth/contact").with(user(username).roles("HOSPITAL_STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void anyoneCanReadThePrivacyNoticeBeforeSigningIn() throws Exception {
        mvc.perform(get("/api/privacy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organisation").value("Pune District Blood Network"))
                .andExpect(jsonPath("$.contact").value("privacy@example.org"))
                .andExpect(jsonPath("$.noticeVersion").value("2026-10-01"))
                .andExpect(jsonPath("$.alertRetentionDays").value(90));
    }

    @Test
    void everyResponseCarriesTheBrowserSecurityHeaders() throws Exception {
        mvc.perform(get("/api/privacy"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", containsString("camera=()")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void turningAlertsOnRecordsConsentAndTurningThemOffErasesTheNumber() throws Exception {
        contact("h02", "{\"phone\":\"98765 43210\",\"alertsEnabled\":true}");
        UserRepository.ContactRecord record = users.contactRecord("h02").orElseThrow();
        assertThat(record.basis()).isEqualTo("CONSENT");
        assertThat(record.noticeVersion()).isEqualTo("2026-10-01");
        assertThat(record.recordedAt()).isNotNull();

        alert("h02", "+919876543210", "SENT", clock.millis());
        alert("h02", "+919876543210", "PENDING", clock.millis());

        contact("h02", "{\"phone\":\"98765 43210\",\"alertsEnabled\":false}");
        UserRepository.Account account = users.find("h02").orElseThrow();
        assertThat(account.phone()).isNull();
        assertThat(account.alertsEnabled()).isFalse();
        assertThat(users.contactRecord("h02").orElseThrow().basis()).isNull();
        assertThat(jdbc.sql("select phone from alert_outbox where username = 'h02'").query(String.class).list())
                .containsOnly(AlertOutbox.REMOVED);
        assertThat(jdbc.sql("select status from alert_outbox where username = 'h02'").query(String.class).list())
                .containsOnly("SENT", "FAILED");
    }

    @Test
    void eachPersonCanDownloadACopyOfTheirOwnData() throws Exception {
        contact("h03", "{\"phone\":\"+919812345678\",\"alertsEnabled\":true}");
        alert("h03", "+919812345678", "SENT", clock.millis());
        alert("h04", "+919800000000", "SENT", clock.millis());

        mvc.perform(get("/api/auth/my-data").with(user("h03").roles("HOSPITAL_STAFF")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(jsonPath("$.username").value("h03"))
                .andExpect(jsonPath("$.displayName").value("Staff H03"))
                .andExpect(jsonPath("$.phone").value("+919812345678"))
                .andExpect(jsonPath("$.phoneHeldBecause", containsString("agreed")))
                .andExpect(jsonPath("$.alerts.length()").value(1))
                .andExpect(jsonPath("$.alerts[0].phone").value("+91******5678"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/auth/my-data")).andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsCanEraseAFormerStaffMembersDetails() throws Exception {
        contact("h05", "{\"phone\":\"+919812345679\",\"alertsEnabled\":true}");
        alert("h05", "+919812345679", "SENT", clock.millis());
        var admin = user("admin").roles("ADMIN");

        mvc.perform(post("/api/users/h05/anonymise").with(admin).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Disable the account before erasing the person's details"));
        mvc.perform(post("/api/users/h05/anonymise").with(user("h06").roles("HOSPITAL_STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/admin/anonymise").with(admin).with(csrf()))
                .andExpect(status().isBadRequest());

        users.setEnabled("h05", false);
        mvc.perform(post("/api/users/h05/anonymise").with(admin).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("h05"))
                .andExpect(jsonPath("$.displayName").value("Former staff member"))
                .andExpect(jsonPath("$.phone").isEmpty());
        assertThat(users.contactRecord("h05").orElseThrow().anonymisedOn()).isEqualTo(TestFixtures.DAY);
        assertThat(jdbc.sql("select phone from alert_outbox where username = 'h05'").query(String.class).list())
                .containsOnly(AlertOutbox.REMOVED);
    }

    @Test
    void oldAlertRecordsAreDeletedAfterTheRetentionPeriod() {
        long now = clock.millis();
        long old = now - Duration.ofDays(91).toMillis();
        alert("h01", "+919812345670", "SENT", old);
        alert("h01", "+919812345670", "FAILED", old);
        alert("h01", "+919812345670", "PENDING", old);
        alert("h01", "+919812345670", "SENT", now);

        assertThat(privacy.purgeOldAlerts()).isEqualTo(2);
        List<String> left = jdbc.sql("select status from alert_outbox order by id").query(String.class).list();
        assertThat(left).containsExactly("PENDING", "SENT");
    }
}
