package com.raktsetu.domain.model;

import java.util.Locale;

/** The three kinds of facility, as written in data files. */
public enum FacilityType {
    HUB,
    STORAGE,
    HOSPITAL;

    public static FacilityType parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Facility type is missing");
        }
        try {
            return valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown facility type: '" + text + "' (expected HUB, STORAGE or HOSPITAL)", e);
        }
    }
}
