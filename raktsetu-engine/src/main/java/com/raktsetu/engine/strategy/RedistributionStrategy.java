package com.raktsetu.engine.strategy;

import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.RiskItem;

import java.time.LocalDate;
import java.util.List;

/**
 * A way of deciding where at-risk units should go. Strategies only propose transfers; staff approve them.
 * Implementations are interchangeable, which lets the console compare them on the same data.
 */
public interface RedistributionStrategy {

    /** Short name used in menus and reports, for example {@code expiry-first}. */
    String name();

    /** One sentence for the console. */
    String description();

    /** Proposed transfers for the given at-risk stock. Does not change the network. */
    List<TransferOrder> plan(BloodNetwork network, List<RiskItem> risks, LocalDate today);
}
