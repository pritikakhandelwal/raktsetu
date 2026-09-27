package com.raktsetu.server;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the whole server (with the sample network in a temporary folder) and checks every endpoint over HTTP.
 * Tests share one application; the few that change state are ordered after the read-only ones.
 */
@SpringBootTest
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiTest {

    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-api-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("raktsetu.data-dir", DATA::toString);
        TestDatabase.register(registry, "apitest");
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.seed", () -> "42");
    }

    @Autowired
    org.springframework.web.context.WebApplicationContext context;

    MockMvc mvc;

    /** Every request is made as the administrator, with a valid CSRF token; SecurityTest covers the other roles. */
    @org.junit.jupiter.api.BeforeEach
    void signInAsAdmin() {
        mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .defaultRequest(get("/").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.csrf()))
                .build();
    }

    private ResultActions postJson(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    @Order(1)
    void summaryDescribesTheSampleNetwork() throws Exception {
        mvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-01"))
                .andExpect(jsonPath("$.storingFacilities").value(5))
                .andExpect(jsonPath("$.hospitals").value(6))
                .andExpect(jsonPath("$.unitsWasted").value(3))
                .andExpect(jsonPath("$.auditIntact").value(true));
    }

    @Test
    @Order(1)
    void facilitiesAndStockAreListed() throws Exception {
        mvc.perform(get("/api/facilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(11)))
                .andExpect(jsonPath("$[3].freshness").value("UNVERIFIED"));
        mvc.perform(get("/api/facilities/SC04/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facility.name", containsString("Baramati")))
                .andExpect(jsonPath("$.lines[*].urgent", hasItem(true)));
        mvc.perform(get("/api/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups", hasSize(8)))
                .andExpect(jsonPath("$.rows", hasSize(4)));
        mvc.perform(get("/api/wastage")).andExpect(jsonPath("$.byComponent.PLATELETS").value(3));
    }

    @Test
    @Order(1)
    void compatibilityFollowsThePlasmaReversal() throws Exception {
        postJson("/api/compatibility", "{\"component\":\"PLASMA\",\"donor\":\"AB+\",\"recipient\":\"O-\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.level").value("COMPATIBLE"));
        postJson("/api/compatibility", "{\"component\":\"RED_CELLS\",\"donor\":\"AB+\",\"recipient\":\"O-\"}")
                .andExpect(jsonPath("$.allowed").value(false));
    }

    @Test
    @Order(1)
    void risksStrategiesAndComparisonAreAvailable() throws Exception {
        mvc.perform(get("/api/risks")).andExpect(status().isOk()).andExpect(jsonPath("$.length()", greaterThan(0)));
        mvc.perform(get("/api/strategies")).andExpect(jsonPath("$[*].name", hasItem("expiry-first")));
        mvc.perform(get("/api/strategies/compare")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @Order(1)
    void errorsAreReadable() throws Exception {
        mvc.perform(get("/api/facilities/NOWHERE/stock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No facility with ID 'NOWHERE'"));
        postJson("/api/requests", "{\"hospitalId\":\"H01\",\"component\":\"RED_CELLS\",\"group\":\"Q+\",\"quantity\":1}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unknown blood group")));
        postJson("/api/requests", "{\"hospitalId\":\"H01\",\"component\":\"RED_CELLS\",\"group\":\"O-\",\"quantity\":50}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("InsufficientStockException"));
        postJson("/api/requests", "not json").andExpect(status().isBadRequest());
        postJson("/api/plans", "{\"strategy\":\"random\"}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers/T0/approve")).andExpect(status().isConflict());
    }

    @Test
    @Order(2)
    void requestsAreServedAndAudited() throws Exception {
        postJson("/api/requests",
                "{\"hospitalId\":\"H01\",\"component\":\"RED_CELLS\",\"group\":\"O+\",\"quantity\":2,\"urgency\":\"EMERGENCY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALLOCATED"))
                .andExpect(jsonPath("$.parts[0].unitIds[0]", startsWith("RS-")));
        mvc.perform(get("/api/audit?limit=5"))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.entries[0].type").value("ISSUED"));
    }

    @Test
    @Order(3)
    void plansCanBeApprovedAndRejected() throws Exception {
        postJson("/api/plans", "{\"strategy\":\"expiry-first\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(1)));
        mvc.perform(post("/api/transfers/T20261001-01/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/transfers/T20261001-02/reject"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/transfers/T20261001-02/reject")).andExpect(status().isConflict());
        mvc.perform(get("/api/transfers")).andExpect(jsonPath("$[*].status", org.hamcrest.Matchers.hasItems("APPROVED",
                "REJECTED")));
    }

    @Test
    @Order(4)
    void unitsCanBeAddedAndReportsRecorded() throws Exception {
        postJson("/api/facilities/SC01/units", "{\"component\":\"PLATELETS\",\"group\":\"AB-\",\"quantity\":2}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitIds", hasSize(2)));
        mvc.perform(post("/api/facilities/SC03/report"))
                .andExpect(jsonPath("$.freshness").value("FRESH"));
        postJson("/api/facilities/H01/units", "{\"component\":\"PLATELETS\",\"group\":\"AB-\",\"quantity\":1}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    void simulationComparesThreePolicies() throws Exception {
        postJson("/api/simulations", "{\"days\":5}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results", hasSize(3)))
                .andExpect(jsonPath("$.results[0].policy").value("no redistribution"));
        postJson("/api/simulations", "{\"days\":0}").andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void dateOnlyMovesForward() throws Exception {
        postJson("/api/date", "{\"today\":\"2026-09-01\"}").andExpect(status().isBadRequest());
        postJson("/api/date", "{\"today\":\"2026-10-03\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-10-03"));
        mvc.perform(get("/api/summary")).andExpect(jsonPath("$.today").value("2026-10-03"));
    }

    @Autowired
    org.springframework.jdbc.core.simple.JdbcClient jdbc;

    @Test
    @Order(7)
    void everyChangeWasWrittenToTheDatabase() {
        int units = jdbc.sql("select count(*) from unit").query(Integer.class).single();
        int audit = jdbc.sql("select count(*) from audit_log").query(Integer.class).single();
        String today = jdbc.sql("select state_value from app_state where state_key = 'today'").query(String.class).single();
        org.assertj.core.api.Assertions.assertThat(units).isPositive();
        org.assertj.core.api.Assertions.assertThat(audit).isGreaterThan(3);
        org.assertj.core.api.Assertions.assertThat(today).isEqualTo("2026-10-03");
    }
}
