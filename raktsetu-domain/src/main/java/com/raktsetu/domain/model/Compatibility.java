package com.raktsetu.domain.model;

/** How safe a donor unit is for a recipient, from best to worst. */
public enum Compatibility {
    /** Same group as the recipient (for the rules that matter for this component). */
    IDENTICAL,
    /** A different group that is clinically safe, for example O- red cells to an A+ patient. */
    COMPATIBLE,
    /** Permitted, but a clinician should be told; for example ABO-non-identical platelets. */
    ALLOWED_WITH_WARNING,
    /** Must never be issued. */
    INCOMPATIBLE;

    public boolean isAllowed() {
        return this != INCOMPATIBLE;
    }
}
