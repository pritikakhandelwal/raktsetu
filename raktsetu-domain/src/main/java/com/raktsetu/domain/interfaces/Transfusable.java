package com.raktsetu.domain.interfaces;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.CompatibilityResult;

/** A blood product that can be checked against a recipient's blood group. */
public interface Transfusable {

    /** Full answer with a reason, for display. Never throws. */
    CompatibilityResult compatibilityWith(BloodGroup recipient);

    default boolean canTransfuseTo(BloodGroup recipient) {
        return compatibilityWith(recipient).isAllowed();
    }
}
