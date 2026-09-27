package com.raktsetu.sim;

import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.ComponentType;

import java.time.LocalDate;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

/**
 * Seeded randomness with the few distributions the simulators need. The same seed always produces the same sequence,
 * which makes every run reproducible.
 */
public final class RandomSource {

    /**
     * Approximate share of each blood group in India (O+ and B+ are the most common; RhD-negative groups are about 5%
     * in total). Illustrative values for simulation only.
     */
    public static final Map<BloodGroup, Double> GROUP_SHARE = Map.of(
            BloodGroup.O_POS, 0.36, BloodGroup.B_POS, 0.31, BloodGroup.A_POS, 0.21, BloodGroup.AB_POS, 0.07,
            BloodGroup.O_NEG, 0.02, BloodGroup.B_NEG, 0.015, BloodGroup.A_NEG, 0.01, BloodGroup.AB_NEG, 0.005);

    private final Random random;

    public RandomSource(long seed) {
        this.random = new Random(seed);
    }

    /**
     * An independent random source for one purpose ("demand", "supply", "reporting") of one replication. Different
     * streams never share numbers, and every policy run of the same replication gets the same streams (common random
     * numbers).
     */
    public static RandomSource stream(long seed, int replication, String purpose) {
        long mixed = seed * 0x9E3779B97F4A7C15L + replication * 0xC2B2AE3D27D4EB4FL + purpose.hashCode();
        return new RandomSource(new SplittableRandom(mixed).nextLong());
    }

    /** A Poisson-distributed count with the given mean (Knuth's method for small means, normal approximation above). */
    public int poisson(double mean) {
        if (mean <= 0) {
            return 0;
        }
        if (mean > 30) {
            return Math.max(0, (int) Math.round(mean + Math.sqrt(mean) * random.nextGaussian()));
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
    public BloodGroup bloodGroup() {
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
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    public double nextDouble() {
        return random.nextDouble();
    }

    /** A random age in days for a unit that is still usable today. */
    public int usableAge(ComponentType type) {
        int shelfLife = type.newUnit("AGE", BloodGroup.O_POS, LocalDate.EPOCH).getShelfLifeDays();
        return random.nextInt(shelfLife);
    }
}
