package com.raktsetu.domain.model;

import java.util.Objects;

/**
 * The answer to "can this unit be given to that patient?", with the reason shown to staff.
 *
 * @param level  how safe the transfusion is
 * @param reason a short explanation suitable for the console or dashboard
 */
public record CompatibilityResult(Compatibility level, String reason) {

    public CompatibilityResult {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(reason, "reason");
    }

    public boolean isAllowed() {
        return level.isAllowed();
    }

    static CompatibilityResult of(Compatibility level, String reason) {
        return new CompatibilityResult(level, reason);
    }
}
