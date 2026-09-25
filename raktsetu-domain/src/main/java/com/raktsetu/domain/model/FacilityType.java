package com.raktsetu.domain.model;

import java.time.LocalDate;
import java.util.Locale;

/** The three kinds of facility, as written in data files. */
public enum FacilityType {
    HUB,
    STORAGE,
    HOSPITAL;

    /** Factory used by loaders so callers never need to know the concrete subclasses. */
    public Facility newFacility(String id, String name, LocalDate lastReportedOn) {
        return switch (this) {
            case HUB -> new HubBloodCentre(id, name, lastReportedOn);
            case STORAGE -> new StorageCentre(id, name, lastReportedOn);
            case HOSPITAL -> new Hospital(id, name, lastReportedOn);
        };
    }

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
