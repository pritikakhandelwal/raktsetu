package com.raktsetu.server;

import com.raktsetu.server.ApiModels.AddUnitsInput;
import com.raktsetu.server.ApiModels.BloodRequestInput;
import com.raktsetu.server.ApiModels.Summary;
import com.raktsetu.server.db.AuditRepository;
import com.raktsetu.server.db.NetworkRepository;
import com.raktsetu.server.db.StateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The database is the source of truth: a restarted service sees exactly what the previous one wrote. Each test builds
 * its own service instances ("restarts") on the shared database; only one is used at a time, as in production.
 */
@SpringBootTest
@DirtiesContext
class DatabaseTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        TestDatabase.register(registry, "dbtest");
        registry.add("raktsetu.today", () -> "2026-10-01");
    }

    @Autowired
    NetworkRepository networkRepo;
    @Autowired
    AuditRepository auditRepo;
    @Autowired
    StateRepository state;
    @Autowired
    TransactionTemplate tx;
    @Autowired
    JdbcClient jdbc;

    private NetworkService restart() throws Exception {
        return new NetworkService(new RaktSetuProperties(Path.of("unused"), LocalDate.of(2026, 10, 1), 42L),
                networkRepo, auditRepo, state, tx);
    }

    @Test
    void aRestartedServiceSeesEverythingThatWasWritten() throws Exception {
        NetworkService service = restart();
        service.addUnits("SC01", new AddUnitsInput("PLATELETS", "AB-", null, 3));
        service.request(new BloodRequestInput("H01", "RED_CELLS", "O+", 2, "EMERGENCY"));
        service.recordReport("SC03");
        Summary before = service.summary();

        NetworkService restarted = restart();
        Summary after = restarted.summary();

        assertThat(after).isEqualTo(before);
        assertThat(restarted.facilityStock("SC01").lines())
                .anySatisfy(l -> assertThat(l.group()).isEqualTo("AB-"));
        assertThat(restarted.facilities()).filteredOn(f -> f.id().equals("SC03"))
                .singleElement().satisfies(f -> assertThat(f.freshness()).isEqualTo("FRESH"));
        assertThat(auditRepo.verify().intact()).isTrue();
    }

    @Test
    void newUnitIdsContinueAfterARestart() throws Exception {
        String first = restart().addUnits("SC02", new AddUnitsInput("PLASMA", "A+", null, 1)).unitIds().get(0);
        String next = restart().addUnits("SC02", new AddUnitsInput("PLASMA", "A+", null, 1)).unitIds().get(0);
        assertThat(next).isGreaterThan(first);
    }

    @Test
    void editingAnAuditRowIsDetected() {
        jdbc.sql("update audit_log set note = 'edited' where seq = 1").update();
        AuditRepository.Verification v = auditRepo.verify();
        assertThat(v.intact()).isFalse();
        assertThat(v.message()).contains("entry 1 was modified");
        jdbc.sql("delete from audit_log").update(); // keep other tests independent of this edit
    }
}
