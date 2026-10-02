package com.raktsetu.server.security;

import com.raktsetu.server.security.AccountService.ContactInput;
import com.raktsetu.server.security.AccountService.NewAccount;
import com.raktsetu.server.security.AccountService.PasswordInput;
import com.raktsetu.server.security.AccountService.SetupInput;
import com.raktsetu.server.security.AccountService.UserInput;
import com.raktsetu.server.security.AccountService.UserView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** First-time setup, account administration and changing your own password. */
@RestController
@Tag(name = "Accounts", description = "First-run setup, staff accounts and passwords")
@RequestMapping("/api")
public class AccountController {

    public record EnabledInput(Boolean enabled) {
    }

    private final AccountService accounts;
    private final CurrentUsers current;
    private final LoginGuard guard;

    public AccountController(AccountService accounts, CurrentUsers current, LoginGuard guard) {
        this.accounts = accounts;
        this.current = current;
        this.guard = guard;
    }

    /**
     * Public: blood centres or hospitals matching a search, for the request form. Facilities in the network come first,
     * then matches from the directory of Indian facilities.
     */
    @GetMapping("/signup/places")
    public List<AccountService.Place> places(@RequestParam(defaultValue = "HOSPITAL_STAFF") String role,
                                             @RequestParam(defaultValue = "") String q) {
        return accounts.places(role, q);
    }

    /** Public: a request to join, which an administrator must approve before the account can sign in. */
    @PostMapping("/signup")
    public ResponseEntity<UserView> requestAccount(@RequestBody AccountService.SignupInput input,
                                                   HttpServletRequest request) {
        guard.signup(request.getRemoteAddr()); // throws TooManyAttemptsException, answered with 429
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.requestAccount(input));
    }

    /** Optional body for approving: the network facility an unlisted place is, chosen by the administrator. */
    public record ApproveInput(String facilityId) {
    }

    @PostMapping("/users/{username}/approve")
    public UserView approve(@PathVariable String username, @RequestBody(required = false) ApproveInput input,
                            Authentication auth) {
        current.of(auth).requireAdmin();
        return accounts.approve(username, input == null ? null : input.facilityId());
    }

    @PostMapping("/users/{username}/reject")
    public ResponseEntity<Void> reject(@PathVariable String username, Authentication auth) {
        current.of(auth).requireAdmin();
        accounts.reject(username);
        return ResponseEntity.noContent().build();
    }

    /** Public: whether the first administrator still has to be created. */
    @GetMapping("/setup")
    public Map<String, Boolean> setupStatus() {
        return Map.of("needsSetup", accounts.needsSetup());
    }

    /** Public, once: creates the first administrator. */
    @PostMapping("/setup")
    public ResponseEntity<UserView> setup(@RequestBody SetupInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.setup(input));
    }

    /** Any signed-in person, for their own account. */
    @PostMapping("/auth/password")
    public ResponseEntity<Void> changePassword(@RequestBody PasswordInput input, Authentication auth) {
        accounts.changePassword(current.of(auth).username(), input);
        return ResponseEntity.noContent().build();
    }

    /** Any signed-in person: their own mobile number and whether they want urgent alerts. */
    @PutMapping("/auth/contact")
    public UserView updateContact(@RequestBody ContactInput input, Authentication auth) {
        return accounts.updateContact(current.of(auth).username(), input);
    }

    @GetMapping("/users")
    public List<UserView> users(Authentication auth) {
        current.of(auth).requireAdmin();
        return accounts.list();
    }

    @PostMapping("/users")
    public ResponseEntity<NewAccount> create(@RequestBody UserInput input, Authentication auth) {
        current.of(auth).requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.create(input));
    }

    @PostMapping("/users/{username}/reset-password")
    public NewAccount resetPassword(@PathVariable String username, Authentication auth) {
        current.of(auth).requireAdmin();
        return accounts.resetPassword(username);
    }

    @PostMapping("/users/{username}/enabled")
    public UserView setEnabled(@PathVariable String username, @RequestBody EnabledInput input, Authentication auth) {
        AppUser admin = current.of(auth);
        admin.requireAdmin();
        if (input == null || input.enabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        return accounts.setEnabled(username, input.enabled(), admin);
    }
}
