package com.raktsetu.bench.load;

import com.raktsetu.bench.load.ApiClient.Response;
import com.raktsetu.bench.load.LatencyRecorder.Stats;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Load test of a running RaktSetu server, through its public API only.
 *
 * <p><b>Seed:</b> on a freshly started, empty server it creates the administrator, {@code centres} blood centres
 * with stock, {@code hospitalsPerCentre} hospitals per centre, and one staff account per hospital (each signing in
 * with its one-time password and choosing a new one, as a real person would).
 *
 * <p><b>Run:</b> for each concurrency level, that many virtual users sign in as hospital staff and call the API
 * as fast as they can (closed loop, no think time) for a fixed time. The mix is what hospital staff do: mostly
 * reading the overview, their requests, network stock and notifications, and 15% raising a blood request and then
 * cancelling it (two writes, which also keeps stock steady). Every call is timed.
 */
public final class LoadTest {

    /** Settings; see {@link #parse(String[])}. */
    record Options(URI url, int centres, int hospitalsPerCentre, List<Integer> levels, Duration duration,
                   Duration warmup, Path out) {
    }

    /** One hospital staff account created by the seed. */
    record Account(String username, String password, String hospitalId) {
    }

    /** Result of one concurrency level. */
    record LevelResult(int users, double seconds, List<Stats> stats) {
        Stats all() {
            return stats.getLast();
        }

        double throughput() {
            return all().calls() / seconds;
        }
    }

    private final Options options;
    private final SecureRandom secure = new SecureRandom();

    LoadTest(Options options) {
        this.options = options;
    }

    public static void main(String[] args) throws Exception {
        Options options = parse(args);
        LoadTest test = new LoadTest(options);
        List<Account> accounts = test.seed();
        List<LevelResult> results = test.run(accounts);
        String report = test.report(results);
        System.out.println(report);
        if (options.out() != null) {
            Files.createDirectories(options.out().toAbsolutePath().getParent());
            Files.writeString(options.out(), report);
            System.out.println("Written to " + options.out().toAbsolutePath());
        }
    }

    static Options parse(String[] args) {
        Map<String, String> a = new java.util.HashMap<>(Map.of("url", "http://localhost:8090", "centres", "20",
                "hospitals-per-centre", "2", "levels", "1,10,50,100,200", "seconds", "30", "warmup", "15"));
        for (int i = 0; i + 1 < args.length; i += 2) {
            if (!args[i].startsWith("--")) {
                throw new IllegalArgumentException("Expected --name value, got " + args[i]);
            }
            a.put(args[i].substring(2), args[i + 1]);
        }
        List<Integer> levels = java.util.Arrays.stream(a.get("levels").split(",")).map(String::trim)
                .map(Integer::parseInt).toList();
        return new Options(URI.create(a.get("url")), Integer.parseInt(a.get("centres")),
                Integer.parseInt(a.get("hospitals-per-centre")), levels,
                Duration.ofSeconds(Long.parseLong(a.get("seconds"))),
                Duration.ofSeconds(Long.parseLong(a.get("warmup"))),
                a.containsKey("out") ? Path.of(a.get("out")) : null);
    }

    // ------------------------------------------------------------------ seed

