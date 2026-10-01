package com.raktsetu.bench;

import com.raktsetu.bench.load.LoadTest;

import java.util.Arrays;

/**
 * Entry point of {@code raktsetu-bench.jar}.
 *
 * <pre>
 * java -jar raktsetu-bench.jar engine [JMH options]      engine benchmarks (JMH), e.g. -rf json -rff out.json
 * java -jar raktsetu-bench.jar load [--url ... --levels 1,10,50 --seconds 30 --out results.md]
 * </pre>
 */
public final class BenchMain {

    private BenchMain() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "" : args[0];
        String[] rest = args.length == 0 ? args : Arrays.copyOfRange(args, 1, args.length);
        switch (mode) {
            case "engine" -> org.openjdk.jmh.Main.main(rest);
            case "load" -> LoadTest.main(rest);
            default -> {
                System.err.println("Usage: java -jar raktsetu-bench.jar engine [JMH options]");
                System.err.println("       java -jar raktsetu-bench.jar load [--url URL] [--centres N] "
                        + "[--levels 1,10,50] [--seconds S] [--out FILE]");
                System.exit(2);
            }
        }
    }
}
