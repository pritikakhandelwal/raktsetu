package com.raktsetu.engine;

import com.raktsetu.domain.model.BloodRequest;

import java.util.List;

/** A request that was met in full, and how. */
public record RequestOutcome(BloodRequest request, List<Allocation> allocations) {

    public RequestOutcome {
        allocations = List.copyOf(allocations);
    }

    public int unitsIssued() {
        return allocations.stream().mapToInt(a -> a.units().size()).sum();
    }

    public boolean usedSubstitutes() {
        return allocations.stream().anyMatch(Allocation::isSubstitute);
    }
}
