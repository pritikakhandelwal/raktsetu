package com.raktsetu.server.security;

import com.raktsetu.server.ApiModels.FacilityView;
import com.raktsetu.server.NetworkService;
import com.raktsetu.server.alerts.AlertOutbox;
import com.raktsetu.server.db.NotificationRepository;
import com.raktsetu.server.alerts.PhoneNumbers;
import com.raktsetu.server.privacy.PrivacyProperties;
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
                           String phone, boolean alertsEnabled, boolean mustChangePassword, LocalDate createdOn,
                           LocalDate anonymisedOn, boolean pending) {
    }

    /** A new or reset account with its one-time password, shown once. */
    public record NewAccount(UserView user, String oneTimePassword) {
    }

    public record SetupInput(String username, String displayName, String password) {
    }

    /** @param phone optional mobile number; if given, the person gets urgent alerts until they turn them off */
    public record UserInput(String username, String displayName, String role, String facilityId, String phone) {
    }

    /** A request to join, from the sign-in page. Only blood centre and hospital staff accounts can be requested. */
    public record SignupInput(String username, String displayName, String password, String role, String facilityId) {
    }

    /** A facility someone can ask to join, as listed on the public request form. */
    public record JoinableFacility(String id, String name, String type) {
    }

    /** Requests waiting beyond this many are refused, so a flood of requests cannot fill the database. */
    static final int MAX_PENDING = 100;

    /** A person's own alert settings. */
    public record ContactInput(String phone, Boolean alertsEnabled) {
    }

    public record PasswordInput(String currentPassword, String newPassword) {
    }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final NetworkService network;
    private final Clock clock;
    private final LoginGuard guard;
    private final AlertOutbox outbox;
    private final PrivacyProperties privacy;
    private final NotificationRepository notifications;

    public AccountService(UserRepository users, PasswordEncoder encoder, NetworkService network, Clock clock,
                          LoginGuard guard, AlertOutbox outbox, PrivacyProperties privacy,
                          NotificationRepository notifications) {
        this.users = users;
        this.encoder = encoder;
        this.network = network;
        this.clock = clock;
        this.guard = guard;
        this.outbox = outbox;
        this.privacy = privacy;
        this.notifications = notifications;
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
        String facilityId = role == Role.ADMIN ? null : facilityFor(role, input.facilityId());
        String phone = input.phone() == null || input.phone().isBlank() ? null : PhoneNumbers.normalise(input.phone());
        String oneTime = Passwords.oneTime();
        users.insert(new AppUser(username, name, role, facilityId), encoder.encode(oneTime), true,
                LocalDate.now(clock));
        if (phone != null) {
            users.setContact(username, phone, true);
            users.recordContactBasis(username, "EMPLOYER", clock.millis(), privacy.noticeVersion());
        }
        return new NewAccount(view(users.find(username).orElseThrow()), oneTime);
    }

    /** Gives an account a new one-time password, for example when someone has forgotten theirs. */
    public synchronized NewAccount resetPassword(String username) {
        UserRepository.Account account = account(username);
        String oneTime = Passwords.oneTime();
        users.setPassword(account.user().username(), encoder.encode(oneTime), true);
        guard.clear(account.user().username());
        return new NewAccount(view(account(username)), oneTime);
    }

    /** Enables or disables an account. You cannot disable yourself or the last active administrator. */
    public synchronized UserView setEnabled(String username, boolean enabled, AppUser actingAdmin) {
        UserRepository.Account account = account(username);
        if (account.pending()) {
            throw new IllegalArgumentException("Approve or reject " + username + "'s request to join instead");
        }
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

    /** The facilities someone can ask to join: every blood centre and hospital, by name. */
    public List<JoinableFacility> joinableFacilities() {
        return network.facilities().stream().map(f -> new JoinableFacility(f.id(), f.name(), f.type())).toList();
    }

    /**
     * Records a request to join. The account is created disabled and pending; nobody can sign in with it until an
     * administrator approves it. Administrators are notified.
     */
    public synchronized UserView requestAccount(SignupInput input) {
        if (needsSetup()) {
            throw new IllegalArgumentException("RaktSetu is not set up yet; the first administrator must set it up");
        }
        if (input == null) {
            throw new IllegalArgumentException("Name, username, role, facility and password are required");
        }
        String username = username(input.username());
        if (users.find(username).isPresent()) {
            throw new IllegalArgumentException("The username '" + username + "' is already taken");
        }
        String name = displayName(input.displayName());
        Role role = switch (input.role() == null ? "" : input.role().trim().toUpperCase(Locale.ROOT)) {
            case "CENTRE_STAFF" -> Role.CENTRE_STAFF;
            case "HOSPITAL_STAFF" -> Role.HOSPITAL_STAFF;
            default -> throw new IllegalArgumentException("Choose blood centre staff or hospital staff");
        };
        String facilityId = facilityFor(role, input.facilityId());
        Passwords.check(input.password(), username);
        if (users.pendingCount() >= MAX_PENDING) {
            throw new IllegalArgumentException("Too many requests are waiting; ask a network administrator directly");
        }
        users.insertPending(new AppUser(username, name, role, facilityId), encoder.encode(input.password()),
                LocalDate.now(clock));
        notifications.add(LocalDate.now(clock), null, NotificationRepository.Kind.ACCOUNT_REQUESTED,
                name + " (" + username + ") asked to join as " + (role == Role.HOSPITAL_STAFF ? "hospital" : "centre")
                        + " staff at " + facilityId + ". Approve or reject on the People page.");
        return view(account(username));
    }

    /** Approves a request to join. */
    public synchronized UserView approve(String username) {
        users.approve(pending(username).user().username());
        return view(account(username));
    }

    /** Rejects a request to join, deleting it. */
    public synchronized void reject(String username) {
        users.deletePending(pending(username).user().username());
    }

    private UserRepository.Account pending(String username) {
        UserRepository.Account account = account(username);
        if (!account.pending()) {
            throw new IllegalArgumentException(username + " is not waiting for approval");
        }
        return account;
    }

    /** Checks the facility exists and suits the role: hospital staff at a hospital, centre staff at a blood centre. */
    private String facilityFor(Role role, String rawId) {
        String id = rawId == null ? "" : rawId.trim();
        FacilityView f = network.facilities().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Choose the facility this person works at"));
        boolean hospital = f.type().equals("HOSPITAL");
        if (hospital != (role == Role.HOSPITAL_STAFF)) {
            throw new IllegalArgumentException(role == Role.HOSPITAL_STAFF
                    ? "Hospital staff must belong to a hospital" : "Centre staff must belong to a blood centre");
        }
        return id;
    }

    /**
     * Sets someone's mobile number and whether they want urgent alerts. The number is held only for alerts, so turning
     * alerts off (or giving a blank number) erases it, including from the record of past alerts: this is how
     * someone withdraws consent. Turning alerts on records that they agreed, when, and under which privacy notice.
     */
    public synchronized UserView updateContact(String username, ContactInput input) {
        UserRepository.Account account = account(username);
        if (input == null) {
            throw new IllegalArgumentException("Phone and alert setting are required");
        }
        String phone = input.phone() == null || input.phone().isBlank() ? null : PhoneNumbers.normalise(input.phone());
        boolean wanted = input.alertsEnabled() == null ? account.alertsEnabled() : input.alertsEnabled();
        if (wanted && phone == null) {
            throw new IllegalArgumentException("Add a mobile number to receive alerts");
        }
        String name = account.user().username();
        if (!wanted) {
            users.setContact(name, null, false);
            users.recordContactBasis(name, null, 0, null);
            outbox.forget(name);
        } else {
            users.setContact(name, phone, true);
            boolean changed = !phone.equals(account.phone()) || !account.alertsEnabled();
            if (changed) {
                users.recordContactBasis(name, "CONSENT", clock.millis(), privacy.noticeVersion());
            }
        }
        return view(users.find(name).orElseThrow());
    }

    /**
     * Erases a former member of staff's name and mobile number, keeping only the username that the tamper-evident
     * audit log refers to. Only disabled accounts can be erased, and never your own.
     */
    public synchronized UserView anonymise(String username, AppUser actingAdmin) {
        UserRepository.Account account = account(username);
        if (account.user().username().equals(actingAdmin.username())) {
            throw new IllegalArgumentException("You cannot erase your own account");
        }
        if (account.enabled()) {
            throw new IllegalArgumentException("Disable the account before erasing the person's details");
        }
        users.anonymise(account.user().username(), LocalDate.now(clock));
        outbox.forget(account.user().username());
        return view(account(username));
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
        return new UserView(u.username(), u.displayName(), u.role(), u.facilityId(), a.enabled(), a.phone(),
                a.alertsEnabled(), a.mustChangePassword(), a.createdOn(), a.anonymisedOn(), a.pending());
    }
}
