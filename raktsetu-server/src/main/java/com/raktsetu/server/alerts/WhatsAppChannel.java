package com.raktsetu.server.alerts;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/**
 * WhatsApp through Meta's Cloud API. Business-started conversations must use a pre-approved message template; the
 * alert text is passed as the template's single body parameter. Configured by {@code raktsetu.alerts.whatsapp.*}.
 */
@Component
public class WhatsAppChannel extends HttpAlertChannel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AlertProperties.WhatsApp settings;

    public WhatsAppChannel(AlertProperties properties) {
        this.settings = properties.whatsapp();
    }

    @Override
    public String name() {
        return "WHATSAPP";
    }

    @Override
    public boolean configured() {
        return present(settings.phoneNumberId()) && present(settings.token());
    }

    @Override
    protected HttpRequest.Builder request(String phone, String message) {
        Map<String, Object> body = Map.of(
                "messaging_product", "whatsapp",
                "to", phone.startsWith("+") ? phone.substring(1) : phone,
                "type", "template",
                "template", Map.of(
                        "name", settings.template(),
                        "language", Map.of("code", settings.language()),
                        "components", List.of(Map.of(
                                "type", "body",
                                "parameters", List.of(Map.of("type", "text", "text", message))))));
        return HttpRequest.newBuilder(uri(settings.baseUrl(), "/" + settings.phoneNumberId() + "/messages"))
                .header("Authorization", "Bearer " + settings.token())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
    }
}
