package com.raktsetu.engine;

import com.raktsetu.domain.exception.FacilityNotFoundException;
import com.raktsetu.domain.interfaces.AuditTrail;
import com.raktsetu.domain.interfaces.DistanceProvider;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.RouteEntry;
import com.raktsetu.domain.model.TableDistanceProvider;
import com.raktsetu.domain.model.UnitIds;
import com.raktsetu.domain.model.UsageHistory;
import com.raktsetu.domain.model.WastageRecord;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The whole network: facilities (which exist independently of it, so this is aggregation), the distances between
 * them, usage history, wastage and the audit trail.
 */
public final class BloodNetwork {

    private final Map<String, Facility> facilities = new LinkedHashMap<>();
    private final List<RouteEntry> routes;
    private final DistanceProvider distances;
    private final UsageHistory usage;
    private final List<WastageRecord> wastage;
    private final AuditTrail audit;
    private long nextUnitSerial;

    public BloodNetwork(NetworkData data, AuditTrail audit) {
        for (Facility f : data.facilities()) {
            if (facilities.putIfAbsent(f.getId(), f) != null) {
                throw new IllegalArgumentException("Duplicate facility ID " + f.getId());
            }
        }
        this.routes = new ArrayList<>(data.routes());
        this.distances = new TableDistanceProvider(routes);
        this.usage = data.usage();
        this.wastage = new ArrayList<>(data.wastage());
        this.audit = Objects.requireNonNull(audit, "audit");
        this.nextUnitSerial = data.nextUnitSerial();
    }

    /** @throws FacilityNotFoundException if no facility has this ID */
    public Facility facility(String id) throws FacilityNotFoundException {
        Facility f = id == null ? null : facilities.get(id.trim());
        if (f == null) {
            throw new FacilityNotFoundException(id);
        }
        return f;
    }

    public List<Facility> facilities() {
        return List.copyOf(facilities.values());
    }

    /** Facilities that hold stock (hubs and storage centres), in data order. */
    public List<Facility> storingFacilities() {
        return facilities.values().stream().filter(Facility::canStore).toList();
    }

    public Optional<Route> route(String fromId, String toId) {
        return distances.route(fromId, toId);
    }

    public UsageHistory usage() {
        return usage;
    }

    public AuditTrail audit() {
        return audit;
    }

    public List<WastageRecord> wastage() {
        return List.copyOf(wastage);
    }

    /** Usable units of this component and group across all storing facilities. */
    public int totalUsable(ComponentType type, BloodGroup group, LocalDate today) {
        int total = 0;
        for (Facility f : storingFacilities()) {
            total += f.getInventory().countUsable(type, group, today);
        }
        return total;
    }

    /**
     * Adds newly collected or received units to a facility and records them in the audit trail.
     *
     * @throws FacilityNotFoundException if the facility does not exist
     * @throws IllegalArgumentException  if it does not store blood, or the quantity is not positive
     */
    public List<BloodComponent> addUnits(String facilityId, ComponentType type, BloodGroup group,
                                         LocalDate collectedOn, int quantity, LocalDate today)
            throws FacilityNotFoundException {
        Facility f = facility(facilityId);
        if (!f.canStore()) {
            throw new IllegalArgumentException(f + " does not store blood");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        if (collectedOn.isAfter(today)) {
            throw new IllegalArgumentException("Collection date cannot be in the future");
        }
        List<BloodComponent> added = new ArrayList<>();
        for (int i = 0; i < quantity; i++) {
            BloodComponent unit = type.newUnit(UnitIds.format(nextUnitSerial++), group, collectedOn);
            f.getInventory().add(unit);
            added.add(unit);
        }
        audit.record(new AuditEvent(today, AuditEventType.COLLECTED, f.getId(), "", type, group,
                added.stream().map(BloodComponent::getId).toList(), "added " + quantity));
        return added;
    }

    /**
     * Removes every expired unit from every facility, records it as wastage and in the audit trail, and returns what was
     * removed. Run this at least daily, and before issuing.
     */
    public List<WastageRecord> removeExpired(LocalDate today) {
        List<WastageRecord> removed = new ArrayList<>();
        for (Facility f : storingFacilities()) {
            List<BloodComponent> expired = f.getInventory().removeExpired(today);
            Map<String, List<BloodComponent>> byKind = new LinkedHashMap<>();
            for (BloodComponent u : expired) {
                removed.add(WastageRecord.of(today, f.getId(), u));
                byKind.computeIfAbsent(u.getType() + "/" + u.getBloodGroup(), k -> new ArrayList<>()).add(u);
            }
            for (List<BloodComponent> units : byKind.values()) {
                BloodComponent first = units.get(0);
                audit.record(new AuditEvent(today, AuditEventType.DISCARDED, f.getId(), "", first.getType(),
                        first.getBloodGroup(), units.stream().map(BloodComponent::getId).toList(), "expired"));
            }
        }
        wastage.addAll(removed);
        return removed;
    }

    /**
     * Removes one specific unit from stock as wastage, for example because the bag is damaged, and records why.
     *
     * @throws FacilityNotFoundException if the facility does not exist
     * @throws IllegalArgumentException  if the unit is not in stock there or no reason is given
     */
    public WastageRecord discardUnit(String facilityId, String unitId, String reason, LocalDate today)
            throws FacilityNotFoundException {
        Facility f = facility(facilityId);
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required to discard a unit");
        }
        if (!f.canStore()) {
            throw new IllegalArgumentException(f + " does not store blood");
        }
        BloodComponent unit = f.getInventory().take(unitId)
                .orElseThrow(() -> new IllegalArgumentException("Unit " + unitId + " is not in stock at " + f));
        WastageRecord record = WastageRecord.of(today, f.getId(), unit);
        wastage.add(record);
        audit.record(new AuditEvent(today, AuditEventType.DISCARDED, f.getId(), "", unit.getType(),
                unit.getBloodGroup(), List.of(unit.getId()), reason.trim()));
        return record;
    }

    /**
     * Puts units back on a facility's shelf, for example when a request is cancelled before dispatch. Units that have
     * expired meanwhile are removed at the next {@link #removeExpired} and counted as wastage there.
     *
     * @throws FacilityNotFoundException if the facility does not exist
     * @throws IllegalArgumentException  if the facility does not store blood or a unit is already in stock there
     */
    public void returnUnits(String facilityId, List<BloodComponent> units, String note, LocalDate today)
            throws FacilityNotFoundException {
        Facility f = facility(facilityId);
        if (!f.canStore()) {
            throw new IllegalArgumentException(f + " does not store blood");
        }
        Map<String, List<BloodComponent>> byKind = new LinkedHashMap<>();
        for (BloodComponent u : units) {
            f.getInventory().add(u);
            byKind.computeIfAbsent(u.getType() + "/" + u.getBloodGroup(), k -> new ArrayList<>()).add(u);
        }
        for (List<BloodComponent> group : byKind.values()) {
            BloodComponent first = group.get(0);
            audit.record(new AuditEvent(today, AuditEventType.RETURNED, f.getId(), "", first.getType(),
                    first.getBloodGroup(), group.stream().map(BloodComponent::getId).toList(), note));
        }
    }

    /** The current state, for saving. */
    public NetworkData snapshot() {
        return new NetworkData(new ArrayList<>(facilities.values()), routes, usage, wastage, nextUnitSerial);
    }
}
