package fr.dllv.booking.bookings;

import fr.dllv.booking.bookings.mail.BookingMailer;
import fr.dllv.booking.bookings.persistence.BookingRepository;
import fr.dllv.booking.catalog.BookableResource;
import fr.dllv.booking.catalog.CatalogService;
import fr.dllv.booking.config.BookingProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * L'API publique de la fonctionnalite reservation : une methode par cas
 * d'usage, et rien qui dise comment on l'a appelee.
 *
 * <p>Cette classe est l'endroit ou ce POC diverge le plus de ses jumeaux
 * hexagonaux. Ceux-ci eclatent ces cinq cas d'usage en cinq
 * {@code ...Handler}, chacun derriere son port entrant, son objet
 * {@code Command} et son objet {@code Result} -- quinze fichiers pour ce qui
 * tient ici en une classe. Chaque methode reste ce que les deux approches
 * appellent un cas d'usage atomique : une transaction, une intention, aucune
 * regle metier codee sur place.
 *
 * <p>Ce qu'on perd : on ne peut plus substituer un cas d'usage par son
 * interface. Tomato repond que personne ne l'a jamais fait, et que Mockito
 * mocke les classes concretes.
 *
 * <p>Ce qu'on gagne se lit dans {@link #cancel} : l'annulation declenche la
 * promotion de la liste d'attente par un simple appel de methode privee, la ou
 * la version hexagonale compose deux use cases en passant par un port et
 * reserialise les dates du creneau en chaines ISO pour franchir la frontiere.
 *
 * <p>{@code @Transactional} est porte par les methodes de ce service, comme
 * dans les exemples de tomato -- jamais par les controleurs, les taches
 * planifiees ou les commandes CLI, qui ne font que deleguer.
 */
@ApplicationScoped
public class BookingService {

    private static final Logger LOG = Logger.getLogger(BookingService.class);

    private final BookingRepository bookings;
    private final CatalogService catalog;
    private final BookingMailer mailer;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingService(
            BookingRepository bookings,
            CatalogService catalog,
            BookingMailer mailer,
            BookingProperties properties,
            Clock clock) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Demande une reservation.
     *
     * <p>Une demande ne prend pas de place : elle nait {@code EN_ATTENTE} et
     * devra etre confirmee. Deux personnes peuvent donc tenir le meme creneau
     * en meme temps ; la premiere a confirmer l'emporte, et la seconde recoit
     * un conflit a la confirmation. C'est voulu, et c'est teste.
     *
     * @throws fr.dllv.booking.catalog.ResourceNotFoundException ressource inconnue
     * @throws BookingInThePastException creneau deja commence
     * @throws BookingTooFarInAdvanceException au-dela de l'horizon
     * @throws InvalidBookingDurationException duree hors bornes
     * @throws OverlappingBookingException ressource complete et liste d'attente refusee
     */
    @Transactional
    public Booking create(CreateBookingCommand command) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        BookableResource resource = catalog.getById(command.resourceId());

        Booking booking = Booking.request(
                command.resourceId(),
                command.attendeeId(),
                command.slot(),
                now,
                properties.maxDaysAhead(),
                properties.minDurationMinutes(),
                properties.maxDurationMinutes());

        long confirmed = bookings.countConfirmedOverlapping(command.resourceId(), command.slot());

        if (!resource.hasRoomFor(confirmed)) {
            if (!command.joinWaitlistIfFull()) {
                throw new OverlappingBookingException(command.slot());
            }

            booking.joinWaitlist();
        }

        bookings.persist(booking);

