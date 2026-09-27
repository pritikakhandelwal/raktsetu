package com.raktsetu.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

/**
 * The single-page app needs the XSRF-TOKEN cookie before its first POST. This runs in its own application context
 * because spring-security-test's csrf() helper, used by other test classes, replaces the token store for the whole
 * context with one that never writes cookies.
 */
@SpringBootTest
@DirtiesContext
class CsrfCookieTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "csrftest");
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    WebApplicationContext context;

    @Test
    void theCsrfCookieIsIssuedOnTheFirstRequest() throws Exception {
        MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()
                .perform(get("/api/auth/me"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }
}
