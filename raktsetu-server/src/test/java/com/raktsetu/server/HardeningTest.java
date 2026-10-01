package com.raktsetu.server;

import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.monitoring.AuditChainHealthIndicator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Status;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in lockout, health checks, metrics and the API documentation. */
@SpringBootTest
@DirtiesContext
class HardeningTest {

    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-hardening-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "hardeningtest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    AuditRepository audit;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Clock clock;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void signIn(String username, String password, String address, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf()).with(r -> {
                    r.setRemoteAddr(address);
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void fiveWrongPasswordsLockTheAccountUntilAnAdminResetsIt() throws Exception {
        for (int i = 0; i < 5; i++) {
            signIn("sc01", "wrong-guess-" + i, "10.0.0.1", 401);
        }
        // Even the right password is refused now, from any address, and the client is told when to retry.
        mvc.perform(post("/api/auth/login").with(csrf()).with(r -> {
                    r.setRemoteAddr("10.0.0.2");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"SC01\",\"password\":\"" + TestFixtures.PASSWORD + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "900"))
                .andExpect(jsonPath("$.error").value("TooManyAttempts"));

        // Other accounts are unaffected.
        signIn("sc02", TestFixtures.PASSWORD, "10.0.0.1", 200);

        // An administrator's reset clears the lock; the person signs in with the one-time password.
        String reset = mvc.perform(post("/api/users/sc01/reset-password").with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String oneTime = reset.replaceAll(".*\"oneTimePassword\":\"([^\"]+)\".*", "$1");
        signIn("sc01", oneTime, "10.0.0.2", 200);
    }

    @Test
    void oneAddressTryingManyAccountsIsLocked() throws Exception {
        for (int i = 0; i < 20; i++) {
            signIn("nobody" + i, "guess", "10.9.9.9", 401);
        }
        signIn("admin", TestFixtures.PASSWORD, "10.9.9.9", 429);
        signIn("admin", TestFixtures.PASSWORD, "10.9.9.10", 200);
    }

    @Test
    void healthIsPublicButItsDetailsAreForAdmins() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
        mvc.perform(get("/actuator/health").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$.components.auditChain.status").value("UP"))
                .andExpect(jsonPath("$.components.auditChain.details.message",
                        containsString("Audit log intact")));
    }

    @Test
    void metricsAreForAdminsOnly() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").with(user("h01").roles("HOSPITAL_STAFF")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics/raktsetu.units.in_stock").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").isNumber());
        mvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("raktsetu_units_at_risk")))
                .andExpect(content().string(containsString("raktsetu_logins_failed_total")));
    }

    @Test
    void theApiIsDocumented() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("RaktSetu API"))
                .andExpect(jsonPath("$.paths['/api/requests']").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists());
    }

    @Test
    void tamperingWithTheAuditLogMakesTheServiceUnhealthy() {
        var indicator = new AuditChainHealthIndicator(audit, clock);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        String original = jdbc.sql("select note from audit_log where seq = 1").query(String.class).optional()
                .orElse(null);
        jdbc.sql("update audit_log set note = 'edited later' where seq = 1").update();
        try {
            var fresh = new AuditChainHealthIndicator(audit, clock);
            assertThat(fresh.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(fresh.health().getDetails().get("message").toString()).contains("TAMPERED");
            // A result is reused within the minute: the first indicator has not looked again yet.
            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        } finally {
            jdbc.sql("update audit_log set note = ? where seq = 1").param(original).update();
        }
    }
}
