package com.raktsetu.engine;

import com.raktsetu.domain.exception.FacilityNotFoundException;
import com.raktsetu.domain.exception.IncompatibleBloodException;
import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.exception.UnitExpiredException;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.Compatibility;
import com.raktsetu.domain.model.CompatibilityResult;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.Urgency;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Serves hospital requests from the network.
 *
 * <p>Facilities are searched by travel time from the hospital, within a maximum travel time; facilities whose data is
 * {@link Freshness#UNVERIFIED} are searched last because their stock may not really be there. Units are always issued
 * first-expiry-first-out. Requests are all-or-nothing: if the network cannot meet the whole request, nothing is
 * issued and the exception says where the compatible units are.
 */
public final class RequestService {

    /** Default search radius: roughly how far an emergency can wait for blood by road. */
    public static final int DEFAULT_MAX_TRAVEL_MINUTES = 90;

    private final BloodNetwork network;
    private final int maxTravelMinutes;

    public RequestService(BloodNetwork network) {
        this(network, DEFAULT_MAX_TRAVEL_MINUTES);
    }

    public RequestService(BloodNetwork network, int maxTravelMinutes) {
        this.network = Objects.requireNonNull(network, "network");
        if (maxTravelMinutes < 0) {
            throw new IllegalArgumentException("Maximum travel time cannot be negative");
        }
        this.maxTravelMinutes = maxTravelMinutes;
    }

    private record Candidate(Facility facility, Route route) {
    }

    private record Take(Candidate from, BloodGroup group, int count) {
    }

    /**
     * Meets a request in full or not at all.
     *
     * <p>{@link Urgency#EMERGENCY}: nearest facility first, using compatible substitutes there before travelling
     * further. {@link Urgency#ROUTINE}: the recipient's own group anywhere in range first, then substitutes.
     *
     * @throws FacilityNotFoundException  if the hospital is unknown
     * @throws InsufficientStockException if not enough compatible units are within range
     * @throws UnitExpiredException       never in practice, because expired units are removed first
     */
    public RequestOutcome handle(BloodRequest request, LocalDate today)
            throws FacilityNotFoundException, InsufficientStockException, UnitExpiredException {
        Facility hospital = network.facility(request.hospitalId());
        network.removeExpired(today);

        List<Candidate> candidates = candidatesFor(hospital, today);
        List<BloodGroup> donors = DonorPreference.forRecipient(request.component(), request.recipient());
        List<Take> plan = request.urgency() == Urgency.EMERGENCY
                ? planNearestFirst(request, candidates, donors, today)
                : planExactFirst(request, candidates, donors, today);

        int planned = plan.stream().mapToInt(Take::count).sum();
        if (planned < request.quantity()) {
            throw new InsufficientStockException("compatible " + request.component() + " for a "
                    + request.recipient() + " patient within " + maxTravelMinutes + " min of " + hospital.getId(),
                    request.quantity(), planned, availability(candidates, donors, request, today));
        }

        List<Allocation> allocations = new ArrayList<>();
        for (Take take : plan) {
            Facility source = take.from().facility();
            List<BloodComponent> units = source.getInventory().issue(request.component(), take.group(), take.count(),
                    today);
            CompatibilityResult why = units.get(0).compatibilityWith(request.recipient());
            record(today, source, hospital.getId(), units, why, request.recipient());
            allocations.add(new Allocation(source, take.from().route(), take.group(), units, why));
        }
        return new RequestOutcome(request, allocations);
    }

    /**
     * Issues one specific unit (for example, a bag already cross-matched) to a patient, checking it first.
     *
     * @throws IncompatibleBloodException if the unit is unsafe for the recipient
     * @throws UnitExpiredException       if the unit has expired
     * @throws RaktSetuException          if the unit is not in stock at that facility
     */
    public Allocation issueUnit(String facilityId, String unitId, String hospitalId, BloodGroup recipient,
                                LocalDate today) throws RaktSetuException {
        Facility source = network.facility(facilityId);
        Facility hospital = network.facility(hospitalId);
        if (!source.canStore()) {
            throw new RaktSetuException(source + " does not store blood");
        }
        BloodComponent unit = source.getInventory().find(unitId)
                .orElseThrow(() -> new RaktSetuException("Unit " + unitId + " is not in stock at " + source));
        CompatibilityResult why = unit.compatibilityWith(recipient);
        if (!why.isAllowed()) {
            throw new IncompatibleBloodException(why.reason());
        }
        if (unit.isExpired(today)) {
            throw new UnitExpiredException(unit.getId(), unit.getExpiryDate());
        }
        source.getInventory().take(unitId);
        record(today, source, hospital.getId(), List.of(unit), why, recipient);
        Route route = network.route(source.getId(), hospital.getId()).orElse(Route.ZERO);
        return new Allocation(source, route, unit.getBloodGroup(), List.of(unit), why);
    }

    private List<Candidate> candidatesFor(Facility hospital, LocalDate today) {
        List<Candidate> list = new ArrayList<>();
        for (Facility f : network.storingFacilities()) {
            Optional<Route> route = network.route(f.getId(), hospital.getId());
            if (route.isPresent() && route.get().travelMinutes() <= maxTravelMinutes) {
                list.add(new Candidate(f, route.get()));
            }
        }
        list.sort(Comparator.comparing((Candidate c) -> Freshness.of(c.facility(), today) == Freshness.UNVERIFIED)
                .thenComparingInt(c -> c.route().travelMinutes())
                .thenComparing(c -> c.facility().getId()));
        return list;
    }

    private static List<Take> planNearestFirst(BloodRequest req, List<Candidate> candidates, List<BloodGroup> donors,
                                               LocalDate today) {
        List<Take> plan = new ArrayList<>();
        int needed = req.quantity();
        for (Candidate c : candidates) {
            for (BloodGroup g : donors) {
                needed -= take(plan, c, g, needed, req, today);
            }
        }
        return plan;
    }

    private static List<Take> planExactFirst(BloodRequest req, List<Candidate> candidates, List<BloodGroup> donors,
                                             LocalDate today) {
        List<Take> plan = new ArrayList<>();
        int needed = req.quantity();
        for (BloodGroup g : donors) {
            for (Candidate c : candidates) {
                needed -= take(plan, c, g, needed, req, today);
            }
        }
        return plan;
    }

    /** Adds a take of up to {@code needed} units to the plan and returns how many it takes. */
    private static int take(List<Take> plan, Candidate c, BloodGroup g, int needed, BloodRequest req,
                            LocalDate today) {
        if (needed <= 0) {
            return 0;
        }
        int n = Math.min(needed, c.facility().getInventory().countUsable(req.component(), g, today));
        if (n > 0) {
            plan.add(new Take(c, g, n));
        }
        return n;
    }

    private static String availability(List<Candidate> candidates, List<BloodGroup> donors, BloodRequest req,
                                       LocalDate today) {
        Map<String, Integer> byFacility = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            int n = 0;
            for (BloodGroup g : donors) {
                n += c.facility().getInventory().countUsable(req.component(), g, today);
            }
            if (n > 0) {
                byFacility.put(c.facility().getId(), n);
            }
        }
        List<String> parts = new ArrayList<>();
        byFacility.forEach((id, n) -> parts.add(id + ": " + n));
        return String.join(", ", parts);
    }

    private void record(LocalDate today, Facility source, String hospitalId, List<BloodComponent> units,
                        CompatibilityResult why, BloodGroup recipient) {
        BloodComponent first = units.get(0);
        network.usage().record(today, source.getId(), first.getType(), first.getBloodGroup(), units.size());
        String note = why.level() == Compatibility.IDENTICAL
                ? "exact match"
                : "substitute " + first.getBloodGroup() + " for " + recipient + ": " + why.reason();
        network.audit().record(new AuditEvent(today, AuditEventType.ISSUED, source.getId(), hospitalId,
                first.getType(), first.getBloodGroup(), units.stream().map(BloodComponent::getId).toList(), note));
    }
}
