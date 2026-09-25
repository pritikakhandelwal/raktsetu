package com.raktsetu.engine;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.exception.UnitExpiredException;
import com.raktsetu.domain.model.AuditEvent;
import com.raktsetu.domain.model.AuditEventType;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.TransferStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Carries out approved transfers. Every order is checked again at approval time, because units may have been issued or
 * may have expired since the plan was made; if any check fails, nothing moves.
 */
public final class TransferService {

    private final BloodNetwork network;

    public TransferService(BloodNetwork network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    /**
     * Moves the order's units and records the transfer.
     *
     * @throws UnitExpiredException if a unit has expired since planning
     * @throws RaktSetuException    if a unit is no longer at the source, or the order is not proposed
     */
    public void approve(TransferOrder order, LocalDate today) throws RaktSetuException {
        if (order.getStatus() != TransferStatus.PROPOSED) {
            throw new RaktSetuException("Transfer " + order.getId() + " is already " + order.getStatus());
        }
        Facility source = order.getSource();
        Facility destination = order.getDestination();
        for (BloodComponent unit : order.getUnits()) {
            if (source.getInventory().find(unit.getId()).isEmpty()) {
                throw new RaktSetuException("Unit " + unit.getId() + " is no longer at " + source
                        + "; plan again before approving");
            }
            if (unit.isExpired(today)) {
                throw new UnitExpiredException(unit.getId(), unit.getExpiryDate());
            }
        }
        Map<String, List<BloodComponent>> byKind = new LinkedHashMap<>();
        for (BloodComponent unit : order.getUnits()) {
            source.getInventory().take(unit.getId());
            destination.getInventory().add(unit);
            byKind.computeIfAbsent(unit.getType() + "/" + unit.getBloodGroup(), k -> new ArrayList<>()).add(unit);
        }
        for (List<BloodComponent> units : byKind.values()) {
            BloodComponent first = units.get(0);
            network.audit().record(new AuditEvent(today, AuditEventType.TRANSFERRED, source.getId(),
                    destination.getId(), first.getType(), first.getBloodGroup(),
                    units.stream().map(BloodComponent::getId).toList(), order.getId() + ": " + order.getReason()));
        }
        order.markCompleted();
    }

    public void reject(TransferOrder order) {
        order.reject();
    }
}
