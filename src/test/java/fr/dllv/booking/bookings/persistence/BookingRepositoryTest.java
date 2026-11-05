package fr.dllv.booking.bookings.persistence;

import static fr.dllv.booking.support.Bookings.slotIn;
import static org.assertj.core.api.Assertions.assertThat;

import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.TimeSlot;
import fr.dllv.booking.support.Bookings;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Les requetes, sur un vrai PostgreSQL.
 *
 * <p>Ce que ce fichier verifie n'est pas verifiable ailleurs : que le
 * chevauchement ecrit en SQL ({@code startsAt < :end and endsAt > :start}) dit
 * exactement la meme chose que le chevauchement ecrit en Java
 * ({@code TimeSlot.overlaps}). Deux formulations de la meme regle dans deux
 * langages, qui peuvent diverger sans que rien ne le signale -- les tests
 * unitaires du creneau passeraient, les tests du service aussi, puisqu'ils
 * mockent le repository.
 *
 * <p>C'est le niveau que tomato juge indispensable : "Unit tests alone are
 * insufficient. Test whole features end-to-end with real dependencies such as
 * databases". Quarkus demarre le conteneur PostgreSQL tout seul (Dev Services,
 * via Testcontainers), donc il n'y a rien a lancer a la main.
 *
 * <p>{@code @TestTransaction} annule tout a la fin de chaque test : la base
 * revient a son etat initial sans qu'aucun test ne nettoie derriere lui.
 */
@QuarkusTest
@DisplayName("Le repository des reservations")
class BookingRepositoryTest {

    @Inject
    BookingRepository bookings;

    private static final UUID RESOURCE = UUID.randomUUID();
    private static final UUID ATTENDEE = UUID.randomUUID();

    private Booking persistedConfirmed(TimeSlot slot) {
        Booking booking = Bookings.pendingOn(slot, RESOURCE, ATTENDEE);
        booking.confirm();
        bookings.persist(booking);

        return booking;
    }

    private Booking persistedWaitlisted(UUID resourceId, TimeSlot slot, java.time.OffsetDateTime requestedAt) {
        Booking booking = Booking.request(
                resourceId,
                UUID.randomUUID(),
                slot,
                requestedAt,
                Bookings.MAX_DAYS_AHEAD,
                Bookings.MIN_DURATION,
                Bookings.MAX_DURATION);
        booking.joinWaitlist();
        bookings.persist(booking);

        return booking;
    }

    @Test
    @TestTransaction
    @DisplayName("relit une reservation telle qu'elle a ete ecrite")
    void reads_a_booking_back_exactly_as_it_was_written() {
        TimeSlot slot = slotIn(24, 90);
        Booking written = persistedConfirmed(slot);

        bookings.getEntityManager().flush();
        bookings.getEntityManager().clear();

        Booking read = bookings.findById(written.id());

        assertThat(read).isNotNull();
        assertThat(read.resourceId()).isEqualTo(RESOURCE);
        assertThat(read.attendeeId()).isEqualTo(ATTENDEE);
        assertThat(read.status()).isEqualTo(written.status());
        assertThat(read.isWaitlisted()).isFalse();
        assertThat(read.slot()).isEqualTo(slot);
        assertThat(read.slot().durationInMinutes()).isEqualTo(90);
    }

    @Test
    @TestTransaction
    @DisplayName("compte comme occupe le creneau qui empiete, et pas celui qui touche")
    void counts_the_slot_that_bites_in_and_not_the_one_that_merely_touches() {
        persistedConfirmed(slotIn(24, 60));

        // Celui qui commence quand l'autre finit : libre.
        assertThat(bookings.countConfirmedOverlapping(RESOURCE, slotIn(25, 60))).isZero();

        // Celui qui finit quand l'autre commence : libre aussi.
        assertThat(bookings.countConfirmedOverlapping(RESOURCE, slotIn(23, 60))).isZero();

        // Celui qui mord d'une demi-heure : occupe.
        assertThat(bookings.countConfirmedOverlapping(RESOURCE, slotIn(24, 30))).isEqualTo(1);

        // Celui qui enveloppe entierement : occupe.
        assertThat(bookings.countConfirmedOverlapping(RESOURCE, slotIn(23, 180)))
                .isEqualTo(1);
    }

    @Test
    @TestTransaction
    @DisplayName("dit la meme chose que TimeSlot.overlaps, sur tous les cas de bord")
    void says_the_same_thing_as_the_java_overlap_on_every_edge_case() {
        TimeSlot booked = slotIn(24, 60);
        persistedConfirmed(booked);

        // Si ces deux colonnes divergent un jour, c'est que le SQL et le Java
        // ne sont plus d'accord sur ce qu'est un chevauchement.
        List<TimeSlot> candidates = List.of(
                slotIn(22, 60),
                slotIn(23, 60),
                slotIn(23, 90),
                slotIn(24, 15),
                slotIn(24, 60),
                slotIn(24, 120),
                slotIn(24, 30),
                slotIn(25, 60),
                slotIn(26, 60),
                slotIn(23, 240));

        for (TimeSlot candidate : candidates) {
            boolean inSql = bookings.countConfirmedOverlapping(RESOURCE, candidate) > 0;
            boolean inJava = booked.overlaps(candidate);

            assertThat(inSql)
                    .as("chevauchement de %s avec %s : SQL=%s, Java=%s", candidate, booked, inSql, inJava)
                    .isEqualTo(inJava);
        }
    }

