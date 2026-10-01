package com.raktsetu.server.security;

import com.raktsetu.server.security.AccountService.NewAccount;
import com.raktsetu.server.security.AccountService.PasswordInput;
import com.raktsetu.server.security.AccountService.SetupInput;
import com.raktsetu.server.security.AccountService.UserInput;
import com.raktsetu.server.security.AccountService.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** First-time setup, account administration and changing your own password. */
@RestController
@RequestMapping("/api")
public class AccountController {

    public record EnabledInput(Boolean enabled) {
    }

    private final AccountService accounts;
    private final CurrentUsers current;

    public AccountController(AccountService accounts, CurrentUsers current) {
        this.accounts = accounts;
        this.current = current;
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
