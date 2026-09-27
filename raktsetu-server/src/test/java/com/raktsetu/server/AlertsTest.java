package com.raktsetu.server;

import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.alerts.AlertDispatcher;
import com.raktsetu.server.alerts.FakeProvider;
import com.raktsetu.server.db.NotificationRepository;
import com.raktsetu.server.db.NotificationRepository.Kind;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.Role;
import com.raktsetu.server.security.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Urgent alerts end to end: who gets them, the outbox, delivery through a (fake) SMS provider, retries. */
@SpringBootTest
@DirtiesContext
class AlertsTest {

    private static final Path DATA;
    private static final FakeProvider TWILIO = new FakeProvider();
    private static final String CENTRE_PHONE = "+919812345678";

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-alerts-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "alertstest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
        registry.add("raktsetu.alerts.sms.account-sid", () -> "AC123");
        registry.add("raktsetu.alerts.sms.auth-token", () -> "test-token");
        registry.add("raktsetu.alerts.sms.from", () -> "+15550001111");
        registry.add("raktsetu.alerts.sms.base-url", TWILIO::url);
    }

    @AfterAll
    static void stopProvider() {
        TWILIO.close();
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    WorkflowService workflow;

    @Autowired
    AlertDispatcher dispatcher;

    @Autowired
    UserRepository users;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Clock clock;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork((ApplicationContext) context);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jdbc.sql("delete from alert_outbox").update();
        TWILIO.reset();
        for (String centre : List.of("hub01", "sc01", "sc02", "sc03", "sc04")) {
            users.setContact(centre, CENTRE_PHONE, true);
        }
        users.setContact("h01", null, false);
    }

    private List<String> statuses() {
        return jdbc.sql("select status from alert_outbox order by id").query(String.class).list();
    }

    @Test
    void anEmergencyRequestTextsTheCentreThatMustDispatch() throws Exception {
        AppUser hospital = new AppUser("h01", "Staff H01", Role.HOSPITAL_STAFF, "H01");
        workflow.raise(new BloodRequestInput("H01", "RED_CELLS", "O+", 2, "EMERGENCY"), hospital);

        // Queued in the same transaction as the request, before anything is sent.
        assertThat(statuses()).isNotEmpty().containsOnly("PENDING");
        assertThat(TWILIO.received()).isEmpty();

        var report = dispatcher.deliverDue(clock.millis());
        assertThat(report.sent()).isEqualTo(statuses().size());
        assertThat(statuses()).containsOnly("SENT");
        var sms = TWILIO.received().getFirst();
        assertThat(sms.path()).isEqualTo("/2010-04-01/Accounts/AC123/Messages.json");
        String form = URLDecoder.decode(sms.body(), StandardCharsets.UTF_8);
        assertThat(form).contains("To=" + CENTRE_PHONE, "Body=RaktSetu: Request #", "dispatch 2 x O+");
        // H01 has no number, so the hospital gets no text; sending twice does nothing more.
        assertThat(dispatcher.deliverDue(clock.millis()).sent()).isZero();
    }

    @Test
    void aProviderOutageIsRetriedWithBackOffThenDelivered() {
        TWILIO.answer(503, 503);
        tx.executeWithoutResult(s -> notifications.add(TestFixtures.DAY, "SC01", Kind.REQUEST_TO_DISPATCH, "Urgent"));
        long now = clock.millis();

        assertThat(dispatcher.deliverDue(now).retrying()).isEqualTo(1);
        assertThat(dispatcher.deliverDue(now + 29_000).sent()).isZero();          // not due yet
        assertThat(dispatcher.deliverDue(now + 30_000).retrying()).isEqualTo(1); // second failure, wait 60 s
        assertThat(dispatcher.deliverDue(now + 60_000).sent()).isZero();
        assertThat(dispatcher.deliverDue(now + 90_000).sent()).isEqualTo(1);
        assertThat(jdbc.sql("select attempts from alert_outbox").query(Integer.class).single()).isEqualTo(3);
        assertThat(TWILIO.received()).hasSize(3);
    }

    @Test
    void aWrongNumberIsNotRetried() {
        TWILIO.answer(400);
        tx.executeWithoutResult(s -> notifications.add(TestFixtures.DAY, "SC01", Kind.REQUEST_TO_DISPATCH, "Urgent"));
        assertThat(dispatcher.deliverDue(clock.millis()).failed()).isEqualTo(1);
        assertThat(statuses()).containsExactly("FAILED");
        assertThat(jdbc.sql("select last_error from alert_outbox").query(String.class).single()).contains("HTTP 400");
    }

    @Test
    void aChangeThatRollsBackSendsNothing() {
        tx.executeWithoutResult(s -> {
            notifications.add(TestFixtures.DAY, "SC01", Kind.REQUEST_TO_DISPATCH, "Urgent");
            s.setRollbackOnly();
        });
        assertThat(statuses()).isEmpty();
        // Routine kinds never alert, and people who turned alerts off are skipped.
        users.setContact("sc02", CENTRE_PHONE, false);
        tx.executeWithoutResult(s -> {
            notifications.add(TestFixtures.DAY, "SC01", Kind.TRANSFER_INCOMING, "Routine");
            notifications.add(TestFixtures.DAY, "SC02", Kind.REQUEST_TO_DISPATCH, "Urgent");
        });
        assertThat(statuses()).isEmpty();
    }

    @Test
    void peopleSetTheirOwnNumberAndCanSendATestAlert() throws Exception {
        mvc.perform(put("/api/auth/contact").with(user("h01").roles("HOSPITAL_STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"alertsEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add a mobile number to receive alerts"));
        mvc.perform(put("/api/auth/contact").with(user("h01").roles("HOSPITAL_STAFF")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"98765 43210\",\"alertsEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+919876543210"))
                .andExpect(jsonPath("$.alertsEnabled").value(true));

        mvc.perform(post("/api/alerts/test").with(user("h01").roles("HOSPITAL_STAFF")).with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.to").value("+91******3210"))
                .andExpect(jsonPath("$.channels[0]").value("SMS"));
        mvc.perform(post("/api/alerts/test").with(user("h01").roles("HOSPITAL_STAFF")).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(dispatcher.deliverDue(clock.millis()).sent()).isEqualTo(1);
    }

    @Test
    void administratorsSeeChannelsAndDeliveriesWithNumbersMasked() throws Exception {
        tx.executeWithoutResult(s -> notifications.add(TestFixtures.DAY, "SC01", Kind.REQUEST_TO_DISPATCH, "Urgent"));
        dispatcher.deliverDue(clock.millis());
        mvc.perform(get("/api/alerts").with(user("sc01").roles("CENTRE_STAFF"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/alerts").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels[?(@.name == 'SMS')].active").value(hasItem(true)))
                .andExpect(jsonPath("$.counts.SENT").value(1))
                .andExpect(jsonPath("$.recent[0].phone").value("+91******5678"))
                .andExpect(jsonPath("$.recent[0].status").value("SENT"));
        mvc.perform(get("/api/users").with(user("admin").roles("ADMIN")))
                .andExpect(jsonPath("$[?(@.username == 'sc01')].phone").value(hasItem(CENTRE_PHONE)));
    }
}
