package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.engine.forecast.DemandForecaster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places the soonest-expiring units in the whole network first, sending each to the centre with the most spare demand,
 * so the most urgent units get the most certain destination.
 */
public final class ExpiryFirstStrategy extends AbstractRedistributionStrategy {

    public static final String NAME = "expiry-first";

    public ExpiryFirstStrategy() {
    }

    public ExpiryFirstStrategy(DemandForecaster forecaster) {
        super(forecaster);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Place the soonest-expiring units first, at the centre with the most spare demand";
    }

    @Override
    protected List<Candidate> order(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(Candidate::unit, BloodComponent.FEFO)
                .thenComparing(c -> c.source().getId()));
        return sorted;
    }

    @Override
    protected Destination choose(List<Destination> options) {
        return options.stream()
                .max(Comparator.comparingInt(Destination::spareDemand)
                        .thenComparing(Comparator.comparingInt((Destination d) -> d.route().travelMinutes()).reversed())
                        .thenComparing(Comparator.comparing((Destination d) -> d.facility().getId()).reversed()))
                .orElseThrow();
    }
}
