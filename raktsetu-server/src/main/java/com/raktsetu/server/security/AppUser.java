package com.raktsetu.server.security;

import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

/**
 * A signed-in person.
 *
 * @param username    login name
 * @param displayName name shown in the app
 * @param role        what they may do
 * @param facilityId  the centre or hospital they work at; {@code null} for administrators, and for someone whose
 *                    request to join a facility not yet in the network is waiting (they cannot sign in until an
 *                    administrator approves it, which adds the facility)
 */
public record AppUser(String username, String displayName, Role role, String facilityId) {

    public AppUser {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** @throws AccessDeniedException unless this is an administrator */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw new AccessDeniedException("Only a network administrator can do this");
        }
    }

    /**
     * Allows administrators, and staff of the given facility with the given role.
     *
     * @throws AccessDeniedException otherwise
     */
    public void requireActingFor(String facilityId, Role staffRole) {
        if (isAdmin()) {
            return;
        }
        if (role != staffRole || this.facilityId == null || !this.facilityId.equals(facilityId)) {
            throw new AccessDeniedException("You can only do this for your own "
                    + (staffRole == Role.HOSPITAL_STAFF ? "hospital" : "centre") + " (" + this.facilityId + ")");
        }
    }
}
