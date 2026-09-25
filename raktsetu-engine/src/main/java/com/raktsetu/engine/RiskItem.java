package com.raktsetu.engine;

import com.raktsetu.domain.interfaces.Exportable;
import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Units of one component and group at one facility that are likely to expire before the facility can use them.
 *
 * @param facility      where the units are
 * @param component     component type
 * @param group         blood group
 * @param atRisk        the at-risk units, soonest-expiring first
 * @param usableStock   all usable units of this kind at the facility
 * @param averageDaily  recent average daily usage at the facility
 * @param today         the date the check was run
 */
public record RiskItem(Facility facility, ComponentType component, BloodGroup group, List<BloodComponent> atRisk,
                       int usableStock, double averageDaily, LocalDate today) implements Exportable {

    public RiskItem {
        atRisk = List.copyOf(atRisk);
        if (atRisk.isEmpty()) {
            throw new IllegalArgumentException("A risk item needs at least one unit");
        }
    }

    public long soonestDaysLeft() {
        return atRisk.get(0).daysToExpiry(today);
    }

    /** For example: "12 of 15 B+ PLATELETS at risk: soonest expires in 2 days; SC04 uses about 3.0 a day". */
    public String reason() {
        return String.format(Locale.ROOT, "%d of %d %s %s at risk: soonest expires in %d day%s; %s uses about %.1f a day",
                atRisk.size(), usableStock, group, component, soonestDaysLeft(), soonestDaysLeft() == 1 ? "" : "s",
                facility.getId(), averageDaily);
    }

    @Override
    public List<String> toCsvRow() {
        return List.of(today.toString(), facility.getId(), component.name(), group.label(),
                String.valueOf(atRisk.size()), String.valueOf(usableStock), String.valueOf(soonestDaysLeft()),
                String.format(Locale.ROOT, "%.2f", averageDaily));
    }
}
