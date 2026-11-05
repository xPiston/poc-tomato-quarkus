package fr.dllv.booking.auth;

import java.security.Principal;
import java.util.UUID;

/**
 * L'utilisateur derriere la requete en cours.
 *
 * <p>C'est ce que les controleurs recoivent, et la seule chose qu'ils
 * transmettent au metier : son {@link #attendeeId()}.
 */
public record AuthenticatedUser(UUID id, UUID attendeeId, String email, String name) implements Principal {

    public static AuthenticatedUser of(User user) {
        return new AuthenticatedUser(user.id(), user.attendeeId(), user.email(), user.name());
    }

    @Override
    public String getName() {
        return email;
    }
}
