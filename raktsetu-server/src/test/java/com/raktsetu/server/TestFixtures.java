package com.raktsetu.server;

import com.raktsetu.domain.model.Facility;
import com.raktsetu.domain.model.NetworkData;
import com.raktsetu.server.db.NetworkRepository;
import com.raktsetu.server.db.StateRepository;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.Role;
import com.raktsetu.server.security.UserRepository;
import com.raktsetu.sim.SampleNetworkFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Test data only: loads the simulator's sample network (1 hub, 4 storage centres, 6 hospitals) and one account per
 * facility plus {@code admin}, all with {@link #PASSWORD}, into a test class's own empty database. The real website
 * starts empty and is set up through the first-run page.
 */
final class TestFixtures {

    static final String PASSWORD = "fixture-pass-2026";
    static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    private TestFixtures() {
    }

    static synchronized void sampleNetwork(ApplicationContext context) {
        NetworkRepository repo = context.getBean(NetworkRepository.class);
        if (!repo.isEmpty()) {
            return;
        }
        StateRepository state = context.getBean(StateRepository.class);
        UserRepository users = context.getBean(UserRepository.class);
        String hash = context.getBean(PasswordEncoder.class).encode(PASSWORD);
        NetworkData data = SampleNetworkFactory.create(DAY, 42);
        context.getBean(TransactionTemplate.class).executeWithoutResult(s -> {
            repo.seed(data);
            state.put(StateRepository.NEXT_UNIT_SERIAL, String.valueOf(data.nextUnitSerial()));
            users.insert(new AppUser("admin", "Test administrator", Role.ADMIN, null), hash, false, DAY);
            for (Facility f : data.facilities()) {
                Role role = f.canStore() ? Role.CENTRE_STAFF : Role.HOSPITAL_STAFF;
                users.insert(new AppUser(f.getId().toLowerCase(Locale.ROOT), "Staff " + f.getId(), role, f.getId()),
                        hash, false, DAY);
            }
        });
        context.getBean(NetworkService.class).reloadAndReprocessDay();
    }
}
