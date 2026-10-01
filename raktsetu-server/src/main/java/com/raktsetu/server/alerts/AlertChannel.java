package com.raktsetu.server.alerts;

/**
 * A way of reaching a person outside the app: SMS, WhatsApp, or (with no provider set up) the server log. The
 * dispatcher only knows this interface, so a provider can be added or swapped without changing it.
 */
public interface AlertChannel {

    /** A short name stored with each alert, for example {@code SMS}. */
    String name();

    /** Whether this channel has the settings it needs (for example an API key) and should be used. */
    boolean configured();

    /**
     * Sends one message.
     *
     * @throws AlertDeliveryException if it was not sent; {@link AlertDeliveryException#retryable()} says whether
     *                                trying again later might succeed
     */
    void send(String phone, String message) throws AlertDeliveryException;
}
