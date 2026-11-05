package fr.dllv.booking.bookings.persistence;

import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.BookingStatus;
import fr.dllv.booking.bookings.TimeSlot;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tout ce que l'application sait demander a la table {@code bookings}.
 *
 * <p>Deux consignes de tomato se croisent ici. "Persistence layer should be
 * solely responsible for database interactions" : les requetes vivent dans ce
 * fichier et nulle part ailleurs, aucun service ne construit de HQL. Et "No
 * unnecessary interfaces" : c'est une classe concrete, mockee telle quelle dans
 * les tests du service, sans interface intermediaire.
 *
 * <p>Le chevauchement s'ecrit {@code startsAt < :end and endsAt > :start} -- la
 * traduction SQL de l'intervalle semi-ouvert de {@link TimeSlot#overlaps}. Les
 * deux doivent dire la meme chose, et {@code BookingRepositoryTest} le verifie
 * sur une vraie base.
 */
@ApplicationScoped
public class BookingRepository implements PanacheRepositoryBase<Booking, UUID> {

    private static final String OVERLAPPING =
            "resourceId = :resourceId and slot.startsAt < :end and slot.endsAt > :start";

    /**
     * Combien de reservations confirmees occupent deja ce creneau.
     *
     * <p>Un {@code count} et non une liste : c'est la seule chose dont la regle
     * de capacite a besoin, et la base compte mieux que nous.
     */
    public long countConfirmedOverlapping(UUID resourceId, TimeSlot slot) {
        return count(
                OVERLAPPING + " and status = :status",
                Parameters.with("resourceId", resourceId)
                        .and("start", slot.startsAt())
                        .and("end", slot.endsAt())
                        .and("status", BookingStatus.CONFIRMED));
    }

    /**
     * Comme {@link #countConfirmedOverlapping}, en ignorant une reservation.
     *
     * <p>Sert a la confirmation : la reservation que l'on confirme chevauche
     * son propre creneau, et se compterait elle-meme.
     */
    public long countConfirmedOverlappingExcluding(UUID resourceId, TimeSlot slot, UUID excludedId) {
        return count(
                OVERLAPPING + " and status = :status and id <> :excluded",
                Parameters.with("resourceId", resourceId)
                        .and("start", slot.startsAt())
                        .and("end", slot.endsAt())
                        .and("status", BookingStatus.CONFIRMED)
                        .and("excluded", excludedId));
    }

    /** Toutes les reservations d'une ressource sur une fenetre : le planning. */
    public List<Booking> findOverlapping(UUID resourceId, TimeSlot window) {
        return list(
                OVERLAPPING + " order by slot.startsAt",
                Parameters.with("resourceId", resourceId)
                        .and("start", window.startsAt())
                        .and("end", window.endsAt()));
    }

    /**
     * Les demandes en liste d'attente qui visent ce creneau, la plus ancienne
     * d'abord.
     *
     * <p>L'ordre <em>est</em> la regle : premier arrive, premier servi. Il est
     * donc exprime en SQL, pas trie apres coup par le service.
     */
    public List<Booking> findWaitlistedOverlapping(UUID resourceId, TimeSlot slot) {
        return list(
                OVERLAPPING + " and waitlisted = true and status = :status order by requestedAt",
                Parameters.with("resourceId", resourceId)
                        .and("start", slot.startsAt())
                        .and("end", slot.endsAt())
                        .and("status", BookingStatus.PENDING));
    }

    /** Les demandes en attente depuis assez longtemps pour etre candidates a l'expiration. */
    public List<Booking> findPendingRequestedBefore(OffsetDateTime threshold) {
        return list(
                "status = :status and requestedAt < :threshold",
                Parameters.with("status", BookingStatus.PENDING).and("threshold", threshold));
    }

    /** Les reservations d'un participant, pour son espace personnel. */
    public List<Booking> findByAttendee(UUID attendeeId) {
        return list("attendeeId = :attendeeId order by slot.startsAt", Parameters.with("attendeeId", attendeeId));
    }
}
