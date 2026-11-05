package fr.dllv.booking.bookings;

import java.time.OffsetDateTime;

/**
 * Representation JSON d'une reservation.
 *
 * <p>Le DTO existe parce que la representation exposee et l'objet metier ne
 * disent pas la meme chose : le JSON porte le libelle francais du statut et des
 * identifiants en chaine, pour le frontend partage avec les autres POC. Tomato
 * ne proscrit pas les DTO -- il proscrit la copie systematique d'une couche a
 * l'autre. Ici il y en a un, a la frontiere, et c'est tout.
 *
 * <p>Jackson est configure en {@code SNAKE_CASE}, donc {@code resourceId}
 * sort en {@code resource_id}.
 */
public record BookingDto(
        String id,
        String resourceId,
        String attendeeId,
        String status,
        boolean waitlisted,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        OffsetDateTime requestedAt) {

    public static BookingDto of(Booking booking) {
        return new BookingDto(
                booking.id().toString(),
                booking.resourceId().toString(),
                booking.attendeeId().toString(),
                booking.status().label(),
                booking.isWaitlisted(),
                booking.slot().startsAt(),
                booking.slot().endsAt(),
                booking.requestedAt());
    }
}
