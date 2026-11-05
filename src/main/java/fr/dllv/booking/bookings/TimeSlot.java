package fr.dllv.booking.bookings;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Un creneau horaire : l'intervalle semi-ouvert {@code [debut, fin[}.
 *
 * Deux creneaux qui se touchent ne se chevauchent pas : 10h-11h et 11h-12h sont
 * compatibles. C'est la seule convention de tout le POC sur ce point, et elle
 * est ecrite une fois, ici, dans {@link #overlaps}.
 *
 * <p>C'est aussi un {@code @Embeddable}, donc les deux colonnes
 * {@code starts_at} / {@code ends_at} de la table {@code bookings} sont lues et
 * ecrites directement dans cet objet. Les POC hexagonaux ont au meme endroit un
 * Value Object de domaine, un {@code BookingRecord} JPA et un
 * {@code BookingMapper} pour passer de l'un a l'autre -- trois fichiers qui
 * disent la meme chose. Tomato appelle ca du "endless data copying" et le
 * compte parmi les couts qu'il refuse de payer. Un embeddable supprime la
 * copie sans rien ceder : la regle de chevauchement vit toujours avec la
 * donnee, et c'est ca qui compte.
 */
@Embeddable
public class TimeSlot {

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    /** Requis par JPA. */
    protected TimeSlot() {}

    private TimeSlot(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public static TimeSlot of(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidTimeSlotException(startsAt, endsAt);
        }

        return new TimeSlot(startsAt, endsAt);
    }

    public OffsetDateTime startsAt() {
        return startsAt;
    }

    public OffsetDateTime endsAt() {
        return endsAt;
    }

    public long durationInMinutes() {
        return Duration.between(startsAt, endsAt).toMinutes();
    }

    /** Chevauchement strict : {@code [10,11[} et {@code [11,12[} ne se chevauchent pas. */
    public boolean overlaps(TimeSlot other) {
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }

    public boolean startsBefore(OffsetDateTime moment) {
        return startsAt.isBefore(moment);
    }

    public boolean hasEndedBy(OffsetDateTime moment) {
        return !endsAt.isAfter(moment);
    }

    /** Minutes restantes avant le debut, negatif si le creneau a deja commence. */
    public long minutesUntilStart(OffsetDateTime moment) {
        return Duration.between(moment, startsAt).toMinutes();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TimeSlot slot && startsAt.isEqual(slot.startsAt) && endsAt.isEqual(slot.endsAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startsAt.toInstant(), endsAt.toInstant());
    }

    @Override
    public String toString() {
        return startsAt + " -> " + endsAt;
    }
}
