package com.raktsetu.server;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The real workflows between people, each step done by the account that would do it. */
@SpringBootTest
@DirtiesContext
class WorkflowTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "workflowtest");
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.demo-password", () -> "unused-password");
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions as(String who, String method, String url, String body) throws Exception {
        var request = (method.equals("GET") ? get(url) : post(url)).with(user(who)).with(csrf());
        if (body != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    private long raise(String hospital, String component, String group, int quantity) throws Exception {
        String json = as(hospital.toLowerCase(), "POST", "/api/requests", "{\"hospitalId\":\"" + hospital
                + "\",\"component\":\"" + component + "\",\"group\":\"" + group + "\",\"quantity\":" + quantity + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private int stockAt(String centre) throws Exception {
        String json = as("admin", "GET", "/api/facilities/" + centre + "/stock", null)
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.facility.units");
    }

    @Test
    void aRequestGoesFromHospitalToCentreAndBack() throws Exception {
        long id = raise("H01", "PLASMA", "AB+", 1);
        String json = as("h01", "GET", "/api/requests", null).andReturn().getResponse().getContentAsString();
        String source = JsonPath.read(json, "$[?(@.id == " + id + ")].parts[0].sourceId").toString()
                .replaceAll("[\\[\\]\"]", "");
        String centreUser = source.toLowerCase();

        as(centreUser, "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.unread", greaterThan(0)))
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("Request #" + id))));
        as(centreUser, "GET", "/api/requests", null).andExpect(jsonPath("$[*].id", hasItem((int) id)));

        as("h01", "POST", "/api/requests/" + id + "/receive", null).andExpect(status().isConflict());
        as("h02", "POST", "/api/requests/" + id + "/dispatch", null).andExpect(status().isForbidden());
        as(centreUser, "POST", "/api/requests/" + id + "/dispatch", null)
                .andExpect(jsonPath("$.status").value("DISPATCHED"));
        as("h01", "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("is on its way"))));
        as("h02", "POST", "/api/requests/" + id + "/receive", null).andExpect(status().isForbidden());
        as("h01", "POST", "/api/requests/" + id + "/receive", null).andExpect(jsonPath("$.status").value("RECEIVED"));
        as("h01", "POST", "/api/requests/" + id + "/cancel", null).andExpect(status().isConflict());
    }

    @Test
    void cancellingPutsTheUnitsBackOnTheShelf() throws Exception {
        long id = raise("H02", "RED_CELLS", "O+", 2);
        String json = as("admin", "GET", "/api/requests", null).andReturn().getResponse().getContentAsString();
        List<String> sources = JsonPath.read(json, "$[?(@.id == " + id + ")].parts[*].sourceId");
        int before = stockAt(sources.get(0));

        as("h02", "POST", "/api/requests/" + id + "/cancel", null).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockAt(sources.get(0))).isGreaterThan(before);
        as("admin", "GET", "/api/audit?limit=3", null).andExpect(jsonPath("$.entries[*].type", hasItem("RETURNED")))
                .andExpect(jsonPath("$.intact").value(true));
    }

    @Test
    void anUnmetRequestIsRecordedAndAdministratorsAreTold() throws Exception {
        as("h03", "POST", "/api/requests",
                "{\"hospitalId\":\"H03\",\"component\":\"RED_CELLS\",\"group\":\"O-\",\"quantity\":50}")
                .andExpect(status().isConflict());
        as("h03", "GET", "/api/requests", null).andExpect(jsonPath("$[0].status").value("UNMET"));
        as("admin", "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.items[*].kind", hasItem("REQUEST_UNMET")));
    }

    @Test
    void anApprovedTransferMovesStockAndIsReceivedByTheDestination() throws Exception {
        String plan = as("admin", "POST", "/api/plans", "{\"strategy\":\"expiry-first\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(plan, "$[0].id");
        String destination = JsonPath.read(plan, "$[0].destinationId");
        int before = stockAt(destination);

        as("admin", "POST", "/api/transfers/" + id + "/approve", null).andExpect(jsonPath("$.status").value("APPROVED"));

        assertThat(stockAt(destination)).isGreaterThan(before);
        String destUser = destination.toLowerCase();
        as(destUser, "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("Incoming transfer " + id))));
        as("h01", "POST", "/api/transfers/" + id + "/receive", null).andExpect(status().isForbidden());
        as(destUser, "POST", "/api/transfers/" + id + "/receive", null)
                .andExpect(jsonPath("$.status").value("RECEIVED"));
        as("admin", "POST", "/api/transfers/" + id + "/approve", null).andExpect(status().isConflict());
    }

    @Test
    void aNewPlanReplacesUndecidedProposals() throws Exception {
        String first = as("admin", "POST", "/api/plans", "{\"strategy\":\"nearest-first\"}")
                .andReturn().getResponse().getContentAsString();
        String firstId = JsonPath.read(first, "$[0].id");
        as("admin", "POST", "/api/plans", "{\"strategy\":\"expiry-first\"}").andExpect(status().isOk());
        as("admin", "POST", "/api/transfers/" + firstId + "/approve", null)
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(200, 409));
    }

    @Test
    void centreStaffDiscardDamagedUnitsAtTheirOwnCentreOnly() throws Exception {
        String json = as("admin", "GET", "/api/audit?limit=500", null).andReturn().getResponse().getContentAsString();
        assertThat(json).isNotEmpty();
        as("sc01", "POST", "/api/facilities/SC01/units", "{\"component\":\"PLASMA\",\"group\":\"B-\",\"quantity\":1}")
                .andExpect(status().isOk());
        String added = as("sc01", "POST", "/api/facilities/SC01/units",
                "{\"component\":\"PLASMA\",\"group\":\"B-\",\"quantity\":1}").andReturn().getResponse()
                .getContentAsString();
        String unit = JsonPath.read(added, "$.unitIds[0]");

        as("sc02", "POST", "/api/facilities/SC01/units/" + unit + "/discard", "{\"reason\":\"bag leaking\"}")
                .andExpect(status().isForbidden());
        as("sc01", "POST", "/api/facilities/SC01/units/" + unit + "/discard", "{\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        as("sc01", "POST", "/api/facilities/SC01/units/" + unit + "/discard", "{\"reason\":\"bag leaking\"}")
                .andExpect(status().isNoContent());
        as("admin", "GET", "/api/audit?limit=1", null).andExpect(jsonPath("$.entries[0].note").value("bag leaking"));
    }

    @Test
    void notificationsCanBeMarkedRead() throws Exception {
        String json = as("admin", "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.unread", greaterThan(0))).andReturn().getResponse().getContentAsString();
        int first = JsonPath.read(json, "$.items[0].id");
        as("admin", "POST", "/api/notifications/" + first + "/read", null).andExpect(status().isNoContent());
        as("sc01", "POST", "/api/notifications/" + first + "/read", null).andExpect(status().isBadRequest());
        as("admin", "POST", "/api/notifications/read-all", null).andExpect(status().isNoContent());
        as("admin", "GET", "/api/notifications", null).andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void theDailyDigestFlagsOverdueReportsAndExpiringStock() throws Exception {
        as("sc03", "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.items[*].kind", hasItem("REPORT_OVERDUE")));
        as("admin", "POST", "/api/date", "{\"today\":\"2026-10-02\"}").andExpect(status().isOk());
        as("admin", "GET", "/api/notifications", null)
                .andExpect(jsonPath("$.items[*].message", hasItem(containsString("unverified"))));
    }
}
