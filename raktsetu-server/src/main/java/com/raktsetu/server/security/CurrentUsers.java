package com.raktsetu.server.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** Turns Spring Security's authentication into the RaktSetu account, with its role and facility. */
@Component
public class CurrentUsers {

    private final UserRepository users;

    public CurrentUsers(UserRepository users) {
        this.users = users;
    }

    public static boolean isSignedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** @throws AccessDeniedException if nobody is signed in or the account no longer exists */
    public AppUser of(Authentication auth) {
        if (!isSignedIn(auth)) {
            throw new AccessDeniedException("Not signed in");
        }
        return users.find(auth.getName()).filter(UserRepository.Account::enabled).map(UserRepository.Account::user)
                .orElseThrow(() -> new AccessDeniedException("Account " + auth.getName() + " is not active"));
    }
}
