package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Urgency;
import com.raktsetu.sim.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Everything random about a run, generated before the run starts: each day's collections, patient requests and which
 * centres report their stock. Every policy replays the same schedule, so differences between policies come only from
 * the policy (common random numbers).
 */
public final class Schedule {

    /** Share of patient requests that are emergencies (nearest centre first); the rest are routine. */
    static final double EMERGENCY_SHARE = 0.3;
    /** Most units a single patient request asks for. */
    static final int MAX_UNITS_PER_REQUEST = 2;

    /** Units collected at one centre on one day. */
    public record Supply(String centreId, ComponentType type, BloodGroup group, int units) {
    }

    /** One simulated day. */
    public record Day(List<Supply> supplies, List<BloodRequest> requests, Set<String> reporting) {
        public Day {
            supplies = List.copyOf(supplies);
            requests = List.copyOf(requests);
            reporting = Set.copyOf(reporting);
        }
    }

    private final List<Day> days;

    private Schedule(List<Day> days) {
        this.days = List.copyOf(days);
    }

    public Day day(int index) {
        return days.get(index);
    }

    public int size() {
        return days.size();
    }

    public static Schedule generate(NetworkSpec spec, TwinSettings settings) {
        RandomSource supply = RandomSource.stream(settings.seed(), settings.replication(), "supply");
        RandomSource demand = RandomSource.stream(settings.seed(), settings.replication(), "demand");
        RandomSource reporting = RandomSource.stream(settings.seed(), settings.replication(), "reporting");
        double totalSupplyWeight = spec.centres().stream().mapToDouble(Site::supplyWeight).sum();
        if (totalSupplyWeight <= 0) {
            throw new IllegalArgumentException("At least one centre needs a positive supply weight");
        }

        List<Day> days = new ArrayList<>(settings.totalDays());
        for (int d = 0; d < settings.totalDays(); d++) {
            List<Supply> supplies = new ArrayList<>();
            for (Site c : spec.centres()) {
                double share = c.supplyWeight() / totalSupplyWeight;
                for (ComponentType type : ComponentType.values()) {
                    double perCentre = spec.totalDailyDemand(type) * spec.supplyFactor(type) * share;
                    for (BloodGroup g : BloodGroup.values()) {
                        int n = supply.poisson(perCentre * RandomSource.GROUP_SHARE.get(g));
                        if (n > 0) {
                            supplies.add(new Supply(c.id(), type, g, n));
                        }
                    }
                }
            }
            List<BloodRequest> requests = new ArrayList<>();
            for (Site h : spec.hospitals()) {
                for (ComponentType type : ComponentType.values()) {
                    double mean = h.demandWeight() * spec.dailyDemand().getOrDefault(type, 0.0);
                    for (BloodGroup g : BloodGroup.values()) {
                        int units = demand.poisson(mean * RandomSource.GROUP_SHARE.get(g));
                        while (units > 0) {
                            int q = Math.min(units, 1 + demand.nextInt(MAX_UNITS_PER_REQUEST));
                            Urgency u = demand.nextDouble() < EMERGENCY_SHARE ? Urgency.EMERGENCY : Urgency.ROUTINE;
                            requests.add(new BloodRequest(h.id(), type, g, q, u));
                            units -= q;
                        }
                    }
                }
            }
            Set<String> reports = new TreeSet<>();
            for (Site c : spec.centres()) {
                if (d == 0 || reporting.nextDouble() < settings.compliance()) {
                    reports.add(c.id());
                }
            }
            days.add(new Day(supplies, requests, reports));
        }
        return new Schedule(days);
    }
}
