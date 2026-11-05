package fr.dllv.booking.bookings;

import static fr.dllv.booking.support.Bookings.NOW;
import static fr.dllv.booking.support.Bookings.slotIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.dllv.booking.bookings.mail.BookingMailer;
import fr.dllv.booking.bookings.persistence.BookingRepository;
import fr.dllv.booking.catalog.BookableResource;
import fr.dllv.booking.catalog.CatalogService;
import fr.dllv.booking.config.BookingProperties;
import fr.dllv.booking.support.Bookings;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * L'orchestration des cas d'usage, avec des doublures.
 *
 * <p>Mockito et pas un repository en memoire, parce que tomato le dit
 * explicitement : "Prefer Mockito over an in-memory repository for
 * service-layer unit tests". Les POC hexagonaux maintiennent pour cela un
 * {@code InMemoryBookingRepository} de cent lignes, qui est une seconde
 * implementation a tenir a jour et un jeu de donnees partage entre tous les
 * tests. Ici chaque test dit ce que la base lui repond, et rien d'autre.
 *
 * <p>Aucun {@code @QuarkusTest} : {@code BookingService} est une classe avec un
 * constructeur. On l'instancie.
 */
@DisplayName("Le service de reservation")
class BookingServiceTest {

    private static final UUID RESOURCE = UUID.randomUUID();
    private static final UUID ATTENDEE = UUID.randomUUID();

    private BookingRepository bookings;
    private CatalogService catalog;
    private BookingMailer mailer;
    private BookingService service;

    @BeforeEach
    void setUp() {
        bookings = mock(BookingRepository.class);
        catalog = mock(CatalogService.class);
        mailer = mock(BookingMailer.class);

        BookingProperties properties = mock(BookingProperties.class);
        when(properties.maxDaysAhead()).thenReturn(Bookings.MAX_DAYS_AHEAD);
        when(properties.minDurationMinutes()).thenReturn(Bookings.MIN_DURATION);
        when(properties.maxDurationMinutes()).thenReturn(Bookings.MAX_DURATION);
        when(properties.penaltyThresholdHours()).thenReturn(24);
        when(properties.pendingExpirationMinutes()).thenReturn(15);

        service =
                new BookingService(bookings, catalog, mailer, properties, Clock.fixed(NOW.toInstant(), ZoneOffset.UTC));
    }

    private void resourceWithCapacity(int capacity) {
        when(catalog.getById(RESOURCE)).thenReturn(BookableResource.of("Salle Everest", capacity));
    }

    private CreateBookingCommand command(TimeSlot slot, boolean joinWaitlist) {
        return new CreateBookingCommand(RESOURCE, ATTENDEE, slot, joinWaitlist);
    }

    @Nested
    @DisplayName("quand il cree une reservation")
    class WhenCreating {

        @Test
        @DisplayName("enregistre une demande en attente quand il reste de la place")
        void saves_a_pending_request_when_there_is_room() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(24, 60);
            when(bookings.countConfirmedOverlapping(RESOURCE, slot)).thenReturn(0L);

            Booking booking = service.create(command(slot, false));

            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
            assertThat(booking.isWaitlisted()).isFalse();
            assertThat(booking.attendeeId()).isEqualTo(ATTENDEE);
            verify(bookings).persist(booking);
        }

        @Test
        @DisplayName("refuse quand la ressource est complete et que l'on n'accepte pas d'attendre")
        void refuses_when_the_resource_is_full_and_waiting_is_not_accepted() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(24, 60);
            when(bookings.countConfirmedOverlapping(RESOURCE, slot)).thenReturn(1L);

            assertThatThrownBy(() -> service.create(command(slot, false)))
                    .isInstanceOf(OverlappingBookingException.class);

