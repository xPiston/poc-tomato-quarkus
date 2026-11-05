package fr.dllv.booking.bookings;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une reservation.
 *
 * <p>C'est l'objet metier <em>et</em> l'entite JPA. Les POC hexagonaux de la
 * famille en font deux classes plus un mapper, pour que le metier ignore qu'il
 * sera stocke un jour. Tomato prend le pari inverse : la persistance est un
 * choix assume, pas un detail qu'on se reserve le droit de changer, et quatre
 * annotations sur les champs coutent moins cher que trois fichiers a tenir
 * synchronises. Le reste de la classe est identique a la version hexagonale --
 * ce sont les memes regles, au mot pres.
 *
 * <p>Ce que cet objet garantit, et que rien a l'exterieur ne peut contourner :
 * <ul>
 *   <li>un creneau invalide (passe, trop long, trop loin) ne devient jamais une
 *       reservation, parce que la seule fabrique publique le refuse ;
 *   <li>le statut ne change que par {@link #transitionTo}, qui consulte la
 *       machine a etats, donc aucun chemin d'appel ne peut produire un etat
 *       incoherent ;
 *   <li>la penalite d'annulation est decidee ici, pas par l'appelant : on lui
 *       demande le seuil, pas la conclusion.
 * </ul>
 *
 * <p>Les setters sont absents volontairement. Tout ce qui se modifie le fait
 * par une methode qui porte le nom de l'intention metier -- {@code confirm},
 * {@code cancel}, {@code expire} -- et qui applique sa regle au passage.
 */
@Entity
@Table(
        name = "bookings",
        indexes = {
            @Index(name = "idx_bookings_resource_slot", columnList = "resource_id, starts_at, ends_at"),
            @Index(name = "idx_bookings_status_requested", columnList = "status, requested_at"),
            @Index(name = "idx_bookings_attendee", columnList = "attendee_id")
        })
public class Booking {

    @Id
    private UUID id;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Column(name = "attendee_id", nullable = false)
    private UUID attendeeId;

    @Embedded
    private TimeSlot slot;

    @Enumerated(EnumType.STRING)
    @Column(length = 40, nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private boolean waitlisted;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    /** Requis par JPA. */
    protected Booking() {}

    private Booking(UUID resourceId, UUID attendeeId, TimeSlot slot, OffsetDateTime requestedAt, boolean waitlisted) {
        this.id = UUID.randomUUID();
        this.resourceId = resourceId;
        this.attendeeId = attendeeId;
        this.slot = slot;
        this.status = BookingStatus.PENDING;
        this.waitlisted = waitlisted;
        this.requestedAt = requestedAt;
    }

    /**
     * Demande une reservation, apres avoir verifie que le creneau est reservable.
     *
     * <p>Une reservation nait toujours {@code EN_ATTENTE} : c'est une option,
     * pas une place. Elle ne consomme pas la capacite de la ressource tant
     * qu'elle n'est pas confirmee, et elle expire si personne ne la confirme
     * (voir {@link #isExpirable}).
     *
     * <p>Les trois regles de validite sont appliquees ici plutot que dans le
     * service, pour qu'il n'existe aucun moyen d'obtenir un {@code Booking}
     * qui les enfreigne -- pas meme depuis un test ou une tache planifiee.
     *
     * <p>La disponibilite, elle, n'est pas verifiee ici : elle depend des
     * autres reservations de la ressource, que cet objet n'a aucun moyen de
     * connaitre. C'est au service de la consulter, puis d'appeler
     * {@link #joinWaitlist()} si la ressource est complete et que le
     * participant accepte d'attendre. Dans cet ordre, et pas l'inverse : un
     * creneau invalide est refuse comme invalide, meme quand la ressource est
     * par ailleurs complete.
     *
     * @param maxDaysAhead horizon de reservation, en jours
     * @param minDurationMinutes duree minimale acceptee
     * @param maxDurationMinutes duree maximale acceptee
     */
    public static Booking request(
            UUID resourceId,
            UUID attendeeId,
            TimeSlot slot,
            OffsetDateTime now,
            int maxDaysAhead,
            int minDurationMinutes,
            int maxDurationMinutes) {

        if (slot.startsBefore(now)) {
            throw new BookingInThePastException(slot);
        }
        if (slot.startsAt().isAfter(now.plusDays(maxDaysAhead))) {
            throw new BookingTooFarInAdvanceException(maxDaysAhead);
        }

        long duration = slot.durationInMinutes();
        if (duration < minDurationMinutes) {
            throw InvalidBookingDurationException.tooShort(duration, minDurationMinutes);
        }
        if (duration > maxDurationMinutes) {
            throw InvalidBookingDurationException.tooLong(duration, maxDurationMinutes);
        }

        return new Booking(resourceId, attendeeId, slot, now, false);
    }

    public void confirm() {
        transitionTo(BookingStatus.CONFIRMED);
    }

    public void expire() {
        transitionTo(BookingStatus.EXPIRED);
    }

    public void complete() {
        transitionTo(BookingStatus.COMPLETED);
    }

    /**
     * Annule la reservation, avec penalite si l'on s'y prend trop tard.
     *
     * <p>L'appelant fournit le seuil (24 h par defaut, configurable), pas le
     * verdict : c'est la reservation qui sait si son propre creneau tombe en
     * deca. L'alternative -- un {@code if} dans le service, qui choisirait
     * entre deux statuts -- sortirait la regle de l'objet qui la porte, et
     * c'est precisement ce que tomato appelle de la logique de domaine egaree
     * dans un service.
     *
     * @param penaltyThresholdMinutes delai avant le creneau en-deca duquel une
     *     penalite s'applique
     * @throws CannotCancelBookingException si le creneau est deja passe ou la
     *     reservation deja honoree
     * @throws InvalidBookingTransitionException si la reservation n'est pas
     *     confirmee
     */
    public void cancel(long penaltyThresholdMinutes, OffsetDateTime now) {
        if (slot.hasEndedBy(now) || status == BookingStatus.COMPLETED) {
            throw new CannotCancelBookingException(id);
        }

        boolean penalty = slot.minutesUntilStart(now) < penaltyThresholdMinutes;

        transitionTo(penalty ? BookingStatus.CANCELLED_WITH_PENALTY : BookingStatus.CANCELLED);
    }

    /**
     * Place la demande en liste d'attente, la ressource etant complete.
     *
     * <p>Une demande en attente reste {@code EN_ATTENTE} : la difference est
     * qu'elle ne sera pas confirmee par son auteur mais par la liberation d'une
     * place (voir {@link #promoteFromWaitlist()}).
     */
    public void joinWaitlist() {
        if (status != BookingStatus.PENDING) {
            throw new InvalidBookingTransitionException(status, BookingStatus.PENDING);
        }

        this.waitlisted = true;
    }

    /** Fait passer une demande de la liste d'attente a une place confirmee. */
    public void promoteFromWaitlist() {
        if (!waitlisted) {
            throw new NotWaitlistedException(id);
        }

        transitionTo(BookingStatus.CONFIRMED);
    }

    /**
     * Une demande restee {@code EN_ATTENTE} au-dela du delai imparti est
     * expirable : elle immobilise une option que personne n'a confirmee.
     */
    public boolean isExpirable(OffsetDateTime now, int expirationMinutes) {
        return status == BookingStatus.PENDING && !now.isBefore(requestedAt.plusMinutes(expirationMinutes));
    }

    private void transitionTo(BookingStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidBookingTransitionException(status, target);
        }

        this.status = target;
    }

    public UUID id() {
        return id;
    }

    public UUID resourceId() {
        return resourceId;
    }

    public UUID attendeeId() {
        return attendeeId;
    }

    public TimeSlot slot() {
        return slot;
    }

    public BookingStatus status() {
        return status;
    }

    public boolean isWaitlisted() {
        return waitlisted;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
    }

    public OffsetDateTime requestedAt() {
        return requestedAt;
    }
}
