package com.raktsetu.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in, CSRF protection and what each role may do. */
@SpringBootTest
@DirtiesContext
class SecurityTest {

    private static final String PASSWORD = TestFixtures.PASSWORD;
    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-security-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "securitytest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork(context);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String login(String user, String password) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void theApiNeedsASignedInUser() throws Exception {
        mvc.perform(get("/api/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        // The front end itself is public (it shows the sign-in page).
        mvc.perform(get("/")).andExpect(r -> assertThat(r.getResponse().getStatus()).isNotEqualTo(401));
    }

    @Test
    void signingInNeedsTheCsrfToken() throws Exception {
        mvc.perform(json(post("/api/auth/login"), login("admin", PASSWORD))).andExpect(status().isForbidden());
    }

    @Test
    void wrongPasswordIsRefused() throws Exception {
        mvc.perform(json(post("/api/auth/login").with(csrf()), login("admin", "nope")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong username or password, or the account is disabled"));
    }

    @Test
    void signInKeepsTheSessionAndSignOutEndsIt() throws Exception {
        MockHttpSession session = (MockHttpSession) mvc.perform(
                        json(post("/api/auth/login").with(csrf()), login("sc04", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CENTRE_STAFF"))
                .andExpect(jsonPath("$.facilityId").value("SC04"))
                .andReturn().getRequest().getSession();

        mvc.perform(get("/api/summary").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.username").value("sc04"));

        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/summary").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void hospitalStaffRequestOnlyForTheirOwnHospital() throws Exception {
        String own = "{\"hospitalId\":\"H01\",\"component\":\"PLASMA\",\"group\":\"AB+\",\"quantity\":1}";
        String other = "{\"hospitalId\":\"H02\",\"component\":\"PLASMA\",\"group\":\"AB+\",\"quantity\":1}";
        mvc.perform(json(post("/api/requests").with(user("h01")).with(csrf()), own)).andExpect(status().isOk());
        mvc.perform(json(post("/api/requests").with(user("h01")).with(csrf()), other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("You can only do this for your own hospital (H01)"));
        mvc.perform(json(post("/api/plans").with(user("h01")).with(csrf()), "{\"strategy\":\"expiry-first\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/users").with(user("h01"))).andExpect(status().isForbidden());
    }

    @Test
    void centreStaffActOnlyForTheirOwnCentre() throws Exception {
        String units = "{\"component\":\"PLATELETS\",\"group\":\"O+\",\"quantity\":1}";
        mvc.perform(json(post("/api/facilities/SC01/units").with(user("sc01")).with(csrf()), units))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/facilities/SC02/units").with(user("sc01")).with(csrf()), units))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/facilities/SC01/report").with(user("sc01")).with(csrf())).andExpect(status().isOk());
        mvc.perform(json(post("/api/requests").with(user("sc01")).with(csrf()),
                        "{\"hospitalId\":\"H01\",\"component\":\"PLASMA\",\"group\":\"AB+\",\"quantity\":1}"))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/api/facilities").with(user("sc01")).with(csrf()),
                        "{\"name\":\"X\",\"type\":\"HOSPITAL\",\"latitude\":18.5,\"longitude\":73.8}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorsSeeAllAccounts() throws Exception {
        mvc.perform(get("/api/users").with(user("admin"))).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(12)));
    }

    @Test
    void unknownAccountsAreRefusedEvenWithASession() throws Exception {
        mvc.perform(get("/api/users").with(user("ghost"))).andExpect(status().isForbidden());
    }
}
