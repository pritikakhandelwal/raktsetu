package com.raktsetu.domain.model;

import java.time.LocalDate;

/** A high-volume blood centre that collects, processes and supplies blood (the hub of hub-and-spoke). */
public final class HubBloodCentre extends Facility {

    public HubBloodCentre(String id, String name) {
        super(id, name);
    }

    public HubBloodCentre(String id, String name, LocalDate lastReportedOn) {
        super(id, name, lastReportedOn);
    }

    @Override
    public boolean canCollect() {
        return true;
    }

    @Override
    public boolean canStore() {
        return true;
    }

    @Override
    public FacilityType getFacilityType() {
        return FacilityType.HUB;
    }
}
