package com.raktsetu.server;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requests to join from the sign-in page, and their approval by an administrator. */
@SpringBootTest
@DirtiesContext
class SignupTest {

    private static final Path DATA;
    private static final String PASSWORD = "joining-pass-2026";

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-signup-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "signuptest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcClient jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions request(String address, String username, String role, String facility) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"displayName\":\"Nurse " + username + "\",\"password\":\""
                + PASSWORD + "\",\"role\":\"" + role + "\",\"facilityId\":\"" + facility + "\"}";
        return mvc.perform(post("/api/signup").with(csrf()).with(r -> {
            r.setRemoteAddr(address);
            return r;
        }).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions signIn(String username, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).with(r -> {
            r.setRemoteAddr("10.9.9.9");
            return r;
        }).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void anyoneCanSeeWhichFacilitiesTheyCanJoin() throws Exception {
        mvc.perform(get("/api/signup/facilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem("H01")))
                .andExpect(jsonPath("$[0].name").exists());
    }

    @Test
    void aRequestWaitsForApprovalBeforeItsOwnerCanSignIn() throws Exception {
        request("10.0.0.1", "nurse.asha", "HOSPITAL_STAFF", "H01")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pending").value(true))
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(jdbc.sql("select count(*) from notification where kind = 'ACCOUNT_REQUESTED' "
                + "and message like '%nurse.asha%'").query(Integer.class).single()).isEqualTo(1);

        signIn("nurse.asha", PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PendingApproval"));
        // A wrong password gets the usual answer, so the request's existence is not revealed.
        signIn("nurse.asha", "wrong-password-1").andExpect(status().isUnauthorized());

        mvc.perform(post("/api/users/nurse.asha/approve").with(user("h02").roles("HOSPITAL_STAFF")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/nurse.asha/approve").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.enabled").value(true));

        signIn("nurse.asha", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HOSPITAL_STAFF"))
                .andExpect(jsonPath("$.facilityId").value("H01"));
    }

    @Test
    void rejectingARequestDeletesIt() throws Exception {
        request("10.0.0.2", "visitor.ravi", "CENTRE_STAFF", "HUB01").andExpect(status().isCreated());
        mvc.perform(post("/api/users/visitor.ravi/enabled").with(user("admin").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/users/visitor.ravi/reject").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(users.find("visitor.ravi")).isEmpty();

        mvc.perform(post("/api/users/h03/reject").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("h03 is not waiting for approval"));
    }

    @Test
    void nobodyCanAskToBeAnAdministratorOrJoinTheWrongKindOfFacility() throws Exception {
        request("10.0.0.3", "would.be.admin", "ADMIN", "H01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Choose blood centre staff or hospital staff"));
        request("10.0.0.3", "wrong.place", "HOSPITAL_STAFF", "HUB01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Hospital staff must belong to a hospital"));
        request("10.0.0.3", "admin", "HOSPITAL_STAFF", "H01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The username 'admin' is already taken"));
        assertThat(users.find("would.be.admin")).isEmpty();
    }

    @Test
    void oneAddressCanSendOnlyFiveRequestsAnHour() throws Exception {
        for (int i = 0; i < 5; i++) {
            request("10.0.0.4", "flood" + i, "HOSPITAL_STAFF", "H01").andExpect(status().isCreated());
        }
        request("10.0.0.4", "flood5", "HOSPITAL_STAFF", "H01").andExpect(status().isTooManyRequests());
        request("10.0.0.5", "flood6", "HOSPITAL_STAFF", "H01").andExpect(status().isCreated());
    }
}
