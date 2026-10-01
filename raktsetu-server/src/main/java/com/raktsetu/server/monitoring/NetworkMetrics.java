package com.raktsetu.server.monitoring;

import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.NetworkService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import java.util.function.ToDoubleFunction;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Publishes the state of the blood network as metrics (for example {@code raktsetu.units.at_risk}), so a dashboard
 * such as Grafana can chart wastage risk over time and alert when stock reports go stale.
 *
 * <p>Gauges read an immutable {@link Snapshot} refreshed every 30 seconds, never the network itself: a metrics
 * scrape must not wait for, or slow down, staff using the site.
 */
@Component
public class NetworkMetrics implements MeterBinder {

    /** The numbers published, taken together at one moment. */
    record Snapshot(int unitsInStock, int urgentUnits, int atRiskUnits, int unitsWasted, int pendingTransfers,
                    int centres, int hospitals, int unverifiedCentres) {
        static final Snapshot EMPTY = new Snapshot(0, 0, 0, 0, 0, 0, 0, 0);
    }

    private final NetworkService network;
    private volatile Snapshot snapshot = Snapshot.EMPTY;

    public NetworkMetrics(NetworkService network) {
        this.network = network;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        gauge(registry, "raktsetu.units.in_stock", "Usable units on the shelves", Snapshot::unitsInStock);
        gauge(registry, "raktsetu.units.urgent", "Units expiring within 2 days", Snapshot::urgentUnits);
        gauge(registry, "raktsetu.units.at_risk", "Units likely to expire unused this week", Snapshot::atRiskUnits);
        gauge(registry, "raktsetu.units.wasted", "Units recorded as wasted", Snapshot::unitsWasted);
        gauge(registry, "raktsetu.transfers.pending", "Transfers awaiting approval", Snapshot::pendingTransfers);
        gauge(registry, "raktsetu.facilities.centres", "Blood centres", Snapshot::centres);
        gauge(registry, "raktsetu.facilities.hospitals", "Hospitals", Snapshot::hospitals);
        gauge(registry, "raktsetu.facilities.unverified", "Centres whose stock report is over 3 days old",
                Snapshot::unverifiedCentres);
        refresh();
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void refresh() {
        Summary s = network.summary();
        List<FacilityView> facilities = network.facilities();
        int unverified = (int) facilities.stream()
                .filter(f -> !"HOSPITAL".equals(f.type()) && "UNVERIFIED".equals(f.freshness()))
                .count();
        snapshot = new Snapshot(s.unitsInStock(), s.urgentUnits(), s.atRiskUnits(), s.unitsWasted(),
                s.pendingTransfers(), s.storingFacilities(), s.hospitals(), unverified);
    }

    Snapshot snapshot() {
        return snapshot;
    }

    private void gauge(MeterRegistry registry, String name, String description, ToDoubleFunction<Snapshot> value) {
        Gauge.builder(name, this, m -> value.applyAsDouble(m.snapshot)).description(description).register(registry);
    }
}
