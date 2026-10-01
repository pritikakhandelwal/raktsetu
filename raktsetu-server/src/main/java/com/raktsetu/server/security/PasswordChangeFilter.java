package com.raktsetu.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Someone signed in with a one-time password may only change their password (or sign out) until they do. Every other
 * API call gets 403 with a message the website uses to show the change-password screen.
 */
final class PasswordChangeFilter extends OncePerRequestFilter {

    private final UserRepository users;

    PasswordChangeFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/") || path.startsWith("/api/auth/") || path.startsWith("/api/setup");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (CurrentUsers.isSignedIn(auth)
                && users.find(auth.getName()).map(UserRepository.Account::mustChangePassword).orElse(false)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"PasswordChangeRequired\","
                    + "\"message\":\"Choose a new password to continue\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
