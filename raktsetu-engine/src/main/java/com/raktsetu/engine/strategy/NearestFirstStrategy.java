package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.engine.forecast.DemandForecaster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Places each centre's at-risk units in turn, sending each to the nearest facility that can use it. */
public final class NearestFirstStrategy extends AbstractRedistributionStrategy {

    public static final String NAME = "nearest-first";

    public NearestFirstStrategy() {
    }

    public NearestFirstStrategy(DemandForecaster forecaster) {
        super(forecaster);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Send each at-risk unit to the nearest centre that can use it (least travel)";
    }

    @Override
    protected List<Candidate> order(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing((Candidate c) -> c.source().getId())
                .thenComparing(Candidate::unit, BloodComponent.FEFO));
        return sorted;
    }

    @Override
    protected Destination choose(List<Destination> options) {
        return options.stream()
                .min(Comparator.comparingInt((Destination d) -> d.route().travelMinutes())
                        .thenComparing(d -> d.facility().getId()))
                .orElseThrow();
    }
}
