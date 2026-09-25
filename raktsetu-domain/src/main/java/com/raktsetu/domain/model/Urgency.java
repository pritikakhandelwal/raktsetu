package com.raktsetu.domain.model;

import java.util.Locale;

/** How quickly a request must be met. It changes how the allocator searches (see {@code RequestService}). */
public enum Urgency {
    /** Planned transfusion: prefer an exact group match anywhere in range before using substitutes. */
    ROUTINE,
    /** Life-threatening: take compatible units from the nearest facility first. */
    EMERGENCY;

    public static Urgency parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Urgency is missing");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown urgency: '" + text + "' (expected ROUTINE or EMERGENCY)", e);
        }
    }
}
