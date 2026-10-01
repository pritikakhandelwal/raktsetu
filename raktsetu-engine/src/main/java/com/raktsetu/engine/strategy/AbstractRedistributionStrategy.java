package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.Route;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.Freshness;
import com.raktsetu.engine.RiskItem;
import com.raktsetu.engine.forecast.DemandForecaster;
import com.raktsetu.engine.forecast.DemandForecaster.Forecast;
import com.raktsetu.engine.forecast.MovingAverageForecaster;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * The shared planning algorithm (Template Method pattern). Subclasses decide only two things: the order in which
 * at-risk units are placed, and which destination to pick from the feasible ones.
 *
 * <p>A destination is feasible for a unit when it stores blood, its data is not {@link Freshness#UNVERIFIED}, a route
 * exists, the unit still has at least one day left on arrival, and the destination has <em>spare demand</em>:
 * {@code floor(forecast demand over the days left on arrival) - its usable stock - units already sent there}. Units are only
 * sent where they are expected to be used, never simply moved to be wasted elsewhere. Transfers are same-group only.
 */
public abstract class AbstractRedistributionStrategy implements RedistributionStrategy {

    private static final DateTimeFormatter ID_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final DemandForecaster forecaster;

    /** With the moving-average forecaster. */
    protected AbstractRedistributionStrategy() {
        this(new MovingAverageForecaster());
    }

    /** @param forecaster predicts how much each possible destination will use */
    protected AbstractRedistributionStrategy(DemandForecaster forecaster) {
        this.forecaster = Objects.requireNonNull(forecaster, "forecaster");
    }

    /** An at-risk unit waiting to be placed. */
    protected record Candidate(Facility source, BloodComponent unit, RiskItem risk) {
    }

    /** A feasible destination for one unit. */
    protected record Destination(Facility facility, Route route, int spareDemand) {
    }

    /** Units are grouped into one order per source, destination, component and blood group. */
    private record Group(Facility source, Facility destination, Route route, ComponentType component,
                         BloodGroup group) {
    }

    @Override
    public final List<TransferOrder> plan(BloodNetwork network, List<RiskItem> risks, LocalDate today) {
        List<Candidate> candidates = new ArrayList<>();
        for (RiskItem risk : risks) {
            for (BloodComponent unit : risk.atRisk()) {
                candidates.add(new Candidate(risk.facility(), unit, risk));
            }
        }
        Figures figures = new Figures(network, forecaster, today);
        Map<Group, List<BloodComponent>> groups = new LinkedHashMap<>();
        Map<Group, String> reasons = new HashMap<>();
        for (Candidate c : order(candidates)) {
            List<Destination> options = feasibleDestinations(c, figures, today);
            if (options.isEmpty()) {
                continue;
            }
            Destination d = choose(options);
            figures.state(d.facility(), c.unit().getType(), c.unit().getBloodGroup()).sent++;
            Group g = new Group(c.source(), d.facility(), d.route(), c.unit().getType(), c.unit().getBloodGroup());
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

    /**
     * Only storing facilities with a route from the source are considered (the network's neighbour index), rather
     * than every facility in the network, and their figures are looked up once per plan: planning time grows with
     * the number of at-risk units times the number of nearby centres, not times the size of the whole network.
     */
    private static List<Destination> feasibleDestinations(Candidate c, Figures figures, LocalDate today) {
        List<Destination> options = new ArrayList<>();
        BloodComponent unit = c.unit();
        long daysLeft = unit.daysToExpiry(today);
        for (Target t : figures.targets(c.source(), unit.getType(), unit.getBloodGroup())) {
            long daysOnArrival = daysLeft - t.route().travelMinutes() / MINUTES_PER_DAY;
            if (daysOnArrival < 1) {
                continue;
            }
            SlotState state = t.state();
            int spare = (int) Math.floor(state.demand.over(daysOnArrival)) - state.stock - state.sent;
            if (spare > 0) {
                options.add(new Destination(t.facility(), t.route(), spare));
            }
        }
        return options;
    }

    /** One facility's stock of one component and group. */
    private record Slot(String facilityId, ComponentType component, BloodGroup group) {
    }

    /**
     * A destination's figures for one component and group, shared by every unit that could go there. Usage and
     * stock do not change while a plan is made (planning only proposes transfers; nothing moves); {@code sent}
     * counts units this plan has already sent there.
     */
    private static final class SlotState {
        final Forecast demand;
        final int stock;
        int sent;

        SlotState(Forecast demand, int stock) {
            this.demand = demand;
            this.stock = stock;
        }
    }

    /** A possible destination for units of one kind from one source. */
    private record Target(Facility facility, Route route, SlotState state) {
    }

    /** What one plan looks up, each worked out once. */
    private static final class Figures {
        private final BloodNetwork network;
        private final DemandForecaster forecaster;
        private final LocalDate today;
        private final Map<Slot, SlotState> states = new HashMap<>();
        private final Map<Slot, List<Target>> targets = new HashMap<>();

        Figures(BloodNetwork network, DemandForecaster forecaster, LocalDate today) {
            this.network = network;
            this.forecaster = forecaster;
            this.today = today;
        }

        SlotState state(Facility f, ComponentType type, BloodGroup group) {
            return states.computeIfAbsent(new Slot(f.getId(), type, group), k -> new SlotState(
                    forecaster.forecast(network.usage(), f.getId(), type, group, today),
                    f.getInventory().countUsable(type, group, today)));
        }

        /** Verified storing neighbours of {@code source} with a route from it, in data order. */
        List<Target> targets(Facility source, ComponentType type, BloodGroup group) {
            return targets.computeIfAbsent(new Slot(source.getId(), type, group), k -> {
                List<Target> list = new ArrayList<>();
                for (BloodNetwork.Neighbour n : network.storingNeighbours(source.getId())) {
                    if (Freshness.of(n.facility(), today) != Freshness.UNVERIFIED) {
                        list.add(new Target(n.facility(), n.route(), state(n.facility(), type, group)));
                    }
                }
                return list;
            });
        }
    }
}
