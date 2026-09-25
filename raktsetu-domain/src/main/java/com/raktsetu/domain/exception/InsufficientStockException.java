package com.raktsetu.domain.exception;

/** A request asks for more usable units than are available. */
public class InsufficientStockException extends RaktSetuException {

    private static final long serialVersionUID = 1L;

    private final int requested;
    private final int available;

    public InsufficientStockException(String what, int requested, int available) {
        this(what, requested, available, "");
    }

    /**
     * As above, adding where the available units are, so staff know what does exist.
     *
     * @param where for example {@code HUB01: 1, SC03: 2}, or an empty string
     */
    public InsufficientStockException(String what, int requested, int available, String where) {
        super("Not enough " + what + ": requested " + requested + ", available " + available
                + (where.isEmpty() ? "" : " (" + where + ")"));
        this.requested = requested;
        this.available = available;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
