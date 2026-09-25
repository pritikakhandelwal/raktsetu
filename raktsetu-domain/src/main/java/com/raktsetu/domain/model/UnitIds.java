package com.raktsetu.domain.model;

/**
 * IDs for units created by RaktSetu, such as {@code RS-000123}. Units loaded from files may use any ID (for example
 * an ISBT 128 donation number); only IDs in this format take part in serial numbering.
 */
public final class UnitIds {

    public static final String PREFIX = "RS-";

    private UnitIds() {
    }

    public static String format(long serial) {
        if (serial < 1) {
            throw new IllegalArgumentException("Serial numbers start at 1");
        }
        return String.format("%s%06d", PREFIX, serial);
    }

    /** The serial number in an ID created by {@link #format}, or 0 if the ID has another format. */
    public static long serialOf(String unitId) {
        if (unitId == null || !unitId.startsWith(PREFIX)) {
            return 0;
        }
        try {
            return Long.parseLong(unitId.substring(PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
