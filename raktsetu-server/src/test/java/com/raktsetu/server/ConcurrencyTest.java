package com.raktsetu.server;

import com.raktsetu.domain.exception.InsufficientStockException;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many users at once: readers run in parallel with changes, and changes never interleave. After every thread has
 * raised and cancelled its requests, stock is exactly as it started and the audit log is intact.
 */
@SpringBootTest
@DirtiesContext
class ConcurrencyTest {

    private static final Path DATA;

    static {
        try {
            DATA = Files.createTempDirectory("raktsetu-concurrency-test");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "concurrencytest");
        registry.add("raktsetu.data-dir", DATA::toString);
        registry.add("raktsetu.today", () -> "2026-10-01");
        registry.add("raktsetu.routing-url", () -> "");
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    NetworkService network;

    @Autowired
    WorkflowService workflow;

    @BeforeEach
    void setUp() {
        TestFixtures.sampleNetwork(context);
    }

    @Test
    void parallelRequestsCancelsAndReadsLeaveTheNetworkConsistent() throws Exception {
        int unitsBefore = network.summary().unitsInStock();
        int threads = 16;
        int rounds = 15;
        List<String> hospitals = List.of("H01", "H02", "H03", "H04", "H05");
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
        AtomicInteger served = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<?>> running = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String hospital = hospitals.get(t % hospitals.size());
                AppUser staff = new AppUser(hospital.toLowerCase(), "Staff", Role.HOSPITAL_STAFF, hospital);
                boolean writer = t % 2 == 0;
                running.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < rounds; i++) {
                        try {
                            if (writer) {
                                var raised = workflow.raise(new BloodRequestInput(hospital, "RED_CELLS", "O+", 1,
                                        "EMERGENCY"), staff);
                                workflow.cancel(raised.id(), staff);
                                served.incrementAndGet();
                            } else {
                                network.summary();
                                network.facilityStock("HUB01");
                                workflow.requests(staff);
                                network.risks();
                            }
                        } catch (InsufficientStockException e) {
                            refused.incrementAndGet(); // allowed: all O+ may be reserved at that moment
                        } catch (Throwable e) {
                            failures.add(e);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : running) {
                f.get();
            }
        }

        assertThat(failures).isEmpty();
        assertThat(served.get() + refused.get()).isEqualTo(threads / 2 * rounds);
        assertThat(served.get()).isPositive();
        assertThat(network.summary().unitsInStock()).isEqualTo(unitsBefore);
        assertThat(network.audit(1).intact()).isTrue();
    }
}
