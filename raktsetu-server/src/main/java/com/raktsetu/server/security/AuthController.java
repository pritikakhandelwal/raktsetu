package com.raktsetu.server.security;

import com.raktsetu.server.ApiModels.ApiError;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/** Signing in and finding out who is signed in. Accounts are managed in {@link AccountController}. */
@RestController
@Tag(name = "Sign-in", description = "Signing in and finding out who is signed in")
@RequestMapping("/api")
public class AuthController {

    public record LoginInput(String username, String password) {
    }

    /** Who is signed in, and whether they must choose a new password first. */
    public record Me(String username, String displayName, Role role, String facilityId, boolean mustChangePassword) {
    }

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final UserRepository users;
    private final LoginGuard guard;

    public AuthController(AuthenticationManager authentication, SecurityContextRepository contexts,
                          UserRepository users, LoginGuard guard) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.users = users;
        this.guard = guard;
    }

    private ResponseEntity<?> me(String username) {
        return users.find(username).filter(UserRepository.Account::enabled)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(new Me(a.user().username(), a.user().displayName(),
                        a.user().role(), a.user().facilityId(), a.mustChangePassword())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(new ApiError("Unauthorized", "Not signed in")));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginInput input, HttpServletRequest request,
                                   HttpServletResponse response) {
        if (input == null || input.username() == null || input.password() == null) {
            return ResponseEntity.badRequest().body(new ApiError("BadRequest", "Username and password are required"));
        }
        String username = input.username().trim();
        String address = request.getRemoteAddr();
        guard.check(username, address); // throws TooManyAttemptsException, answered with 429
        Authentication result;
        try {
            result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    username, input.password()));
        } catch (AuthenticationException e) {
            guard.failed(username, address);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("BadCredentials", "Wrong username or password, or the account is disabled"));
        }
        guard.succeeded(username);
        request.getSession(true);
        request.changeSessionId(); // protects against session fixation
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return me(result.getName());
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (!CurrentUsers.isSignedIn(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("Unauthorized", "Not signed in"));
        }
        return me(auth.getName());
    }
}
