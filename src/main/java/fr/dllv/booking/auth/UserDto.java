package fr.dllv.booking.auth;

/** Representation JSON d'un compte. */
public record UserDto(String id, String name, String email, String attendeeId) {

    public static UserDto of(AuthenticatedUser user) {
        return new UserDto(
                user.id().toString(),
                user.name(),
                user.email(),
                user.attendeeId().toString());
    }
}
