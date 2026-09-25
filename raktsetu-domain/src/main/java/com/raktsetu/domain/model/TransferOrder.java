package com.raktsetu.domain.model;

import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.interfaces.Identifiable;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A recommendation to move specific units from one facility to another (an association between two facilities).
 *
 * <p>Orders are created as {@link TransferStatus#PROPOSED} by a strategy and change state only through approval or
 * rejection.
 */
public final class TransferOrder implements Identifiable, Exportable {

    private final String id;
    private final Facility source;
    private final Facility destination;
    private final List<BloodComponent> units;
    private final LocalDate plannedOn;
    private final Route route;
    private final String reason;
    private TransferStatus status = TransferStatus.PROPOSED;

    public TransferOrder(String id, Facility source, Facility destination, List<BloodComponent> units,
                         LocalDate plannedOn, Route route, String reason) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Objects.requireNonNull(source, "source");
        this.destination = Objects.requireNonNull(destination, "destination");
        if (source.equals(destination)) {
            throw new IllegalArgumentException("A transfer needs two different facilities");
        }
        if (units == null || units.isEmpty()) {
            throw new IllegalArgumentException("A transfer needs at least one unit");
        }
        this.units = List.copyOf(units);
        this.plannedOn = Objects.requireNonNull(plannedOn, "plannedOn");
        this.route = Objects.requireNonNull(route, "route");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    @Override
    public String getId() {
        return id;
    }

    public Facility getSource() {
        return source;
    }

    public Facility getDestination() {
        return destination;
    }

    public List<BloodComponent> getUnits() {
        return units;
    }

    public int unitCount() {
        return units.size();
    }

    public LocalDate getPlannedOn() {
        return plannedOn;
    }

    public Route getRoute() {
        return route;
    }

    public String getReason() {
        return reason;
    }

    public TransferStatus getStatus() {
        return status;
    }

    /** Marks the order as carried out. The caller moves the stock before calling this. */
    public void markCompleted() {
        requireProposed();
        status = TransferStatus.COMPLETED;
    }

    public void reject() {
        requireProposed();
        status = TransferStatus.REJECTED;
    }

    private void requireProposed() {
        if (status != TransferStatus.PROPOSED) {
            throw new IllegalStateException("Transfer " + id + " is already " + status);
        }
    }

    /** Short description of the units, for example {@code 6 x PLATELETS B+}. */
    public String describeUnits() {
        BloodComponent first = units.get(0);
        boolean uniform = units.stream().allMatch(
                u -> u.getType() == first.getType() && u.getBloodGroup() == first.getBloodGroup());
        return uniform ? units.size() + " x " + first.getType() + " " + first.getBloodGroup() : units.size() + " units";
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(id, plannedOn.toString(), source.getId(), destination.getId(), describeUnits(),
                String.valueOf(route.distanceKm()), String.valueOf(route.travelMinutes()), status.name(), reason);
    }

    @Override
    public String toString() {
        return id + ": " + describeUnits() + " " + source.getId() + " -> " + destination.getId() + " ("
                + route.distanceKm() + " km) [" + status + "]";
    }
}
