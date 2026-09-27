package com.raktsetu.sim.twin;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.strategy.RedistributionEngine;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * A redistribution policy under test. It sees only the observed network (reported stock) and proposes transfers; the
 * simulator then tries to carry them out on the true network, where some units may already be gone.
 */
public interface TwinPolicy {

    /** A request that could not be met today; reactive policies respond to these. */
    record Shortfall(String hospitalId, ComponentType component, BloodGroup group, int units) {
    }

    String name();

    List<TransferOrder> plan(BloodNetwork observed, List<Shortfall> shortfalls, LocalDate today);

    /** P0: the status quo; no transfers. */
    TwinPolicy NONE = new TwinPolicy() {
        @Override
        public String name() {
            return "none";
        }

        @Override
        public List<TransferOrder> plan(BloodNetwork observed, List<Shortfall> shortfalls, LocalDate today) {
            return List.of();
        }
    };

    /** P1/P2: one of the engine's proactive strategies (nearest-first, expiry-first), run on the observed network. */
    static TwinPolicy strategy(String strategyName) {
        RedistributionEngine engine = RedistributionEngine.withDefaultStrategies();
        engine.strategy(strategyName); // fail fast on an unknown name
        return new TwinPolicy() {
            @Override
            public String name() {
                return strategyName;
            }

            @Override
            public List<TransferOrder> plan(BloodNetwork observed, List<Shortfall> shortfalls, LocalDate today) {
                return engine.plan(strategyName, Objects.requireNonNull(observed), today);
            }
        };
    }
}
