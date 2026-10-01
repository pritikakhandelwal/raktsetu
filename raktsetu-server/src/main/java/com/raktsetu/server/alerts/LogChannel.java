package com.raktsetu.server.alerts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Writes alerts to the server log instead of sending them. Used only while no real provider is configured, so the
 * whole alert flow (outbox, dispatcher, delivery status) can be seen and tested without an SMS account. Numbers are
 * masked in the log.
 */
@Component
public class LogChannel implements AlertChannel {

    private static final Logger LOG = LoggerFactory.getLogger(LogChannel.class);

    @Override
    public String name() {
        return "LOG";
    }

    @Override
    public boolean configured() {
        return true;
    }

    @Override
    public void send(String phone, String message) {
        LOG.info("Alert (no SMS or WhatsApp provider configured) to {}: {}", PhoneNumbers.mask(phone), message);
    }
}
