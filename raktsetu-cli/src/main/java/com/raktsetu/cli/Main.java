package com.raktsetu.cli;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.LoadResult;
import com.raktsetu.persistence.AuditLogger;
import com.raktsetu.persistence.DataLoader;
import com.raktsetu.sim.SampleNetworkFactory;
import com.raktsetu.sim.Simulator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;

/**
 * Entry point. Parses the command line, creates sample data on first run, loads the network and starts the console.
 *
 * <pre>
 * java -jar raktsetu.jar [--data DIR] [--today YYYY-MM-DD] [--seed N] [--reset]
 * </pre>
 */
public final class Main {

    static final String USAGE = """
            Usage: java -jar raktsetu.jar [options]
              --data DIR          folder for the network's data files (default: raktsetu-data)
              --today YYYY-MM-DD  the date the app treats as today (default: the system date)
              --seed N            seed for the sample network and simulation (default: 42)
              --reset             replace the data folder's contents with a fresh sample network
              --help              show this help""";

    static final String AUDIT_FILE = "audit_log.csv";

    private record Options(Path dataDir, LocalDate today, long seed, boolean reset) {
    }

    private Main() {
    }

    public static void main(String[] args) {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        System.exit(run(args, in, out, LocalDate.now()));
    }

    /**
     * Runs the application and returns the process exit code.
     *
     * @param systemToday the date used when {@code --today} is not given; this is the only place the real clock enters
     */
    static int run(String[] args, BufferedReader in, PrintStream out, LocalDate systemToday) {
        Options options;
        try {
            options = parse(args, systemToday);
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
            out.println(USAGE);
            return 2;
        }
        if (options == null) {
            out.println(USAGE);
            return 0;
        }
        out.println("RaktSetu - expiry-aware blood redistribution network");
        try {
            DataLoader loader = new DataLoader(options.dataDir());
            Path auditFile = options.dataDir().resolve(AUDIT_FILE);
            if (options.reset() || !loader.hasData()) {
                if (Files.exists(auditFile)) {
                    Path backup = options.dataDir().resolve("audit_log-before-reset.csv");
                    Files.move(auditFile, backup, StandardCopyOption.REPLACE_EXISTING);
                    out.println("Previous audit log kept as " + backup.getFileName() + ".");
                }
                loader.save(SampleNetworkFactory.create(options.today(), options.seed()));
                out.println("Created a sample network in " + options.dataDir().toAbsolutePath() + " (seed "
                        + options.seed() + ", dated relative to " + options.today() + ").");
            }
            LoadResult loaded = loader.load();
            if (!loaded.problems().isEmpty()) {
                out.println("Skipped " + loaded.problems().size() + " problem row(s) while loading:");
                loaded.problems().forEach(p -> out.println("  - " + p));
            }
            AuditLogger audit = new AuditLogger(auditFile);
            new ConsoleApp(new ConsoleIO(in, out), loader, audit, loaded.data(), options.today(), options.seed())
                    .run();
            return 0;
        } catch (RaktSetuException | IOException e) {
            out.println("Could not start: " + e.getMessage());
            return 1;
        }
    }

    /** Returns null when help was requested. */
    private static Options parse(String[] args, LocalDate systemToday) {
        Path dataDir = Paths.get("raktsetu-data");
        LocalDate today = systemToday;
        long seed = Simulator.DEFAULT_SEED;
        boolean reset = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help", "-h" -> {
                    return null;
                }
                case "--reset" -> reset = true;
                case "--data" -> dataDir = Paths.get(value(args, ++i, "--data"));
                case "--today" -> today = ConsoleIO.parseDate(value(args, ++i, "--today"));
                case "--seed" -> {
                    String v = value(args, ++i, "--seed");
                    try {
                        seed = Long.parseLong(v);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("--seed needs a whole number, not '" + v + "'");
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        return new Options(dataDir, today, seed, reset);
    }

    private static String value(String[] args, int i, String option) {
        if (i >= args.length) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }
}
