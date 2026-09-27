package com.raktsetu.server.alerts;

import com.raktsetu.server.db.NotificationRepository.Kind;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

/**
 * Settings under {@code raktsetu.alerts}. With no provider configured, alerts are written to the server log, so the
 * whole flow can be seen without an account. Keys and tokens belong in environment variables, never in the code.
 *
 * @param kinds       notification kinds that also go out as alerts
 * @param maxAttempts tries before an alert is marked failed
 * @param pollMs      how often the dispatcher looks for alerts to send
 * @param sms         SMS through Twilio's Messages API
 * @param whatsapp    WhatsApp through Meta's Cloud API (an approved message template is required)
 */
@ConfigurationProperties("raktsetu.alerts")
public record AlertProperties(List<Kind> kinds, Integer maxAttempts, Long pollMs, Sms sms, WhatsApp whatsapp) {

    public AlertProperties {
        kinds = kinds == null ? List.of(Kind.REQUEST_TO_DISPATCH, Kind.REQUEST_UNMET, Kind.REQUEST_DISPATCHED)
                : List.copyOf(kinds);
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 5 : maxAttempts;
        pollMs = pollMs == null || pollMs < 100 ? 5000L : pollMs;
        sms = sms == null ? new Sms(null, null, null, null) : sms;
        whatsapp = whatsapp == null ? new WhatsApp(null, null, null, null, null) : whatsapp;
    }

    public Set<Kind> alertKinds() {
        return Set.copyOf(kinds);
    }

    /**
     * @param accountSid Twilio account SID
     * @param authToken  Twilio auth token
     * @param from       the sending number or messaging service, for example +12025550123
     * @param baseUrl    API address; only changed for testing
     */
    public record Sms(String accountSid, String authToken, String from, String baseUrl) {
        public Sms {
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.twilio.com" : baseUrl;
        }
    }

    /**
     * @param phoneNumberId the WhatsApp Business phone number ID
     * @param token         a permanent access token
     * @param template      name of an approved template with one body parameter, for example {@code raktsetu_alert}
     * @param language      template language code, for example {@code en}
     * @param baseUrl       API address; only changed for testing
     */
    public record WhatsApp(String phoneNumberId, String token, String template, String language, String baseUrl) {
        public WhatsApp {
            template = template == null || template.isBlank() ? "raktsetu_alert" : template;
            language = language == null || language.isBlank() ? "en" : language;
            baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://graph.facebook.com/v21.0" : baseUrl;
        }
    }
}
