package com.raktsetu.sim;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;

import java.util.Map;
import java.util.Random;

/**
 * Seeded randomness with the few distributions the simulator needs. The same seed always produces the same sequence,
 * which makes every run reproducible.
 */
final class RandomSource {

    /**
     * Approximate share of each blood group in India (O+ and B+ are the most common; RhD-negative groups are about 5%
     * in total). Illustrative values for simulation only.
     */
    static final Map<BloodGroup, Double> GROUP_SHARE = Map.of(
            BloodGroup.O_POS, 0.36, BloodGroup.B_POS, 0.31, BloodGroup.A_POS, 0.21, BloodGroup.AB_POS, 0.07,
            BloodGroup.O_NEG, 0.02, BloodGroup.B_NEG, 0.015, BloodGroup.A_NEG, 0.01, BloodGroup.AB_NEG, 0.005);

    private final Random random;

    RandomSource(long seed) {
        this.random = new Random(seed);
    }

    /** A Poisson-distributed count with the given mean (Knuth's method; fine for the small means used here). */
    int poisson(double mean) {
        if (mean <= 0) {
            return 0;
        }
        double limit = Math.exp(-mean);
        double product = random.nextDouble();
        int k = 0;
        while (product > limit) {
            product *= random.nextDouble();
            k++;
        }
        return k;
    }

    /** A blood group drawn from {@link #GROUP_SHARE}. */
    BloodGroup bloodGroup() {
        double r = random.nextDouble();
        double cumulative = 0;
        for (BloodGroup g : BloodGroup.values()) {
            cumulative += GROUP_SHARE.get(g);
            if (r < cumulative) {
                return g;
            }
        }
        return BloodGroup.O_POS;
    }

    /** A uniform integer in {@code [0, bound)}. */
    int nextInt(int bound) {
        return random.nextInt(bound);
    }

    double nextDouble() {
        return random.nextDouble();
    }

    /** A random age in days for a unit that is still usable today. */
    int usableAge(ComponentType type) {
        int shelfLife = type.newUnit("AGE", BloodGroup.O_POS, java.time.LocalDate.EPOCH).getShelfLifeDays();
        return random.nextInt(shelfLife);
    }
}