        return booking;
    }

    /**
     * Confirme une demande, si la place est toujours la.
     *
     * <p>La disponibilite est re-verifiee ici et pas seulement a la creation :
     * c'est tout l'interet du modele optimiste decrit dans {@link #create}.
     *
     * @throws BookingNotFoundException reservation inconnue
     * @throws OverlappingBookingException quelqu'un a confirme avant
     * @throws InvalidBookingTransitionException la reservation n'est plus en attente
     */
    @Transactional
    public Booking confirm(UUID bookingId) {
        Booking booking = getById(bookingId);
        BookableResource resource = catalog.getById(booking.resourceId());

        long confirmed =
                bookings.countConfirmedOverlappingExcluding(booking.resourceId(), booking.slot(), booking.id());

        if (!resource.hasRoomFor(confirmed)) {
            throw new OverlappingBookingException(booking.slot());
        }

        booking.confirm();

        return booking;
    }

    /**
     * Annule une reservation, puis tente de donner la place liberee a la liste
     * d'attente.
     *
     * @throws BookingNotFoundException reservation inconnue
     * @throws CannotCancelBookingException creneau deja passe
     * @throws InvalidBookingTransitionException la reservation n'etait pas confirmee
     */
    @Transactional
    public CancellationOutcome cancel(UUID bookingId) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Booking booking = getById(bookingId);

        booking.cancel(properties.penaltyThresholdHours() * 60L, now);

        Booking promoted =
                promoteFromWaitlist(booking.resourceId(), booking.slot()).orElse(null);

        return new CancellationOutcome(booking, promoted);
    }

    /**
     * Donne un creneau libere a la premiere demande en attente qui peut le
     * prendre.
     *
     * <p>Premier arrive, premier servi : l'ordre vient de la requete
     * ({@code order by requestedAt}), pas d'un tri refait ici. La boucle
     * s'arrete a la premiere candidate qui chevauche le creneau <em>et</em> qui
     * tient dans la capacite restante -- une demande peut viser un creneau plus
     * large que celui qui vient de se liberer, et rester bloquee par une autre
     * reservation.
     *
     * <p>Publique parce que c'est un cas d'usage a part entiere, appele aussi
     * bien par {@link #cancel} que, potentiellement, par un operateur.
     */
    @Transactional
    public Optional<Booking> promoteFromWaitlist(UUID resourceId, TimeSlot freedSlot) {
        BookableResource resource = catalog.getById(resourceId);

        for (Booking candidate : bookings.findWaitlistedOverlapping(resourceId, freedSlot)) {
            long confirmed = bookings.countConfirmedOverlapping(resourceId, candidate.slot());

            if (resource.hasRoomFor(confirmed)) {
                candidate.promoteFromWaitlist();
                mailer.notifyWaitlistPromotion(candidate);

                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    /**
     * Expire les demandes que personne n'a confirmees dans le delai imparti.
     *
     * <p>Appele par la tache planifiee et par la commande CLI. Le filtre final
     * est demande a chaque reservation ({@link Booking#isExpirable}) et pas
     * deduit de la requete : la requete degrossit, l'objet decide.
     *
     * @return le nombre de reservations expirees
     */
    @Transactional
    public int expirePending() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        int minutes = properties.pendingExpirationMinutes();
        int expired = 0;

        for (Booking booking : bookings.findPendingRequestedBefore(now.minusMinutes(minutes))) {
            if (booking.isExpirable(now, minutes)) {
                booking.expire();
                expired++;
            }
        }

        if (expired > 0) {
            LOG.infof("%d reservation(s) expiree(s).", expired);
        }

        return expired;
    }

    /**
     * @throws BookingNotFoundException reservation inconnue
     */
    @Transactional
    public Booking getById(UUID bookingId) {
        Booking booking = bookings.findById(bookingId);

        if (booking == null) {
            throw new BookingNotFoundException(bookingId);
        }

        return booking;
    }

    /** Les reservations d'un participant. */
    @Transactional
    public List<Booking> findForAttendee(UUID attendeeId) {
        return bookings.findByAttendee(attendeeId);
    }

    /**
     * Le planning d'une ressource sur une fenetre de temps.
     *
     * <p>Les lectures passent par le service, pas par le repository depuis le
     * controleur. Les POC hexagonaux font l'inverse et injectent le port de
     * repository dans leur ressource HTTP ; tomato veut que le controleur ne
     * parle qu'a la couche de service, et que les requetes restent du cote
     * persistance.
     */
    @Transactional
    public List<Booking> findSchedule(UUID resourceId, TimeSlot window) {
        catalog.getById(resourceId);

        return bookings.findOverlapping(resourceId, window);
    }

    /** Resultat d'une annulation : la reservation annulee, et la promue s'il y en a une. */
    public record CancellationOutcome(Booking cancelled, Booking promoted) {

        public boolean penaltyApplied() {
            return cancelled.status() == BookingStatus.CANCELLED_WITH_PENALTY;
        }

        public Optional<Booking> promotedBooking() {
            return Optional.ofNullable(promoted);
        }
    }
}
