package com.raktsetu.server.security;

import com.raktsetu.server.ApiModels.ApiError;
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

import java.util.List;

/** Signing in, finding out who is signed in, and (for administrators) listing accounts. */
@RestController
@RequestMapping("/api")
public class AuthController {

    public record LoginInput(String username, String password) {
    }

    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final CurrentUsers current;
    private final UserRepository users;

    public AuthController(AuthenticationManager authentication, SecurityContextRepository contexts,
                          CurrentUsers current, UserRepository users) {
        this.authentication = authentication;
        this.contexts = contexts;
        this.current = current;
        this.users = users;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> login(@RequestBody LoginInput input, HttpServletRequest request,
                                   HttpServletResponse response) {
        if (input == null || input.username() == null || input.password() == null) {
            return ResponseEntity.badRequest().body(new ApiError("BadRequest", "Username and password are required"));
        }
        Authentication result;
        try {
            result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    input.username().trim(), input.password()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("BadCredentials", "Wrong username or password"));
        }
        request.getSession(true);
        request.changeSessionId(); // protects against session fixation
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return ResponseEntity.ok(current.of(result));
    }

    @GetMapping("/auth/me")
    public ResponseEntity<?> me(Authentication auth) {
        if (!CurrentUsers.isSignedIn(auth)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError("Unauthorized", "Not signed in"));
        }
        return ResponseEntity.ok(current.of(auth));
    }

    @GetMapping("/users")
    public List<AppUser> users(Authentication auth) {
        current.of(auth).requireAdmin();
        return users.all();
    }
}
