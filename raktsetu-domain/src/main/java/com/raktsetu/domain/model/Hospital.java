package com.raktsetu.domain.model;

import java.time.LocalDate;

/** A hospital that raises requests. In this model it holds no stock of its own; units are issued to it by centres. */
public final class Hospital extends Facility {

    public Hospital(String id, String name) {
        super(id, name);
    }

    public Hospital(String id, String name, LocalDate lastReportedOn) {
        super(id, name, lastReportedOn);
    }

    @Override
    public boolean canCollect() {
        return false;
    }

    @Override
    public boolean canStore() {
        return false;
    }

    @Override
    public FacilityType getFacilityType() {
        return FacilityType.HOSPITAL;
    }
}
