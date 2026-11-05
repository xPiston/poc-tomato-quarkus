package fr.dllv.booking.bookings;

import static fr.dllv.booking.support.Bookings.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Un creneau horaire")
class TimeSlotTest {

    @Test
    @DisplayName("refuse de naitre si sa fin ne suit pas son debut")
    void rejects_an_end_that_does_not_follow_its_start() {
        assertThatThrownBy(() -> TimeSlot.of(NOW, NOW)).isInstanceOf(InvalidTimeSlotException.class);
        assertThatThrownBy(() -> TimeSlot.of(NOW, NOW.minusHours(1))).isInstanceOf(InvalidTimeSlotException.class);
    }

    @Test
    @DisplayName("ne chevauche pas celui qu'il touche")
    void does_not_overlap_the_slot_it_merely_touches() {
        TimeSlot tenToEleven = TimeSlot.of(NOW, NOW.plusHours(1));
        TimeSlot elevenToTwelve = TimeSlot.of(NOW.plusHours(1), NOW.plusHours(2));

        assertThat(tenToEleven.overlaps(elevenToTwelve)).isFalse();
        assertThat(elevenToTwelve.overlaps(tenToEleven)).isFalse();
    }

    @Test
    @DisplayName("chevauche celui qui l'entame, dans les deux sens")
    void overlaps_a_slot_that_bites_into_it_either_way_round() {
        TimeSlot tenToEleven = TimeSlot.of(NOW, NOW.plusHours(1));
        TimeSlot halfPastTenToHalfPastEleven = TimeSlot.of(NOW.plusMinutes(30), NOW.plusMinutes(90));

        assertThat(tenToEleven.overlaps(halfPastTenToHalfPastEleven)).isTrue();
        assertThat(halfPastTenToHalfPastEleven.overlaps(tenToEleven)).isTrue();
    }

    @Test
    @DisplayName("chevauche celui qu'il contient entierement")
    void overlaps_a_slot_it_fully_contains() {
        TimeSlot wide = TimeSlot.of(NOW, NOW.plusHours(4));
        TimeSlot inside = TimeSlot.of(NOW.plusHours(1), NOW.plusHours(2));

        assertThat(wide.overlaps(inside)).isTrue();
        assertThat(inside.overlaps(wide)).isTrue();
    }

    @Test
    @DisplayName("compte sa duree en minutes")
    void counts_its_duration_in_minutes() {
        assertThat(TimeSlot.of(NOW, NOW.plusMinutes(90)).durationInMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("sait s'il a commence, et s'il est fini")
    void knows_whether_it_has_started_and_whether_it_has_ended() {
        TimeSlot slot = TimeSlot.of(NOW.minusHours(2), NOW.minusHours(1));

        assertThat(slot.startsBefore(NOW)).isTrue();
        assertThat(slot.hasEndedBy(NOW)).isTrue();
        assertThat(slot.hasEndedBy(NOW.minusHours(3))).isFalse();
    }

    @Test
    @DisplayName("tient sa fin pour non atteinte a la seconde ou elle tombe")
    void treats_its_end_as_reached_exactly_when_it_falls() {
        TimeSlot slot = TimeSlot.of(NOW.minusHours(1), NOW);

        // Intervalle semi-ouvert : a NOW pile, le creneau est termine.
        assertThat(slot.hasEndedBy(NOW)).isTrue();
    }

    @Test
    @DisplayName("compte les minutes qui restent avant son debut, negatives s'il a commence")
    void counts_the_minutes_left_before_it_starts() {
        assertThat(TimeSlot.of(NOW.plusHours(2), NOW.plusHours(3)).minutesUntilStart(NOW))
                .isEqualTo(120);
        assertThat(TimeSlot.of(NOW.minusHours(1), NOW.plusHours(1)).minutesUntilStart(NOW))
                .isEqualTo(-60);
    }
}
