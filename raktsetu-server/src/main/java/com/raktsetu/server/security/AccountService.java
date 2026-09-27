package com.raktsetu.server.security;

import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.NetworkService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Accounts: the one-time setup of the first administrator, accounts created by administrators with a one-time
 * password, password changes and resets, and enabling or disabling accounts.
 */
@Service
public class AccountService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,29}");

    /** An account as administrators see it (never the password hash). */
    public record UserView(String username, String displayName, Role role, String facilityId, boolean enabled,
                           boolean mustChangePassword, LocalDate createdOn) {
    }

    /** A new or reset account with its one-time password, shown once. */
    public record NewAccount(UserView user, String oneTimePassword) {
    }

    public record SetupInput(String username, String displayName, String password) {
    }

    public record UserInput(String username, String displayName, String role, String facilityId) {
    }

    public record PasswordInput(String currentPassword, String newPassword) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final NetworkService network;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordEncoder encoder, NetworkService network, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.network = network;
        this.clock = clock;
    }

    public boolean needsSetup() {
        return users.count() == 0;
    }

    /** Creates the first administrator. Only possible while there are no accounts at all. */
    public synchronized UserView setup(SetupInput input) {
        if (!needsSetup()) {
            throw new AccessDeniedException("RaktSetu is already set up; ask an administrator for an account");
        }
        if (input == null) {
            throw new IllegalArgumentException("Username, name and password are required");
        }
        String username = username(input.username());
        String name = displayName(input.displayName());
        Passwords.check(input.password(), username);
        AppUser admin = new AppUser(username, name, Role.ADMIN, null);
        users.insert(admin, encoder.encode(input.password()), false, LocalDate.now(clock));
        return view(users.find(username).orElseThrow());
    }

    public List<UserView> list() {
        return users.all().stream().map(AccountService::view).toList();
    }

    /** Creates an account with a one-time password; the person must choose their own at first sign-in. */
    public synchronized NewAccount create(UserInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Username, name and role are required");
        }
        String username = username(input.username());
        if (users.find(username).isPresent()) {
            throw new IllegalArgumentException("The username '" + username + "' is already taken");
        }
        String name = displayName(input.displayName());
        Role role;
        try {
            role = Role.valueOf(input.role() == null ? "" : input.role().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Role must be ADMIN, CENTRE_STAFF or HOSPITAL_STAFF", e);
        }
        String facilityId = null;
        if (role != Role.ADMIN) {
            facilityId = input.facilityId() == null ? "" : input.facilityId().trim();
            String id = facilityId;
            FacilityView f = network.facilities().stream().filter(x -> x.id().equals(id)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Choose the facility this person works at"));
            boolean hospital = f.type().equals("HOSPITAL");
            if (hospital != (role == Role.HOSPITAL_STAFF)) {
                throw new IllegalArgumentException(role == Role.HOSPITAL_STAFF
                        ? "Hospital staff must belong to a hospital" : "Centre staff must belong to a blood centre");
            }
        }
        String oneTime = Passwords.oneTime();
        users.insert(new AppUser(username, name, role, facilityId), encoder.encode(oneTime), true,
                LocalDate.now(clock));
        return new NewAccount(view(users.find(username).orElseThrow()), oneTime);
    }

    /** Gives an account a new one-time password, for example when someone has forgotten theirs. */
    public synchronized NewAccount resetPassword(String username) {
        UserRepository.Account account = account(username);
        String oneTime = Passwords.oneTime();
        users.setPassword(account.user().username(), encoder.encode(oneTime), true);
        return new NewAccount(view(account(username)), oneTime);
    }

    /** Enables or disables an account. You cannot disable yourself or the last active administrator. */
    public synchronized UserView setEnabled(String username, boolean enabled, AppUser actingAdmin) {
        UserRepository.Account account = account(username);
        if (!enabled) {
            if (account.user().username().equals(actingAdmin.username())) {
                throw new IllegalArgumentException("You cannot disable your own account");
            }
            if (account.user().isAdmin() && account.enabled() && users.enabledAdmins() <= 1) {
                throw new IllegalArgumentException("Keep at least one active administrator");
            }
        }
        users.setEnabled(account.user().username(), enabled);
        return view(account(username));
    }

    /** Changes the signed-in person's own password. */
    public synchronized void changePassword(String username, PasswordInput input) {
        UserRepository.Account account = account(username);
        if (input == null || input.currentPassword() == null
                || !encoder.matches(input.currentPassword(), account.passwordHash())) {
            throw new IllegalArgumentException("Your current password is not correct");
        }
        Passwords.check(input.newPassword(), username);
        if (input.newPassword().equals(input.currentPassword())) {
            throw new IllegalArgumentException("Choose a password different from the current one");
        }
        users.setPassword(username, encoder.encode(input.newPassword()), false);
    }

    public boolean mustChangePassword(String username) {
        return users.find(username).map(UserRepository.Account::mustChangePassword).orElse(false);
    }

    private UserRepository.Account account(String username) {
        return users.find(username == null ? "" : username.trim())
                .orElseThrow(() -> new IllegalArgumentException("No account '" + username + "'"));
    }

    private static String username(String raw) {
        String u = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(u).matches()) {
            throw new IllegalArgumentException("Usernames are 3-30 characters: lower-case letters, digits, '.', '_' "
                    + "or '-', starting with a letter or digit");
        }
        return u;
    }

    private static String displayName(String raw) {
        String n = raw == null ? "" : raw.trim();
        if (n.isEmpty() || n.length() > 100) {
            throw new IllegalArgumentException("Enter the person's name (up to 100 characters)");
        }
        return n;
    }

    private static UserView view(UserRepository.Account a) {
        AppUser u = a.user();
        return new UserView(u.username(), u.displayName(), u.role(), u.facilityId(), a.enabled(),
                a.mustChangePassword(), a.createdOn());
    }
}