    List<Account> seed() throws IOException, InterruptedException {
        ApiClient admin = new ApiClient(options.url());
        if (!admin.get("/api/setup").orThrow("setup status").body().path("needsSetup").asBoolean()) {
            throw new IllegalStateException("The server already has accounts. Start it with an empty data folder.");
        }
        String adminPassword = password();
        admin.post("/api/setup", Map.of("username", "loadadmin", "displayName", "Load Test Admin",
                "password", adminPassword)).orThrow("setup");
        admin.signIn("loadadmin", adminPassword).orThrow("admin sign-in");

        SplittableRandom random = new SplittableRandom(42);
        List<Account> accounts = new ArrayList<>();
        long started = System.nanoTime();
        for (int c = 1; c <= options.centres(); c++) {
            double lat = 18.2 + random.nextDouble() * 0.8;
            double lon = 73.5 + random.nextDouble() * 1.0;
            String centre = admin.post("/api/facilities", Map.of("name", "Load Centre " + c,
                            "type", c % 10 == 1 ? "HUB" : "STORAGE", "latitude", lat, "longitude", lon, "address", ""))
                    .orThrow("add centre").body().path("id").asString();
            for (String group : List.of("O+", "O-", "A+", "B+")) {
                admin.post("/api/facilities/" + centre + "/units", Map.of("component", "RED_CELLS",
                        "group", group, "quantity", group.equals("O+") ? 400 : 60)).orThrow("add units");
            }
            admin.post("/api/facilities/" + centre + "/report", null).orThrow("report");
            for (int h = 1; h <= options.hospitalsPerCentre(); h++) {
                String hospital = admin.post("/api/facilities", Map.of("name", "Load Hospital " + c + "-" + h,
                                "type", "HOSPITAL", "latitude", lat + (random.nextDouble() - 0.5) * 0.1,
                                "longitude", lon + (random.nextDouble() - 0.5) * 0.1, "address", ""))
                        .orThrow("add hospital").body().path("id").asString();
                accounts.add(account(admin, hospital));
            }
        }
        System.out.printf(Locale.ROOT, "Seeded %d centres, %d hospitals and %d accounts in %.1f s%n",
                options.centres(), accounts.size(), accounts.size(), (System.nanoTime() - started) / 1e9);
        return accounts;
    }

    private Account account(ApiClient admin, String hospitalId) throws IOException, InterruptedException {
        String username = "load." + hospitalId.toLowerCase(Locale.ROOT);
        String oneTime = admin.post("/api/users", Map.of("username", username, "displayName", "Staff " + hospitalId,
                "role", "HOSPITAL_STAFF", "facilityId", hospitalId)).orThrow("create account")
                .body().path("oneTimePassword").asString();
        String chosen = password();
        ApiClient person = new ApiClient(options.url());
        person.signIn(username, oneTime).orThrow("first sign-in");
        person.post("/api/auth/password", Map.of("currentPassword", oneTime, "newPassword", chosen))
                .orThrow("password change");
        return new Account(username, chosen, hospitalId);
    }

    private String password() {
        byte[] bytes = new byte[9];
        secure.nextBytes(bytes);
        return "Lt" + HexFormat.of().formatHex(bytes) + "7";
    }

    // ------------------------------------------------------------------ run

    List<LevelResult> run(List<Account> accounts) throws Exception {
        System.out.println("Warming up for " + options.warmup().toSeconds() + " s...");
        level(accounts, Math.min(10, accounts.size()), options.warmup());
        List<LevelResult> results = new ArrayList<>();
        for (int users : options.levels()) {
            LevelResult r = level(accounts, users, options.duration());
            System.out.printf(Locale.ROOT, "%4d users: %8.1f req/s, p50 %6.1f ms, p95 %6.1f ms, p99 %6.1f ms, "
                            + "errors %d%n", users, r.throughput(), r.all().p50(), r.all().p95(), r.all().p99(),
                    r.all().errors());
            results.add(r);
        }
        return results;
    }

