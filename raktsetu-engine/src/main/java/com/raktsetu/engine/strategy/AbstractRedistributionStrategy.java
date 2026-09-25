package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.Freshness;
import com.raktsetu.engine.RiskItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The shared planning algorithm (Template Method pattern). Subclasses decide only two things: the order in which
 * at-risk units are placed, and which destination to pick from the feasible ones.
 *
 * <p>A destination is feasible for a unit when it stores blood, its data is not {@link Freshness#UNVERIFIED}, a route
 * exists, the unit still has at least one day left on arrival, and the destination has <em>spare demand</em>:
 * {@code floor(usage per day x days left on arrival) - its usable stock - units already sent there}. Units are only
 * sent where they are expected to be used, never simply moved to be wasted elsewhere. Transfers are same-group only.
 */
public abstract class AbstractRedistributionStrategy implements RedistributionStrategy {

    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MINUTES_PER_DAY = 24 * 60;

    /** An at-risk unit waiting to be placed. */
    protected record Candidate(Facility source, BloodComponent unit, RiskItem risk) {
    }

    /** A feasible destination for one unit. */
    protected record Destination(Facility facility, Route route, int spareDemand) {
    }

    private record Group(Facility source, Facility destination, Route route) {
    }

    @Override
    public final List<TransferOrder> plan(BloodNetwork network, List<RiskItem> risks, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();
        for (RiskItem risk : risks) {
            for (BloodComponent unit : risk.atRisk()) {
                candidates.add(new Candidate(risk.facility(), unit, risk));
            }
        }
        Map<String, Integer> sent = new HashMap<>();
        Map<Group, List<BloodComponent>> groups = new LinkedHashMap<>();
        Map<Group, String> reasons = new HashMap<>();
        for (Candidate c : order(candidates)) {
            List<Destination> options = feasibleDestinations(c, network, sent, today);
            if (options.isEmpty()) {
                continue;
            }
            Destination d = choose(options);
            sent.merge(key(d.facility(), c.unit()), 1, Integer::sum);
            Group g = new Group(c.source(), d.facility(), d.route());
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(c.unit());
            reasons.putIfAbsent(g, String.format(Locale.ROOT, "%s; %s can use about %d more before expiry",
                    c.risk().reason(), d.facility().getId(), d.spareDemand()));
        }
        List<TransferOrder> orders = new ArrayList<>();
        int n = 1;
        for (Map.Entry<Group, List<BloodComponent>> e : groups.entrySet()) {
            Group g = e.getKey();
            String id = String.format(Locale.ROOT, "T%s-%02d", today.format(ID_DATE), n++);
            orders.add(new TransferOrder(id, g.source(), g.destination(), e.getValue(), today, g.route(),
                    reasons.get(g)));
        }
        return orders;
    }

    /** The order in which candidates are placed; earlier candidates get first pick of destinations. */
    protected abstract List<Candidate> order(List<Candidate> candidates);

    /** Picks one of the feasible destinations (never empty). */
    protected abstract Destination choose(List<Destination> options);

    private List<Destination> feasibleDestinations(Candidate c, BloodNetwork network, Map<String, Integer> sent,
                                                   LocalDate today) {
        List<Destination> options = new ArrayList<>();
        BloodComponent unit = c.unit();
        for (Facility f : network.storingFacilities()) {
            if (f.equals(c.source()) || Freshness.of(f, today) == Freshness.UNVERIFIED) {
                continue;
            }
            Optional<Route> route = network.route(c.source().getId(), f.getId());
            if (route.isEmpty()) {
                continue;
            }
            long daysOnArrival = unit.daysToExpiry(today) - route.get().travelMinutes() / MINUTES_PER_DAY;
            if (daysOnArrival < 1) {
                continue;
            }
            double usage = network.usage().averageDaily(f.getId(), unit.getType(), unit.getBloodGroup(), today);
            int stock = f.getInventory().countUsable(unit.getType(), unit.getBloodGroup(), today);
            int spare = (int) Math.floor(usage * daysOnArrival) - stock - sent.getOrDefault(key(f, unit), 0);
            if (spare > 0) {
                options.add(new Destination(f, route.get(), spare));
            }
        }
        return options;
    }

    private static String key(Facility f, BloodComponent unit) {
        return f.getId() + "|" + unit.getType() + "|" + unit.getBloodGroup();
    }
}
