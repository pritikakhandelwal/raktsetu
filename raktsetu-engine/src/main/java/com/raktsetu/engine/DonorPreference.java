package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodComponent;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.CompatibilityResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The order in which donor groups are tried for a recipient: safest match first, and among equally safe groups, the
 * most plentiful first so that scarce RhD-negative and O-negative units are kept for patients who need them.
 */
public final class DonorPreference {

    private static final LocalDate ANY_DATE = LocalDate.of(2000, 1, 1);

    private DonorPreference() {
    }

    /** Every allowed donor group for this component and recipient, most preferred first. */
    public static List<BloodGroup> forRecipient(ComponentType component, BloodGroup recipient) {
        Map<BloodGroup, CompatibilityResult> allowed = new EnumMap<>(BloodGroup.class);
        for (BloodGroup donor : BloodGroup.values()) {
            BloodComponent probe = component.newUnit("PROBE", donor, ANY_DATE);
            CompatibilityResult r = probe.compatibilityWith(recipient);
            if (r.isAllowed()) {
                allowed.put(donor, r);
            }
        }
        List<BloodGroup> order = new ArrayList<>(allowed.keySet());
        order.sort(Comparator.comparing((BloodGroup g) -> allowed.get(g).level())
                .thenComparingInt(DonorPreference::scarcity)
                .thenComparing(Comparator.naturalOrder()));
        return order;
    }

    /** 0 = plentiful; higher = keep for patients who need it. O-negative is the scarcest universal donor. */
    static int scarcity(BloodGroup g) {
        return (g.isRhPositive() ? 0 : 2) + (g.abo() == BloodGroup.Abo.O ? 1 : 0);
    }
}
