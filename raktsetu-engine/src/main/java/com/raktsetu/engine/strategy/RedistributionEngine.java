package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.ExpiryRiskAnalyzer;
import com.raktsetu.engine.RiskItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runs the risk check and then a chosen strategy, or all strategies for comparison. */
public final class RedistributionEngine {

    private final ExpiryRiskAnalyzer analyzer;
    private final Map<String, RedistributionStrategy> strategies = new LinkedHashMap<>();

    public RedistributionEngine(ExpiryRiskAnalyzer analyzer, List<RedistributionStrategy> strategies) {
        this.analyzer = Objects.requireNonNull(analyzer, "analyzer");
        for (RedistributionStrategy s : strategies) {
            this.strategies.put(s.name(), s);
        }
        if (this.strategies.isEmpty()) {
            throw new IllegalArgumentException("At least one strategy is needed");
        }
    }

    /** The two V1 strategies with the default risk settings. */
    public static RedistributionEngine withDefaultStrategies() {
        return new RedistributionEngine(new ExpiryRiskAnalyzer(),
                List.of(new NearestFirstStrategy(), new ExpiryFirstStrategy()));
    }

    public List<RedistributionStrategy> strategies() {
        return List.copyOf(strategies.values());
    }

    /** @throws IllegalArgumentException if no strategy has this name */
    public RedistributionStrategy strategy(String name) {
        RedistributionStrategy s = strategies.get(name);
        if (s == null) {
            throw new IllegalArgumentException("Unknown strategy '" + name + "'; choose one of " + strategies.keySet());
        }
        return s;
    }

    public List<RiskItem> risks(BloodNetwork network, LocalDate today) {
        return analyzer.analyze(network, today);
    }

    public List<TransferOrder> plan(String strategyName, BloodNetwork network, LocalDate today) {
        return strategy(strategyName).plan(network, risks(network, today), today);
    }

    /** Every strategy's plan on the same risk list. */
    public List<StrategyComparison> compare(BloodNetwork network, LocalDate today) {
        List<RiskItem> risks = risks(network, today);
        int atRisk = risks.stream().mapToInt(r -> r.atRisk().size()).sum();
        List<StrategyComparison> result = new ArrayList<>();
        for (RedistributionStrategy s : strategies.values()) {
            result.add(new StrategyComparison(s.name(), s.plan(network, risks, today), atRisk));
        }
        return result;
    }
}