    @Test
    @TestTransaction
    @DisplayName("ne compte que les reservations confirmees")
    void counts_only_the_confirmed_bookings() {
        TimeSlot slot = slotIn(24, 60);
        bookings.persist(Bookings.pendingOn(slot, RESOURCE, ATTENDEE));
        bookings.persist(Bookings.waitlistedOn(slot));

        assertThat(bookings.countConfirmedOverlapping(RESOURCE, slot)).isZero();
    }

    @Test
    @TestTransaction
    @DisplayName("sait s'ignorer quand on confirme")
    void knows_how_to_ignore_itself_when_confirming() {
        Booking booking = persistedConfirmed(slotIn(24, 60));

        assertThat(bookings.countConfirmedOverlapping(RESOURCE, booking.slot())).isEqualTo(1);
        assertThat(bookings.countConfirmedOverlappingExcluding(RESOURCE, booking.slot(), booking.id()))
                .isZero();
    }

    @Test
    @TestTransaction
    @DisplayName("ne melange pas deux ressources")
    void does_not_mix_two_resources() {
        TimeSlot slot = slotIn(24, 60);
        persistedConfirmed(slot);

        assertThat(bookings.countConfirmedOverlapping(UUID.randomUUID(), slot)).isZero();
    }

    @Test
    @TestTransaction
    @DisplayName("rend la liste d'attente dans l'ordre d'arrivee")
    void hands_back_the_waitlist_in_the_order_people_arrived() {
        TimeSlot slot = slotIn(24, 60);
        UUID resource = UUID.randomUUID();

        // Insérées a l'envers de leur ordre d'arrivee : si le tri n'etait pas
        // dans la requete, elles ressortiraient dans cet ordre-la.
        Booking second = persistedWaitlisted(resource, slot, Bookings.NOW.plusMinutes(5));
        Booking first = persistedWaitlisted(resource, slot, Bookings.NOW);

        assertThat(bookings.findWaitlistedOverlapping(resource, slot))
                .extracting(Booking::id)
                .containsExactly(first.id(), second.id());
    }

    @Test
    @TestTransaction
    @DisplayName("ne compte pas une demande en attente parmi les places prises")
    void does_not_count_a_waiting_request_among_the_taken_seats() {
        TimeSlot slot = slotIn(24, 60);
        UUID resource = UUID.randomUUID();
        persistedWaitlisted(resource, slot, Bookings.NOW);

        assertThat(bookings.countConfirmedOverlapping(resource, slot)).isZero();
    }

    @Test
    @TestTransaction
    @DisplayName("ne remonte pour expiration que les demandes assez vieilles")
    void only_brings_back_requests_old_enough_to_expire() {
        Booking old = Bookings.pendingOn(slotIn(24, 60), RESOURCE, ATTENDEE);
        bookings.persist(old);

        assertThat(bookings.findPendingRequestedBefore(Bookings.NOW.minusMinutes(1)))
                .extracting(Booking::id)
                .doesNotContain(old.id());

        assertThat(bookings.findPendingRequestedBefore(Bookings.NOW.plusMinutes(1)))
                .extracting(Booking::id)
                .contains(old.id());
    }

    @Test
    @TestTransaction
    @DisplayName("rend les reservations d'un participant, du creneau le plus proche au plus lointain")
    void hands_back_an_attendee_bookings_nearest_slot_first() {
        UUID attendee = UUID.randomUUID();
        Booking later = Bookings.pendingOn(slotIn(72, 60), RESOURCE, attendee);
        Booking sooner = Bookings.pendingOn(slotIn(24, 60), RESOURCE, attendee);
        bookings.persist(later);
        bookings.persist(sooner);

        assertThat(bookings.findByAttendee(attendee)).extracting(Booking::id).containsExactly(sooner.id(), later.id());
    }

    @Test
    @TestTransaction
    @DisplayName("rend le planning d'une ressource trie par heure de debut")
    void hands_back_a_resource_schedule_sorted_by_start_time() {
        UUID resource = UUID.randomUUID();
        Booking afternoon = Bookings.pendingOn(slotIn(30, 60), resource, ATTENDEE);
        Booking morning = Bookings.pendingOn(slotIn(25, 60), resource, ATTENDEE);
        bookings.persist(afternoon);
        bookings.persist(morning);

        assertThat(bookings.findOverlapping(resource, slotIn(0, 48 * 60)))
                .extracting(Booking::id)
                .containsExactly(morning.id(), afternoon.id());
    }
}
