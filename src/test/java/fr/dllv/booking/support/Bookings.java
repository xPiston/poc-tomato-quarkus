package fr.dllv.booking.support;

import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.TimeSlot;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Fabrique de reservations pour les tests.
 *
 * <p>Un instant de reference fixe plutot que {@code now()} : toutes les regles
 * de ce POC sont temporelles, et un test qui lit l'horloge systeme se met a
 * dependre du moment ou on le lance.
 */
public final class Bookings {

    /** L'instant de reference de tous les tests : un mardi, a midi, en UTC. */
    public static final OffsetDateTime NOW = OffsetDateTime.of(2026, 7, 7, 12, 0, 0, 0, ZoneOffset.UTC);

    public static final int MAX_DAYS_AHEAD = 30;
    public static final int MIN_DURATION = 15;
    public static final int MAX_DURATION = 480;

    private Bookings() {}

    /** Un creneau de {@code durationMinutes}, commencant dans {@code hoursFromNow}. */
    public static TimeSlot slotIn(long hoursFromNow, long durationMinutes) {
        OffsetDateTime start = NOW.plusHours(hoursFromNow);

        return TimeSlot.of(start, start.plusMinutes(durationMinutes));
    }

    /** Une demande valide, en attente, sur un creneau de demain. */
    public static Booking pending() {
        return pendingOn(slotIn(24, 60));
    }

    public static Booking pendingOn(TimeSlot slot) {
        return Booking.request(
                UUID.randomUUID(), UUID.randomUUID(), slot, NOW, MAX_DAYS_AHEAD, MIN_DURATION, MAX_DURATION);
    }

    public static Booking pendingOn(TimeSlot slot, UUID resourceId, UUID attendeeId) {
        return Booking.request(resourceId, attendeeId, slot, NOW, MAX_DAYS_AHEAD, MIN_DURATION, MAX_DURATION);
    }

    /** Une reservation confirmee sur un creneau de demain. */
    public static Booking confirmed() {
        Booking booking = pending();
        booking.confirm();

        return booking;
    }

    public static Booking confirmedOn(TimeSlot slot) {
        Booking booking = pendingOn(slot);
        booking.confirm();

        return booking;
    }

    /** Une demande placee en liste d'attente. */
    public static Booking waitlistedOn(TimeSlot slot) {
        Booking booking = pendingOn(slot);
        booking.joinWaitlist();

        return booking;
    }
}
