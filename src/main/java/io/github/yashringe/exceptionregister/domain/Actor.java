package io.github.yashringe.exceptionregister.domain;

import java.util.Set;

/** The person performing an action, with the roles the identity provider gave them. */
public record Actor(String name, Set<Role> roles) {

    public enum Role {
        /** Can file and manage their own exception requests. */
        REQUESTER,
        /** Can approve or reject requests filed by others. */
        RISK_OFFICER,
        /** Read-only access to everything, including the audit trail. */
        AUDITOR
    }

    public Actor {
        if (name == null || name.isBlank()) {
            throw new DomainException("Actor needs a name");
        }
        roles = Set.copyOf(roles);
    }

    public boolean has(Role role) {
        return roles.contains(role);
    }
}
