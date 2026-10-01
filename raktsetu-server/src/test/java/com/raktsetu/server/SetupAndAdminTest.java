package com.raktsetu.server;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A brand-new installation, used the way a real organisation would: first-run setup, adding facilities, creating
 * accounts with one-time passwords, the forced password change, resets and disabling. Tests run in order on one
 * database, each step building on the last.
 */
@SpringBootTest
@DirtiesContext
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupAndAdminTest {

    private static final String ADMIN_PASSWORD = "first-admin-2026";
    private static String oneTimePassword;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "setuptest");
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions json(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r,
                               String body, MockHttpSession session) throws Exception {
        r = r.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(session == null ? r : r.session(session));
    }

    private MockHttpSession signIn(String user, String password) throws Exception {
        return (MockHttpSession) json(post("/api/auth/login"),
                "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}", null)
                .andExpect(status().isOk()).andReturn().getRequest().getSession();
    }

    @Test
    @Order(1)
    void aNewInstallationStartsEmptyAndAsksForSetup() throws Exception {
        mvc.perform(get("/api/setup")).andExpect(jsonPath("$.needsSetup").value(true));
        json(post("/api/setup"), "{\"username\":\"Asha\",\"displayName\":\"Asha Rao\",\"password\":\"short\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose a password of at least 10 characters"));
        json(post("/api/setup"), "{\"username\":\"Asha\",\"displayName\":\"Asha Rao\",\"password\":\""
                + ADMIN_PASSWORD + "\"}", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("asha"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/setup")).andExpect(jsonPath("$.needsSetup").value(false));
        json(post("/api/setup"), "{\"username\":\"mallory\",\"displayName\":\"M\",\"password\":\"another-pass-1\"}",
                null).andExpect(status().isForbidden());

        MockHttpSession admin = signIn("asha", ADMIN_PASSWORD);
        mvc.perform(get("/api/facilities").session(admin)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/summary").session(admin)).andExpect(jsonPath("$.unitsInStock").value(0));
    }

    @Test
    @Order(2)
    void administratorsAddFacilitiesAndRoutesAreWorkedOut() throws Exception {
        MockHttpSession admin = signIn("asha", ADMIN_PASSWORD);
        json(post("/api/facilities"), "{\"name\":\"Regional Blood Centre\",\"type\":\"HUB\",\"latitude\":18.5204,"
                + "\"longitude\":73.8567,\"address\":\"Pune\"}", admin)
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("HUB01"));
        json(post("/api/facilities"), "{\"name\":\"District Hospital\",\"type\":\"HOSPITAL\",\"latitude\":18.1515,"
                + "\"longitude\":74.5815}", admin)
                .andExpect(jsonPath("$.id").value("H01"));
        json(post("/api/facilities"), "{\"name\":\"\",\"type\":\"HOSPITAL\",\"latitude\":18.1,\"longitude\":74.5}",
                admin).andExpect(status().isBadRequest());
        json(post("/api/facilities"), "{\"name\":\"X\",\"type\":\"HOSPITAL\",\"latitude\":118.1,\"longitude\":74.5}",
                admin).andExpect(status().isBadRequest());

        mvc.perform(get("/api/facilities/HUB01/routes").session(admin))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].toId").value("H01"))
                .andExpect(jsonPath("$[0].source").value("ESTIMATE"));
        json(put("/api/routes/HUB01/H01"), "{\"distanceKm\":104,\"travelMinutes\":126}", admin)
                .andExpect(jsonPath("$.source").value("MANUAL"));
        mvc.perform(get("/api/facilities/H01/routes").session(admin))
                .andExpect(jsonPath("$[0].travelMinutes").value(126));
        json(put("/api/facilities/H01"), "{\"name\":\"District Hospital Baramati\",\"latitude\":18.1515,"
                + "\"longitude\":74.5815}", admin)
                .andExpect(jsonPath("$.name").value("District Hospital Baramati"));
        mvc.perform(get("/api/facilities/H01/routes").session(admin))
                .andExpect(jsonPath("$[0].travelMinutes").value(126)); // same place: manual route kept
    }

    @Test
    @Order(3)
    void newStaffGetAOneTimePasswordAndMustChangeIt() throws Exception {
        MockHttpSession admin = signIn("asha", ADMIN_PASSWORD);
        json(post("/api/users"), "{\"username\":\"ravi\",\"displayName\":\"Ravi\",\"role\":\"HOSPITAL_STAFF\","
                + "\"facilityId\":\"HUB01\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hospital staff must belong to a hospital"));
        String created = json(post("/api/users"), "{\"username\":\"ravi\",\"displayName\":\"Ravi Kumar\","
                + "\"role\":\"HOSPITAL_STAFF\",\"facilityId\":\"H01\"}", admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();
        oneTimePassword = JsonPath.read(created, "$.oneTimePassword");
        json(post("/api/users"), "{\"username\":\"ravi\",\"displayName\":\"Again\",\"role\":\"ADMIN\"}", admin)
                .andExpect(status().isBadRequest());

        MockHttpSession ravi = signIn("ravi", oneTimePassword);
        mvc.perform(get("/api/auth/me").session(ravi)).andExpect(jsonPath("$.mustChangePassword").value(true));
        mvc.perform(get("/api/summary").session(ravi))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PasswordChangeRequired"));
        json(post("/api/auth/password"), "{\"currentPassword\":\"wrong\",\"newPassword\":\"ravi-password-9\"}", ravi)
                .andExpect(status().isBadRequest());
        json(post("/api/auth/password"), "{\"currentPassword\":\"" + oneTimePassword
                + "\",\"newPassword\":\"onlyletterspassword\"}", ravi).andExpect(status().isBadRequest());
        json(post("/api/auth/password"), "{\"currentPassword\":\"" + oneTimePassword
                + "\",\"newPassword\":\"ravi-password-9\"}", ravi).andExpect(status().isNoContent());
        mvc.perform(get("/api/summary").session(ravi)).andExpect(status().isOk());
        mvc.perform(get("/api/users").session(ravi)).andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void administratorsResetPasswordsAndDisableAccounts() throws Exception {
        MockHttpSession admin = signIn("asha", ADMIN_PASSWORD);
        String reset = mvc.perform(post("/api/users/ravi/reset-password").session(admin).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String newOneTime = JsonPath.read(reset, "$.oneTimePassword");
        signIn("ravi", newOneTime);

        json(post("/api/users/ravi/enabled"), "{\"enabled\":false}", admin).andExpect(jsonPath("$.enabled").value(false));
        json(post("/api/auth/login"), "{\"username\":\"ravi\",\"password\":\"" + newOneTime + "\"}", null)
                .andExpect(status().isUnauthorized());
        json(post("/api/users/asha/enabled"), "{\"enabled\":false}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You cannot disable your own account"));
        mvc.perform(get("/api/users").session(admin))
                .andExpect(jsonPath("$[*].username", hasItem("ravi")))
                .andExpect(jsonPath("$[*].username", hasItem("asha")));
    }
}
