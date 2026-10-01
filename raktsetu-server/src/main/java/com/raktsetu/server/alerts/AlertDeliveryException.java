package com.raktsetu.server.alerts;

/** An alert could not be sent. */
public class AlertDeliveryException extends Exception {

    private final boolean retryable;

    public AlertDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AlertDeliveryException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * True for problems that may pass (the provider is down, rate-limited, or the network failed); false when
     * retrying cannot help (a wrong number, a rejected key).
     */
    public boolean retryable() {
        return retryable;
    }

    /** Classifies an HTTP status from a provider: 408, 429 and 5xx are worth retrying; other errors are not. */
    static AlertDeliveryException fromStatus(String provider, int status, String body) {
        boolean retry = status == 408 || status == 429 || status >= 500;
        String detail = body == null ? "" : body.replaceAll("\\s+", " ");
        if (detail.length() > 200) {
            detail = detail.substring(0, 200) + "...";
        }
        return new AlertDeliveryException(provider + " answered HTTP " + status + ": " + detail, retry);
    }
}
