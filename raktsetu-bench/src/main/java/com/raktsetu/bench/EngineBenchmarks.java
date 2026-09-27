package com.raktsetu.bench;

import com.raktsetu.domain.exception.RaktSetuException;
import com.raktsetu.domain.model.BloodGroup;
import com.raktsetu.domain.model.BloodRequest;
import com.raktsetu.domain.model.ComponentType;
import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.TransferOrder;
import com.raktsetu.domain.model.Urgency;
import com.raktsetu.engine.Allocation;
import com.raktsetu.engine.BloodNetwork;
import com.raktsetu.engine.RequestOutcome;
import com.raktsetu.engine.RequestService;
import com.raktsetu.engine.RiskItem;
import com.raktsetu.engine.strategy.ExpiryFirstStrategy;
import com.raktsetu.engine.strategy.RedistributionEngine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.time.LocalDate;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * How the engine's three core operations scale with the size of the network. Each network has two hospitals and
 * 300 units per centre, so 1,000 centres means 3,000 facilities and 300,000 units.
 *
 * <ul>
 *   <li>{@link #allocateEmergency}: serve one emergency request for 2 red-cell units at a random hospital, then
 *       put the units back so stock stays the same between calls.</li>
 *   <li>{@link #analyseRisk}: find every unit likely to expire unused this week.</li>
 *   <li>{@link #planTransfers}: risk analysis plus an Expiry-First transfer plan for the whole network.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgsAppend = {"-Xmx4g"})
public class EngineBenchmarks {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    @Param({"50", "200", "500", "1000"})
    public int centres;

    private BloodNetwork network;
    private RequestService requests;
    private RedistributionEngine engine;
    private List<String> hospitals;
    private SplittableRandom random;

    @Setup(Level.Trial)
    public void build() throws RaktSetuException {
        network = new BloodNetwork(SyntheticNetwork.generate(SyntheticNetwork.Spec.ofCentres(centres), TODAY),
                new NoOpAuditTrail());
        network.removeExpired(TODAY);
        requests = new RequestService(network);
        engine = RedistributionEngine.withDefaultStrategies();
        hospitals = network.facilities().stream().filter(f -> !f.canStore()).map(Facility::getId).toList();
        random = new SplittableRandom(7);
    }

    @Benchmark
    public int allocateEmergency() throws RaktSetuException {
        String hospital = hospitals.get(random.nextInt(hospitals.size()));
        RequestOutcome outcome = requests.handle(new BloodRequest(hospital, ComponentType.RED_CELLS, BloodGroup.O_POS,
                2, Urgency.EMERGENCY), TODAY);
        int issued = 0;
        for (Allocation a : outcome.allocations()) {
            network.returnUnits(a.source().getId(), a.units(), "benchmark", TODAY);
            issued += a.units().size();
        }
        return issued;
    }

    @Benchmark
    public List<RiskItem> analyseRisk() {
        return engine.risks(network, TODAY);
    }

    @Benchmark
    public List<TransferOrder> planTransfers() {
        return engine.plan(ExpiryFirstStrategy.NAME, network, TODAY);
    }
}
