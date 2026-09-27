package com.raktsetu.server.security;

import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.NetworkService;
import com.raktsetu.server.RaktSetuProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Creates the demo accounts on first start: one administrator, and one staff account per centre and per hospital
 * (username = facility ID in lower case, for example {@code sc04} or {@code h01}).
 *
 * <p>All demo accounts share one password: {@code raktsetu.demo-password} if set, otherwise a random one. The sign-in
 * details are written to {@code demo-accounts.txt} in the data folder, never to the log.
 */
@Component
public class DemoAccounts {

    public static final String FILE = "demo-accounts.txt";
    private static final Logger LOG = LoggerFactory.getLogger(DemoAccounts.class);
    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public DemoAccounts(UserRepository users, NetworkService network, PasswordEncoder encoder,
                        RaktSetuProperties properties) {
        if (users.count() > 0) {
            return;
        }
        String password = properties.demoPassword() != null && !properties.demoPassword().isBlank()
                ? properties.demoPassword() : randomPassword();
        String hash = encoder.encode(password);
        List<AppUser> created = new ArrayList<>();
        created.add(new AppUser("admin", "Network administrator", Role.ADMIN, null));
        for (FacilityView f : network.facilities()) {
            Role role = f.type().equals("HOSPITAL") ? Role.HOSPITAL_STAFF : Role.CENTRE_STAFF;
            String name = f.name().replace(" (sample)", "").replace(" (synthetic)", "");
            created.add(new AppUser(f.id().toLowerCase(Locale.ROOT), "Staff, " + name, role, f.id()));
        }
        created.forEach(u -> users.insert(u, hash));
        Path file = write(properties.dataDir(), created, password);
        LOG.info("Created {} demo accounts; sign-in details are in {}", created.size(), file.toAbsolutePath());
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static Path write(Path dataDir, List<AppUser> accounts, String password) {
        List<String> lines = new ArrayList<>();
        lines.add("RaktSetu demo accounts. Every account uses the same password:");
        lines.add("");
        lines.add("    " + password);
        lines.add("");
        lines.add(String.format(Locale.ROOT, "%-10s %-15s %-8s %s", "USERNAME", "ROLE", "WHERE", "NAME"));
        for (AppUser u : accounts) {
            lines.add(String.format(Locale.ROOT, "%-10s %-15s %-8s %s", u.username(), u.role(),
                    u.facilityId() == null ? "-" : u.facilityId(), u.displayName()));
        }
        lines.add("");
        lines.add("For demonstration only. Keep this file private; delete the data folder to start afresh.");
        try {
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve(FILE);
            Files.write(file, lines, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + FILE, e);
        }
    }
}
