package com.raktsetu.server.alerts;

import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** SMS through Twilio's Messages API. Configured by {@code raktsetu.alerts.sms.*}. */
@Component
public class SmsChannel extends HttpAlertChannel {

    /** SMS longer than this are split and billed as several messages; alerts are cut to fit two. */
    static final int MAX_LENGTH = 300;

    private final AlertProperties.Sms settings;

    public SmsChannel(AlertProperties properties) {
        this.settings = properties.sms();
    }

    @Override
    public String name() {
        return "SMS";
    }

    @Override
    public boolean configured() {
        return present(settings.accountSid()) && present(settings.authToken()) && present(settings.from());
    }

    @Override
    protected HttpRequest.Builder request(String phone, String message) {
        String text = message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH - 3) + "..." : message;
        String form = "To=" + encode(phone) + "&From=" + encode(settings.from()) + "&Body=" + encode(text);
        String auth = Base64.getEncoder().encodeToString((settings.accountSid() + ":" + settings.authToken())
                .getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder(uri(settings.baseUrl(),
                        "/2010-04-01/Accounts/" + encode(settings.accountSid()) + "/Messages.json"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