            verify(bookings, never()).persist(any(Booking.class));
        }

        @Test
        @DisplayName("met en liste d'attente quand la ressource est complete et que l'on accepte")
        void puts_the_request_on_the_waitlist_when_full_and_waiting_is_accepted() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(24, 60);
            when(bookings.countConfirmedOverlapping(RESOURCE, slot)).thenReturn(1L);

            Booking booking = service.create(command(slot, true));

            assertThat(booking.isWaitlisted()).isTrue();
            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
            verify(bookings).persist(booking);
        }

        @Test
        @DisplayName("laisse de la place jusqu'a la capacite de la ressource")
        void leaves_room_up_to_the_resource_capacity() {
            resourceWithCapacity(4);
            TimeSlot slot = slotIn(24, 60);
            when(bookings.countConfirmedOverlapping(RESOURCE, slot)).thenReturn(3L);

            assertThat(service.create(command(slot, false)).isWaitlisted()).isFalse();
        }

        @Test
        @DisplayName("juge le creneau avant de regarder la disponibilite")
        void judges_the_slot_before_it_looks_at_availability() {
            // Une ressource complete ET un creneau deja passe : c'est l'erreur
            // de creneau qui doit sortir, pas le conflit. L'ordre compte, parce
            // que l'un repond 422 et l'autre 409 -- et le frontend ne dit pas la
            // meme chose dans les deux cas.
            resourceWithCapacity(1);
            when(bookings.countConfirmedOverlapping(eq(RESOURCE), any(TimeSlot.class)))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.create(command(slotIn(-2, 60), false)))
                    .isInstanceOf(BookingInThePastException.class);
        }
    }

    @Nested
    @DisplayName("quand il confirme")
    class WhenConfirming {

        @Test
        @DisplayName("ne se compte pas elle-meme dans l'occupation du creneau")
        void does_not_count_the_booking_against_itself() {
            resourceWithCapacity(1);
            Booking booking = Bookings.pendingOn(slotIn(24, 60), RESOURCE, ATTENDEE);
            when(bookings.findById(booking.id())).thenReturn(booking);
            when(catalog.getById(booking.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.countConfirmedOverlappingExcluding(RESOURCE, booking.slot(), booking.id()))
                    .thenReturn(0L);

            assertThat(service.confirm(booking.id()).status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("refuse quand quelqu'un a confirme avant")
        void refuses_when_somebody_else_confirmed_first() {
            Booking booking = Bookings.pendingOn(slotIn(24, 60), RESOURCE, ATTENDEE);
            when(bookings.findById(booking.id())).thenReturn(booking);
            when(catalog.getById(booking.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.countConfirmedOverlappingExcluding(RESOURCE, booking.slot(), booking.id()))
                    .thenReturn(1L);

            assertThatThrownBy(() -> service.confirm(booking.id())).isInstanceOf(OverlappingBookingException.class);

            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        }

        @Test
        @DisplayName("ne trouve rien a confirmer sous un identifiant inconnu")
        void finds_nothing_to_confirm_under_an_unknown_id() {
            UUID unknown = UUID.randomUUID();
            when(bookings.findById(unknown)).thenReturn(null);

            assertThatThrownBy(() -> service.confirm(unknown)).isInstanceOf(BookingNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("quand il annule")
    class WhenCancelling {

        @Test
        @DisplayName("donne la place liberee a la premiere demande en attente, et la previent")
        void hands_the_freed_seat_to_the_first_waiting_request_and_tells_it() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(48, 60);
            Booking cancelled = Bookings.confirmedOn(slot);
            Booking waiting = Bookings.waitlistedOn(slot);

            when(bookings.findById(cancelled.id())).thenReturn(cancelled);
            when(catalog.getById(cancelled.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.findWaitlistedOverlapping(cancelled.resourceId(), slot))
                    .thenReturn(List.of(waiting));
            when(bookings.countConfirmedOverlapping(cancelled.resourceId(), waiting.slot()))
                    .thenReturn(0L);

            BookingService.CancellationOutcome outcome = service.cancel(cancelled.id());

            assertThat(outcome.cancelled().status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(outcome.penaltyApplied()).isFalse();
            assertThat(outcome.promotedBooking()).contains(waiting);
            assertThat(waiting.status()).isEqualTo(BookingStatus.CONFIRMED);
            verify(mailer).notifyWaitlistPromotion(waiting);
        }

        @Test
        @DisplayName("signale la penalite quand on s'y prend tard")
        void reports_the_penalty_when_it_is_late() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(2, 60);
            Booking cancelled = Bookings.confirmedOn(slot);

            when(bookings.findById(cancelled.id())).thenReturn(cancelled);
            when(catalog.getById(cancelled.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.findWaitlistedOverlapping(cancelled.resourceId(), slot))
                    .thenReturn(List.of());

            BookingService.CancellationOutcome outcome = service.cancel(cancelled.id());

            assertThat(outcome.cancelled().status()).isEqualTo(BookingStatus.CANCELLED_WITH_PENALTY);
            assertThat(outcome.penaltyApplied()).isTrue();
        }

        @Test
        @DisplayName("n'envoie rien quand personne n'attendait")
        void sends_nothing_when_nobody_was_waiting() {
            resourceWithCapacity(1);
            TimeSlot slot = slotIn(48, 60);
            Booking cancelled = Bookings.confirmedOn(slot);

            when(bookings.findById(cancelled.id())).thenReturn(cancelled);
            when(catalog.getById(cancelled.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.findWaitlistedOverlapping(cancelled.resourceId(), slot))
                    .thenReturn(List.of());

            assertThat(service.cancel(cancelled.id()).promotedBooking()).isEmpty();
            verifyNoInteractions(mailer);
        }

        @Test
        @DisplayName("passe la demande que la capacite ne peut pas encore accueillir")
        void skips_the_request_the_capacity_still_cannot_take() {
            // La premiere candidate vise un creneau plus large que celui qui
            // vient de se liberer, et reste bloquee par une autre reservation.
            // La seconde tient : c'est elle qui passe.
            TimeSlot freed = slotIn(48, 60);
            Booking cancelled = Bookings.confirmedOn(freed);
            Booking tooWide = Bookings.waitlistedOn(slotIn(47, 180));
            Booking fits = Bookings.waitlistedOn(freed);

            when(bookings.findById(cancelled.id())).thenReturn(cancelled);
            when(catalog.getById(cancelled.resourceId())).thenReturn(BookableResource.of("Salle Everest", 1));
            when(bookings.findWaitlistedOverlapping(cancelled.resourceId(), freed))
                    .thenReturn(List.of(tooWide, fits));
            when(bookings.countConfirmedOverlapping(cancelled.resourceId(), tooWide.slot()))
                    .thenReturn(1L);
            when(bookings.countConfirmedOverlapping(cancelled.resourceId(), fits.slot()))
                    .thenReturn(0L);

            assertThat(service.cancel(cancelled.id()).promotedBooking()).contains(fits);
            assertThat(tooWide.status()).isEqualTo(BookingStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("quand il expire les demandes")
    class WhenExpiring {

        @Test
        @DisplayName("n'expire que celles qui ont vraiment depasse le delai")
        void expires_only_those_that_really_ran_out_of_time() {
            Booking demandedAtNoon = Bookings.pending();
            Booking demandedTenMinutesLater = Booking.request(
                    RESOURCE,
                    ATTENDEE,
                    slotIn(24, 60),
                    NOW.plusMinutes(10),
                    Bookings.MAX_DAYS_AHEAD,
                    Bookings.MIN_DURATION,
                    Bookings.MAX_DURATION);

            // Le lot rendu par la requete contient les deux demandes : c'est
            // volontaire, et c'est le point du test. La requete degrossit sur
            // `requestedAt` avec une seule borne, elle ne peut pas etre juste
            // pour deux reservations demandees a dix minutes d'intervalle. Le
            // tri fin est demande a chaque objet, et un objet qui n'a pas
            // depasse son delai refuse d'expirer meme si la requete l'a remonte.
            when(bookings.findPendingRequestedBefore(any()))
                    .thenReturn(List.of(demandedAtNoon, demandedTenMinutesLater));

            // A midi pile, aucune des deux n'a atteint ses quinze minutes.
            assertThat(service.expirePending()).isEqualTo(0);
            assertThat(demandedAtNoon.status()).isEqualTo(BookingStatus.PENDING);

            // Vingt minutes plus tard, celle de midi a depasse le delai (echeance
            // 12 h 15) ; celle de 12 h 10 ne l'atteindra qu'a 12 h 25.
            BookingProperties properties = mock(BookingProperties.class);
            when(properties.pendingExpirationMinutes()).thenReturn(15);
            BookingService later = new BookingService(
                    bookings,
                    catalog,
                    mailer,
                    properties,
                    Clock.fixed(NOW.plusMinutes(20).toInstant(), ZoneOffset.UTC));

            assertThat(later.expirePending()).isEqualTo(1);
            assertThat(demandedAtNoon.status()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(demandedTenMinutesLater.status()).isEqualTo(BookingStatus.PENDING);
        }

        @Test
        @DisplayName("ne compte rien quand il n'y a rien a expirer")
        void counts_nothing_when_there_is_nothing_to_expire() {
            when(bookings.findPendingRequestedBefore(any())).thenReturn(List.of());

            assertThat(service.expirePending()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("quand il lit")
    class WhenReading {

        @Test
        @DisplayName("verifie que la ressource existe avant de rendre son planning")
        void checks_the_resource_exists_before_handing_out_its_schedule() {
            TimeSlot window = slotIn(0, 24 * 60);
            resourceWithCapacity(1);
            when(bookings.findOverlapping(RESOURCE, window)).thenReturn(List.of(Bookings.confirmedOn(slotIn(24, 60))));

            assertThat(service.findSchedule(RESOURCE, window)).hasSize(1);
            verify(catalog).getById(RESOURCE);
        }

        @Test
        @DisplayName("rend les reservations d'un participant")
        void hands_back_an_attendee_bookings() {
            when(bookings.findByAttendee(ATTENDEE)).thenReturn(List.of(Bookings.confirmed()));

            assertThat(service.findForAttendee(ATTENDEE)).hasSize(1);
        }

        @Test
        @DisplayName("rend Optional.empty plutot que de lever quand personne n'attend")
        void hands_back_an_empty_optional_rather_than_throwing_when_nobody_waits() {
            TimeSlot slot = slotIn(24, 60);
            resourceWithCapacity(1);
            when(bookings.findWaitlistedOverlapping(RESOURCE, slot)).thenReturn(List.of());

            assertThat(service.promoteFromWaitlist(RESOURCE, slot)).isEmpty();
        }
    }
}
