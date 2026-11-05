package fr.dllv.booking.bookings;

import static fr.dllv.booking.support.Bookings.MAX_DAYS_AHEAD;
import static fr.dllv.booking.support.Bookings.MAX_DURATION;
import static fr.dllv.booking.support.Bookings.MIN_DURATION;
import static fr.dllv.booking.support.Bookings.NOW;
import static fr.dllv.booking.support.Bookings.slotIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import fr.dllv.booking.support.Bookings;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Les regles d'une reservation, testees sans rien demarrer.
 *
 * <p>Ni base, ni conteneur d'injection, ni Quarkus : {@code Booking} est une
 * classe, on l'instancie. C'est ce que tomato appelle le premier niveau, celui
 * qui doit rester instantane -- et c'est le meme test que dans les POC
 * hexagonaux, aux imports pres, parce que ce sont les memes regles.
 */
@DisplayName("Une reservation")
class BookingTest {

    private static final long PENALTY_THRESHOLD_MINUTES = 24 * 60;

    @Nested
    @DisplayName("a la demande")
    class WhenRequested {

        @Test
        @DisplayName("nait en attente, et sans place reservee")
        void is_born_pending_and_without_a_seat() {
            Booking booking = Bookings.pending();

            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
            assertThat(booking.isWaitlisted()).isFalse();
            assertThat(booking.isConfirmed()).isFalse();
            assertThat(booking.id()).isNotNull();
            assertThat(booking.requestedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("refuse un creneau deja commence")
        void refuses_a_slot_that_has_already_started() {
            assertThatThrownBy(() -> Bookings.pendingOn(slotIn(-1, 60))).isInstanceOf(BookingInThePastException.class);
        }

        @Test
        @DisplayName("refuse un creneau au-dela de l'horizon")
        void refuses_a_slot_beyond_the_horizon() {
            assertThatThrownBy(() -> Bookings.pendingOn(slotIn(24 * (MAX_DAYS_AHEAD + 1), 60)))
                    .isInstanceOf(BookingTooFarInAdvanceException.class);
        }

        @Test
        @DisplayName("accepte un creneau pile sur l'horizon")
        void accepts_a_slot_sitting_exactly_on_the_horizon() {
            assertThat(Bookings.pendingOn(slotIn(24 * MAX_DAYS_AHEAD, 60)).status())
                    .isEqualTo(BookingStatus.PENDING);
        }

        @Test
        @DisplayName("refuse une duree hors des bornes, des deux cotes")
        void refuses_a_duration_outside_the_bounds_either_way() {
            assertThatThrownBy(() -> Bookings.pendingOn(slotIn(24, MIN_DURATION - 1)))
                    .isInstanceOf(InvalidBookingDurationException.class)
                    .hasMessageContaining("trop courte");

            assertThatThrownBy(() -> Bookings.pendingOn(slotIn(24, MAX_DURATION + 1)))
                    .isInstanceOf(InvalidBookingDurationException.class)
                    .hasMessageContaining("trop longue");
        }

        @Test
        @DisplayName("accepte les durees pile sur les bornes")
        void accepts_the_durations_sitting_exactly_on_the_bounds() {
            assertThat(Bookings.pendingOn(slotIn(24, MIN_DURATION)).slot().durationInMinutes())
                    .isEqualTo(MIN_DURATION);
            assertThat(Bookings.pendingOn(slotIn(24, MAX_DURATION)).slot().durationInMinutes())
                    .isEqualTo(MAX_DURATION);
        }

        @Test
        @DisplayName("retient qui reserve quoi")
        void remembers_who_booked_what() {
            UUID resource = UUID.randomUUID();
            UUID attendee = UUID.randomUUID();

            Booking booking = Bookings.pendingOn(slotIn(24, 60), resource, attendee);

            assertThat(booking.resourceId()).isEqualTo(resource);
            assertThat(booking.attendeeId()).isEqualTo(attendee);
        }
    }

    @Nested
    @DisplayName("quand on la confirme")
    class WhenConfirmed {

        @Test
        @DisplayName("passe de l'attente a la confirmation")
        void goes_from_pending_to_confirmed() {
            Booking booking = Bookings.pending();

            booking.confirm();

            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.isConfirmed()).isTrue();
        }

        @Test
        @DisplayName("refuse de l'etre deux fois")
        void refuses_to_be_confirmed_twice() {
            Booking booking = Bookings.confirmed();

            assertThatThrownBy(booking::confirm)
                    .isInstanceOf(InvalidBookingTransitionException.class)
                    .hasMessageContaining("CONFIRMEE");
        }

        @Test
        @DisplayName("refuse de l'etre apres expiration")
        void refuses_to_be_confirmed_once_expired() {
            Booking booking = Bookings.pending();
            booking.expire();

            assertThatThrownBy(booking::confirm).isInstanceOf(InvalidBookingTransitionException.class);
        }
    }

