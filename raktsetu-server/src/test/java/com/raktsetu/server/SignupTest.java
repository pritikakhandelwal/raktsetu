package com.raktsetu.server;

import com.raktsetu.server.db.DirectoryRepository;
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

    @Autowired
    DirectoryRepository directory;

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

    private ResultActions directoryRequest(String address, String username, String role, String directoryId)
            throws Exception {
        String body = "{\"username\":\"" + username + "\",\"displayName\":\"Dr " + username + "\",\"password\":\""
                + PASSWORD + "\",\"role\":\"" + role + "\",\"directoryId\":\"" + directoryId + "\"}";
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
    void theDirectoryOfIndianBloodCentresAndHospitalsIsLoaded() {
        assertThat(directory.count()).isGreaterThan(8_000);
        assertThat(directory.find("BC1845")).get().extracting(DirectoryRepository.Entry::name)
                .isEqualTo("KEM Hospital Blood Bank");
    }

    @Test
    void searchingOffersNetworkFacilitiesThenTheDirectory() throws Exception {
        mvc.perform(get("/api/signup/places").param("role", "HOSPITAL_STAFF"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].facilityId", hasItem("H01")))
                .andExpect(jsonPath("$[0].onNetwork").value(true));
        mvc.perform(get("/api/signup/places").param("role", "CENTRE_STAFF").param("q", "kem pune"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.directoryId == 'BC1845')].name").value(hasItem("KEM Hospital Blood Bank")))
                .andExpect(jsonPath("$[?(@.directoryId == 'BC1845')].onNetwork").value(hasItem(false)));
    }

    @Test
    void approvingSomeoneFromADirectoryHospitalAddsItToTheNetworkOnce() throws Exception {
        directoryRequest("10.0.1.1", "dr.mehta", "HOSPITAL_STAFF", "HO83830")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facilityId").isEmpty())
                .andExpect(jsonPath("$.requestedFacility").value("District Hospital, Pune, Pune, Maharashtra"));
        String json = mvc.perform(post("/api/users/dr.mehta/approve").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String facilityId = com.jayway.jsonpath.JsonPath.read(json, "$.facilityId");
        assertThat(facilityId).startsWith("H");
        assertThat(directory.networkFacility("HO83830")).contains(facilityId);

        // A colleague from the same hospital now joins the network facility, which the search offers instead.
        directoryRequest("10.0.1.2", "nurse.joshi", "HOSPITAL_STAFF", "HO83830")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facilityId").value(facilityId));
        mvc.perform(get("/api/signup/places").param("role", "HOSPITAL_STAFF").param("q", "district hospital pune"))
                .andExpect(jsonPath("$[?(@.directoryId == 'HO83830')]").isEmpty())
                .andExpect(jsonPath("$[*].facilityId", hasItem(facilityId)));

        directoryRequest("10.0.1.3", "wrong.kind", "CENTRE_STAFF", "HO83830")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Centre staff must belong to a blood centre"));
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
