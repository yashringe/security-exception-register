package io.github.yashringe.exceptionregister.web;

import io.github.yashringe.exceptionregister.domain.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.EnumSet;
import java.util.Set;

/** Turns the authenticated principal into the domain's {@link Actor}. Unknown authorities are ignored. */
final class CurrentActor {

    private CurrentActor() {
    }

    static Actor from(Authentication auth) {
        Set<Actor.Role> roles = EnumSet.noneOf(Actor.Role.class);
        for (GrantedAuthority a : auth.getAuthorities()) {
            String name = a.getAuthority();
            if (name.startsWith("ROLE_")) {
                try {
                    roles.add(Actor.Role.valueOf(name.substring(5)));
                } catch (IllegalArgumentException ignored) {
                    // not one of ours
                }
            }
        }
        return new Actor(auth.getName(), roles);
    }
}