    @Nested
    @DisplayName("quand on l'annule")
    class WhenCancelled {

        @Test
        @DisplayName("s'annule sans penalite au-dela du seuil")
        void cancels_without_penalty_beyond_the_threshold() {
            Booking booking = Bookings.confirmedOn(slotIn(48, 60));

            booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("s'annule avec penalite en deca du seuil")
        void cancels_with_a_penalty_inside_the_threshold() {
            Booking booking = Bookings.confirmedOn(slotIn(23, 60));

            booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED_WITH_PENALTY);
        }

        @Test
        @DisplayName("ne prend pas de penalite pile sur le seuil")
        void takes_no_penalty_exactly_on_the_threshold() {
            Booking booking = Bookings.confirmedOn(slotIn(24, 60));

            booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW);

            assertThat(booking.status()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("refuse de s'annuler quand son creneau est passe")
        void refuses_to_cancel_once_its_slot_is_over() {
            Booking booking = Bookings.confirmedOn(slotIn(24, 60));

            assertThatThrownBy(() -> booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW.plusDays(2)))
                    .isInstanceOf(CannotCancelBookingException.class);
        }

        @Test
        @DisplayName("refuse de s'annuler si elle n'a jamais ete confirmee")
        void refuses_to_cancel_if_it_was_never_confirmed() {
            Booking booking = Bookings.pending();

            assertThatThrownBy(() -> booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW))
                    .isInstanceOf(InvalidBookingTransitionException.class);
        }

        @Test
        @DisplayName("refuse de s'annuler deux fois")
        void refuses_to_cancel_twice() {
            Booking booking = Bookings.confirmedOn(slotIn(48, 60));
            booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW);

            assertThatThrownBy(() -> booking.cancel(PENALTY_THRESHOLD_MINUTES, NOW))
                    .isInstanceOf(InvalidBookingTransitionException.class);
        }
    }

    @Nested
    @DisplayName("sur liste d'attente")
    class OnTheWaitlist {

        @Test
        @DisplayName("se met en attente et y reste en attente")
        void joins_the_waitlist_and_stays_pending_there() {
            Booking booking = Bookings.waitlistedOn(slotIn(24, 60));

            assertThat(booking.isWaitlisted()).isTrue();
            assertThat(booking.status()).isEqualTo(BookingStatus.PENDING);
        }

        @Test
        @DisplayName("se laisse promouvoir quand une place se libere")
        void lets_itself_be_promoted_when_a_seat_frees_up() {
            Booking booking = Bookings.waitlistedOn(slotIn(24, 60));

            booking.promoteFromWaitlist();

            assertThat(booking.status()).isEqualTo(BookingStatus.CONFIRMED);
        }

        @Test
        @DisplayName("refuse d'etre promue si elle n'attendait pas")
        void refuses_promotion_if_it_was_not_waiting() {
            assertThatThrownBy(() -> Bookings.pending().promoteFromWaitlist())
                    .isInstanceOf(NotWaitlistedException.class);
        }

        @Test
        @DisplayName("refuse de rejoindre l'attente quand son sort est deja scelle")
        void refuses_to_join_the_waitlist_once_its_fate_is_sealed() {
            Booking booking = Bookings.confirmed();

            assertThatThrownBy(booking::joinWaitlist).isInstanceOf(InvalidBookingTransitionException.class);
        }
    }

    @Nested
    @DisplayName("face a l'expiration")
    class FacingExpiry {

        @Test
        @DisplayName("devient expirable passe le delai")
        void becomes_expirable_once_the_delay_is_past() {
            Booking booking = Bookings.pending();

            assertThat(booking.isExpirable(NOW.plusMinutes(14), 15)).isFalse();
            assertThat(booking.isExpirable(NOW.plusMinutes(15), 15)).isTrue();
            assertThat(booking.isExpirable(NOW.plusMinutes(16), 15)).isTrue();
        }

        @Test
        @DisplayName("n'est jamais expirable une fois confirmee")
        void is_never_expirable_once_confirmed() {
            Booking booking = Bookings.confirmed();

            assertThat(booking.isExpirable(NOW.plusDays(10), 15)).isFalse();
        }

        @Test
        @DisplayName("passe en expiree, et plus rien ensuite")
        void expires_and_then_nothing_more() {
            Booking booking = Bookings.pending();

            booking.expire();

            assertThat(booking.status()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(booking.status().isFinal()).isTrue();
        }
    }
}
