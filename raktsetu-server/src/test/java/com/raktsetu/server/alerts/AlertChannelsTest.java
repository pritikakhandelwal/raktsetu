package com.raktsetu.server.alerts;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Phone numbers, the channels' requests to their providers, and back-off, without Spring. */
class AlertChannelsTest {

    @Test
    void phoneNumbersAreNormalisedToE164() {
        assertThat(PhoneNumbers.normalise("98123 45678")).isEqualTo("+919812345678");
        assertThat(PhoneNumbers.normalise("098123-45678")).isEqualTo("+919812345678");
        assertThat(PhoneNumbers.normalise("91 9812345678")).isEqualTo("+919812345678");
        assertThat(PhoneNumbers.normalise("+44 7700 900123")).isEqualTo("+447700900123");
        assertThat(PhoneNumbers.normalise("0044 7700 900123")).isEqualTo("+447700900123");
        assertThatThrownBy(() -> PhoneNumbers.normalise("12345")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalise("5812345678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PhoneNumbers.normalise(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(PhoneNumbers.mask("+919812345678")).isEqualTo("+91******5678");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("****");
    }

    @Test
    void smsIsSentThroughTwiliosMessagesApi() throws Exception {
        try (FakeProvider twilio = new FakeProvider()) {
            SmsChannel sms = new SmsChannel(properties(new AlertProperties.Sms("AC123", "secret", "+15550001111",
                    twilio.url()), null));
            assertThat(sms.configured()).isTrue();
            sms.send("+919812345678", "RaktSetu: dispatch 2 x O+ RED_CELLS & quickly");

            var r = twilio.received().getFirst();
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.path()).isEqualTo("/2010-04-01/Accounts/AC123/Messages.json");
            assertThat(r.authorization()).isEqualTo("Basic "
                    + Base64.getEncoder().encodeToString("AC123:secret".getBytes(StandardCharsets.UTF_8)));
            assertThat(r.contentType()).isEqualTo("application/x-www-form-urlencoded");
            String form = URLDecoder.decode(r.body(), StandardCharsets.UTF_8);
            assertThat(form).isEqualTo("To=+919812345678&From=+15550001111"
                    + "&Body=RaktSetu: dispatch 2 x O+ RED_CELLS & quickly");
        }
    }

    @Test
    void whatsAppUsesAnApprovedTemplateWithTheMessageAsItsParameter() throws Exception {
        try (FakeProvider meta = new FakeProvider()) {
            WhatsAppChannel whatsapp = new WhatsAppChannel(properties(null,
                    new AlertProperties.WhatsApp("1098", "tok", null, null, meta.url())));
            assertThat(whatsapp.configured()).isTrue();
            whatsapp.send("+919812345678", "Request #7 is on its way");

            var r = meta.received().getFirst();
            assertThat(r.path()).isEqualTo("/1098/messages");
            assertThat(r.authorization()).isEqualTo("Bearer tok");
            assertThat(r.body()).contains("\"to\":\"919812345678\"", "\"name\":\"raktsetu_alert\"",
                    "\"code\":\"en\"", "\"text\":\"Request #7 is on its way\"");
        }
    }

    @Test
    void providerErrorsSayWhetherToRetry() {
        try (FakeProvider twilio = new FakeProvider()) {
            SmsChannel sms = new SmsChannel(properties(new AlertProperties.Sms("AC1", "s", "+1555", twilio.url()),
                    null));
            twilio.answer(503, 429, 400);
            assertThatThrownBy(() -> sms.send("+919812345678", "x"))
                    .isInstanceOfSatisfying(AlertDeliveryException.class, e -> assertThat(e.retryable()).isTrue());
            assertThatThrownBy(() -> sms.send("+919812345678", "x"))
                    .isInstanceOfSatisfying(AlertDeliveryException.class, e -> assertThat(e.retryable()).isTrue());
            assertThatThrownBy(() -> sms.send("+919812345678", "x"))
                    .isInstanceOfSatisfying(AlertDeliveryException.class, e -> {
                        assertThat(e.retryable()).isFalse();
                        assertThat(e.getMessage()).contains("HTTP 400");
                    });
        }
        // An unreachable provider may come back.
        SmsChannel down = new SmsChannel(properties(new AlertProperties.Sms("AC1", "s", "+1555",
                "http://127.0.0.1:1"), null));
        assertThatThrownBy(() -> down.send("+919812345678", "x"))
                .isInstanceOfSatisfying(AlertDeliveryException.class, e -> assertThat(e.retryable()).isTrue());
    }

    @Test
    void theLogIsUsedOnlyWhileNoProviderIsConfigured() throws Exception {
        LogChannel log = new LogChannel();
        SmsChannel unconfigured = new SmsChannel(properties(null, null));
        assertThat(unconfigured.configured()).isFalse();
        assertThatThrownBy(() -> unconfigured.send("+919812345678", "x"))
                .isInstanceOfSatisfying(AlertDeliveryException.class, e -> assertThat(e.retryable()).isFalse());
        assertThat(new AlertChannels(List.of(unconfigured, log)).active()).containsExactly(log);

        SmsChannel configured = new SmsChannel(properties(new AlertProperties.Sms("AC1", "s", "+1555", null), null));
        AlertChannels withSms = new AlertChannels(List.of(configured, log));
        assertThat(withSms.active()).containsExactly(configured);
        assertThat(withSms.status()).extracting(AlertChannels.Status::name, AlertChannels.Status::active)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SMS", true),
                        org.assertj.core.groups.Tuple.tuple("LOG", false));
        log.send("+919812345678", "only logged");
    }

    @Test
    void retriesBackOffExponentiallyUpToAnHour() {
        assertThat(AlertDispatcher.backoff(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(AlertDispatcher.backoff(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(AlertDispatcher.backoff(4)).isEqualTo(Duration.ofMinutes(4));
        assertThat(AlertDispatcher.backoff(30)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void defaultsAlertOnlyUrgentKinds() {
        AlertProperties p = new AlertProperties(null, null, null, null, null);
        assertThat(p.maxAttempts()).isEqualTo(5);
        assertThat(p.pollMs()).isEqualTo(5000L);
        assertThat(p.alertKinds()).extracting(Enum::name)
                .containsExactlyInAnyOrder("REQUEST_TO_DISPATCH", "REQUEST_UNMET", "REQUEST_DISPATCHED");
        assertThat(p.sms().baseUrl()).isEqualTo("https://api.twilio.com");
        assertThat(p.whatsapp().template()).isEqualTo("raktsetu_alert");
    }

    private static AlertProperties properties(AlertProperties.Sms sms, AlertProperties.WhatsApp whatsapp) {
        return new AlertProperties(null, null, null, sms, whatsapp);
    }
}