    private LevelResult level(List<Account> accounts, int users, Duration duration) throws Exception {
        LatencyRecorder recorder = new LatencyRecorder();
        // Sign everyone in first, so the timed part is steady-state traffic.
        List<VirtualUser> vus = new ArrayList<>();
        for (int i = 0; i < users; i++) {
            Account account = accounts.get(i % accounts.size());
            ApiClient client = new ApiClient(options.url());
            client.signIn(account.username(), account.password()).orThrow("sign-in");
            vus.add(new VirtualUser(client, account, recorder, new SplittableRandom(i)));
        }
        Instant start = Instant.now();
        Instant end = start.plus(duration);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> running = new ArrayList<>();
            for (VirtualUser vu : vus) {
                running.add(pool.submit(() -> vu.runUntil(end)));
            }
            for (Future<?> f : running) {
                f.get();
            }
        }
        double seconds = Duration.between(start, Instant.now()).toNanos() / 1e9;
        return new LevelResult(users, seconds, recorder.summary());
    }

    /** One simulated member of hospital staff, calling the API in a loop. */
    record VirtualUser(ApiClient client, Account account, LatencyRecorder recorder, SplittableRandom random) {

        void runUntil(Instant end) {
            while (Instant.now().isBefore(end)) {
                try {
                    step();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (IOException | RuntimeException e) {
                    recorder.record("transport-error", 0, false);
                }
            }
        }

        private void step() throws IOException, InterruptedException {
            int roll = random.nextInt(100);
            if (roll < 35) {
                timed("GET /api/summary", () -> client.get("/api/summary"));
            } else if (roll < 55) {
                timed("GET /api/requests", () -> client.get("/api/requests"));
            } else if (roll < 70) {
                timed("GET /api/stock", () -> client.get("/api/stock"));
            } else if (roll < 85) {
                timed("GET /api/notifications", () -> client.get("/api/notifications"));
            } else {
                Response raised = timed("POST /api/requests", () -> client.post("/api/requests", Map.of(
                        "hospitalId", account.hospitalId(), "component", "RED_CELLS", "group", "O+",
                        "quantity", 1, "urgency", "EMERGENCY")));
                if (raised.ok()) {
                    long id = raised.body().path("id").asLong();
                    timed("POST /api/requests/{id}/cancel",
                            () -> client.post("/api/requests/" + id + "/cancel", null));
                }
            }
        }

        private Response timed(String operation, Call call) throws IOException, InterruptedException {
            long t0 = System.nanoTime();
            Response r = call.run();
            recorder.record(operation, System.nanoTime() - t0, r.ok());
            return r;
        }
    }

    @FunctionalInterface
    interface Call {
        Response run() throws IOException, InterruptedException;
    }

    // ------------------------------------------------------------------ report

    String report(List<LevelResult> results) {
        StringBuilder md = new StringBuilder();
        Runtime rt = Runtime.getRuntime();
        md.append("# RaktSetu HTTP load test\n\n");
        md.append(String.format(Locale.ROOT, "- Network: %d centres, %d hospitals, one staff account per hospital%n",
                options.centres(), options.centres() * options.hospitalsPerCentre()));
        md.append(String.format(Locale.ROOT, "- Each level: %d s after a %d s warm-up; closed loop, no think time%n",
                options.duration().toSeconds(), options.warmup().toSeconds()));
        md.append("- Mix: 35% overview, 20% my requests, 15% network stock, 15% notifications, "
                + "15% raise an emergency request then cancel it\n");
        md.append(String.format(Locale.ROOT, "- Load generator: Java %s, %d cores, %s %s, same machine as the server%n%n",
                System.getProperty("java.version"), rt.availableProcessors(), System.getProperty("os.name"),
                System.getProperty("os.arch")));
        md.append("| Users | Throughput (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Max (ms) | Errors |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|\n");
        for (LevelResult r : results) {
            Stats s = r.all();
            md.append(String.format(Locale.ROOT, "| %d | %.0f | %.1f | %.1f | %.1f | %.0f | %d |%n", r.users(),
                    r.throughput(), s.p50(), s.p95(), s.p99(), s.max(), s.errors()));
        }
        LevelResult top = results.getLast();
        md.append(String.format(Locale.ROOT, "%n## By operation at %d users%n%n", top.users()));
        md.append("| Operation | Calls | p50 (ms) | p95 (ms) | p99 (ms) | Errors |\n|---|---:|---:|---:|---:|---:|\n");
        for (Stats s : top.stats()) {
            md.append(String.format(Locale.ROOT, "| %s | %d | %.1f | %.1f | %.1f | %d |%n", s.operation(),
                    s.calls(), s.p50(), s.p95(), s.p99(), s.errors()));
        }
        return md.toString();
    }
}
