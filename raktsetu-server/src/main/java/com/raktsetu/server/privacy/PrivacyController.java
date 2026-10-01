package com.raktsetu.server.privacy;

import com.raktsetu.server.security.AccountService;
import com.raktsetu.server.security.AccountService.UserView;
import com.raktsetu.server.security.AppUser;
import com.raktsetu.server.security.CurrentUsers;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The privacy notice, each person's copy of their data, and erasing a former member of staff's details. */
@RestController
@Tag(name = "Privacy", description = "Privacy notice, your data, and erasure (DPDP Act, 2023)")
@RequestMapping("/api")
public class PrivacyController {

    private final PrivacyService privacy;
    private final AccountService accounts;
    private final CurrentUsers current;

    public PrivacyController(PrivacyService privacy, AccountService accounts, CurrentUsers current) {
        this.privacy = privacy;
        this.accounts = accounts;
        this.current = current;
    }

    /** Public: who runs this network, how to contact them, and how long alert records are kept. */
    @GetMapping("/privacy")
    public PrivacyService.Notice notice() {
        return privacy.notice();
    }

    /** Any signed-in person: a copy of everything held about them, as a JSON download. */
    @GetMapping("/auth/my-data")
    public ResponseEntity<PrivacyService.MyData> myData(Authentication auth) {
        AppUser me = current.of(auth);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"raktsetu-my-data-" + me.username() + ".json\"")
                .body(privacy.myData(me.username()));
    }

    /** Administrators: erase a disabled account's name and number, keeping the username for the audit log. */
    @PostMapping("/users/{username}/anonymise")
    public UserView anonymise(@PathVariable String username, Authentication auth) {
        AppUser admin = current.of(auth);
        admin.requireAdmin();
        return accounts.anonymise(username, admin);
    }
}
