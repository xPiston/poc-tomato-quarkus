package fr.dllv.booking;

import static org.assertj.core.api.Assertions.assertThat;

import fr.dllv.booking.auth.AuthService;
import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.BookingStatus;
import fr.dllv.booking.bookings.persistence.BookingRepository;
import fr.dllv.booking.catalog.BookableResource;
import fr.dllv.booking.catalog.CatalogService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le jeu de demonstration, relu par l'application.
 *
 * <p>Ces donnees sont inserees en SQL par {@code V2__demo_data.sql}, donc elles
 * n'ont traverse aucune regle du domaine : rien n'empeche une migration d'y
 * ecrire un creneau a l'envers, un statut qui n'existe pas ou une empreinte de
 * mot de passe fausse. C'est le prix du seed par migration, et ce fichier est
 * la contrepartie -- il relit tout par les classes de l'application, donc par
 * les memes conversions que la production.
 *
 * <p>La verification la plus utile est la plus bete : que le mot de passe des
 * comptes de demonstration soit vraiment "password". Sans elle, une faute de
 * frappe dans l'empreinte ne se decouvrirait qu'a la premiere tentative de
 * connexion de quelqu'un qui vient de cloner le depot.
 */
@QuarkusTest
@DisplayName("Le jeu de demonstration")
class DemoDataMigrationTest {

    private static final String EVEREST = "11111111-1111-4111-8111-111111111111";

    @Inject
    CatalogService catalog;

    @Inject
    BookingRepository bookings;

    @Inject
    AuthService auth;

    @Test
    @DisplayName("ouvre bien les trois comptes avec le mot de passe annonce")
    void really_opens_the_three_accounts_with_the_advertised_password() {
        for (String email : List.of("alice@booking.test", "bob@booking.test", "carol@booking.test")) {
            AuthService.Authentication authentication = auth.login(email, "password");

            assertThat(authentication.token()).hasSize(64);
            assertThat(authentication.user().email()).isEqualTo(email);
            assertThat(authentication.user().attendeeId()).isNotNull();

            auth.logout(authentication.token());
        }
    }

    @Test
    @DisplayName("propose quatre ressources, dont un espace partage")
    void offers_four_resources_one_of_them_shared() {
        List<BookableResource> resources = catalog.findAll();

        assertThat(resources).hasSize(4);
        assertThat(resources).extracting(BookableResource::name).contains("Salle Everest");
        assertThat(resources)
                .allSatisfy(resource -> assertThat(resource.capacity()).isGreaterThanOrEqualTo(1));
        assertThat(resources)
                .filteredOn(resource -> resource.capacity() > 1)
                .singleElement()
                .satisfies(open -> assertThat(open.capacity()).isEqualTo(4));
    }

    @Test
    @DisplayName("place toutes ses reservations dans le futur, pour que la demo soit jouable")
    @Transactional
    void puts_all_its_bookings_in_the_future_so_the_demo_can_be_played() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Booking> seeded = bookings.listAll();

        assertThat(seeded).isNotEmpty();
        assertThat(seeded).allSatisfy(booking -> {
            assertThat(booking.slot().startsAt()).isAfter(now);
            assertThat(booking.slot().endsAt()).isAfter(booking.slot().startsAt());
            assertThat(booking.slot().durationInMinutes()).isGreaterThanOrEqualTo(15);
        });
    }

    @Test
    @DisplayName("met en place le scenario de liste d'attente sur Everest")
    @Transactional
    void sets_up_the_waitlist_scenario_on_everest() {
        List<Booking> everest = bookings.list("resourceId", UUID.fromString(EVEREST));

        // Un creneau tenu par quelqu'un, et quelqu'un d'autre qui attend
        // exactement dessus : annuler le premier doit promouvoir le second.
        Booking waiting = everest.stream()
                .filter(Booking::isWaitlisted)
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucune demande en liste d'attente dans le jeu de demo"));

        assertThat(waiting.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(everest)
                .filteredOn(booking -> booking.isConfirmed() && booking.slot().overlaps(waiting.slot()))
                .as("la demande en attente doit viser un creneau deja pris, sinon il n'y a rien a attendre")
                .isNotEmpty();
    }
}
