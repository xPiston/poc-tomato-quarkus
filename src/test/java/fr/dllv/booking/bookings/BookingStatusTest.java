package fr.dllv.booking.bookings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("La machine a etats")
class BookingStatusTest {

    @Test
    @DisplayName("ne laisse une demande aller que vers la confirmation ou l'expiration")
    void lets_a_pending_request_go_only_to_confirmed_or_expired() {
        assertThat(BookingStatus.PENDING.allowedTransitions())
                .containsExactlyInAnyOrder(BookingStatus.CONFIRMED, BookingStatus.EXPIRED);
    }

    @Test
    @DisplayName("ne laisse une reservation confirmee qu'aboutir ou s'annuler")
    void lets_a_confirmed_booking_only_complete_or_cancel() {
        assertThat(BookingStatus.CONFIRMED.allowedTransitions())
                .containsExactlyInAnyOrder(
                        BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.CANCELLED_WITH_PENALTY);
    }

    @ParameterizedTest
    @EnumSource(
            value = BookingStatus.class,
            names = {"COMPLETED", "CANCELLED", "CANCELLED_WITH_PENALTY", "EXPIRED"})
    @DisplayName("ne laisse rien sortir d'un etat terminal")
    void lets_nothing_out_of_a_final_state(BookingStatus terminal) {
        assertThat(terminal.isFinal()).isTrue();
        assertThat(terminal.allowedTransitions()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("donne un libelle a chaque etat, pour le frontend")
    void gives_every_state_a_label_for_the_frontend(BookingStatus status) {
        assertThat(status.label()).isNotBlank().isUpperCase();
    }

    @Test
    @DisplayName("compte les deux annulations comme des annulations")
    void counts_both_cancellations_as_cancelled() {
        assertThat(BookingStatus.CANCELLED.isCancelled()).isTrue();
        assertThat(BookingStatus.CANCELLED_WITH_PENALTY.isCancelled()).isTrue();
        assertThat(BookingStatus.EXPIRED.isCancelled()).isFalse();
    }
}
