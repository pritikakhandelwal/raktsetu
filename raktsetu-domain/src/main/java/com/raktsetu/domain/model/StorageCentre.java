package com.raktsetu.domain.model;

import java.time.LocalDate;

/** A blood storage centre that holds and issues stock but does not collect from donors. */
public final class StorageCentre extends Facility {

    public StorageCentre(String id, String name) {
        super(id, name);
    }

    public StorageCentre(String id, String name, LocalDate lastReportedOn) {
        super(id, name, lastReportedOn);
    }

    @Override
    public boolean canCollect() {
        return false;
    }

    @Override
    public boolean canStore() {
        return true;
    }

    @Override
    public FacilityType getFacilityType() {
        return FacilityType.STORAGE;
    }
}
