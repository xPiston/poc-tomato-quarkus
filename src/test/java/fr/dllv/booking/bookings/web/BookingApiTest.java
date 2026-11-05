package fr.dllv.booking.bookings.web;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La fonctionnalite entiere, par HTTP, contre une vraie base.
 *
 * <p>C'est le niveau que tomato juge non negociable : "Test not only units, but
 * whole features". Rien n'est mocke -- le serveur tourne, PostgreSQL tourne
 * (Dev Services), les migrations ont joue, le jeu de demonstration est en base,
 * et les requetes partent par le reseau avec un vrai jeton.
 *
 * <p>Les creneaux utilises sont a dix jours et sur la salle Kilimandjaro, que
 * le jeu de demonstration ne touche pas : les tests ne se genent donc pas entre
 * eux et ne dependent pas de leur ordre d'execution. Chacun prend une heure de
 * la journee qui lui est propre.
 */
@QuarkusTest
@DisplayName("L'API des reservations")
class BookingApiTest {

    /** La salle Kilimandjaro du jeu de demonstration : capacite 1, planning vide. */
    private static final String KILIMANDJARO = "33333333-3333-4333-8333-333333333333";

    private String token;

    @BeforeEach
    void logIn() {
        token = given().contentType(ContentType.JSON)
                .body(Map.of("email", "alice@booking.test", "password", "password"))
                .post("/api/login")
                .then()
                .statusCode(200)
                .body("user.email", equalTo("alice@booking.test"))
                .body("user.attendee_id", notNullValue())
                .extract()
                .path("token");
    }

    /** Un creneau d'une heure, a dix jours, a l'heure demandee. */
    private static String at(int hour) {
        return OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(10)
                .withHour(hour)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private io.restassured.specification.RequestSpecification authenticated() {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private io.restassured.response.Response book(String resourceId, int fromHour, int toHour, boolean waitlist) {
        return authenticated()
                .body(Map.of(
                        "resource_id",
                        resourceId,
                        "starts_at",
                        at(fromHour),
                        "ends_at",
                        at(toHour),
                        "join_waitlist",
                        waitlist))
                .post("/api/bookings");
    }

    @Test
    @DisplayName("refuse tout ce qui n'a pas de jeton")
    void turns_away_everything_without_a_token() {
        given().get("/api/bookings").then().statusCode(401);
        given().get("/api/resources/" + KILIMANDJARO + "/schedule").then().statusCode(401);
        given().contentType(ContentType.JSON)
                .body(Map.of("resource_id", KILIMANDJARO, "starts_at", at(8), "ends_at", at(9)))
                .post("/api/bookings")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("refuse un jeton inconnu")
    void turns_away_an_unknown_token() {
        given().header("Authorization", "Bearer pas-un-vrai-jeton")
                .get("/api/bookings")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("laisse voir les ressources sans etre connecte")
    void lets_anyone_see_the_resources() {
        given().get("/api/resources")
                .then()
                .statusCode(200)
                .body("data.size()", equalTo(4))
                .body("data.capacity", org.hamcrest.Matchers.hasItem(4));
    }

    @Test
    @DisplayName("refuse des identifiants faux, sans dire lequel")
    void refuses_wrong_credentials_without_saying_which_part_is_wrong() {
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "alice@booking.test", "password", "faux"))
                .post("/api/login")
                .then()
                .statusCode(422)
                .body("message", equalTo("Identifiants invalides."));

        given().contentType(ContentType.JSON)
                .body(Map.of("email", "personne@booking.test", "password", "password"))
                .post("/api/login")
                .then()
                .statusCode(422)
                .body("message", equalTo("Identifiants invalides."));
    }

    @Test
    @DisplayName("cree une demande en attente, puis la confirme")
    void creates_a_pending_request_then_confirms_it() {
        String id = book(KILIMANDJARO, 6, 7, false)
                .then()
                .statusCode(201)
                .body("data.status", equalTo("EN_ATTENTE"))
                .body("data.waitlisted", equalTo(false))
                .body("data.resource_id", equalTo(KILIMANDJARO))
                .body("data.starts_at", notNullValue())
                .extract()
                .path("data.id");

        authenticated()
                .post("/api/bookings/" + id + "/confirm")
                .then()
                .statusCode(200)
                .body("data.status", equalTo("CONFIRMEE"));
    }

    @Test
    @DisplayName("refuse de confirmer deux fois")
    void refuses_to_confirm_twice() {
        String id =
                book(KILIMANDJARO, 8, 9, false).then().statusCode(201).extract().path("data.id");

        authenticated().post("/api/bookings/" + id + "/confirm").then().statusCode(200);
        authenticated().post("/api/bookings/" + id + "/confirm").then().statusCode(409);
    }

    @Test
    @DisplayName("rend 404 sur une reservation qui n'existe pas")
    void answers_404_for_a_booking_that_does_not_exist() {
        authenticated()
                .post("/api/bookings/8f3b2c1e-0000-4000-8000-000000000000/confirm")
                .then()
                .statusCode(404);
    }

    @Test
    @DisplayName("rend 422 avec le detail par champ quand la charge est mal formee")
    void answers_422_with_per_field_detail_when_the_payload_is_malformed() {
        authenticated()
                .body(Map.of("resource_id", "pas-un-uuid", "starts_at", "hier", "ends_at", "demain"))
                .post("/api/bookings")
                .then()
                .statusCode(422)
                .body("error", equalTo("ValidationError"))
                .body("violations", hasKey("resource_id"))
                .body("violations", hasKey("starts_at"))
                .body("violations", hasKey("ends_at"));
    }

    @Test
    @DisplayName("rend 422 quand la fin precede le debut")
    void answers_422_when_the_end_comes_before_the_start() {
        authenticated()
                .body(Map.of("resource_id", KILIMANDJARO, "starts_at", at(10), "ends_at", at(9)))
                .post("/api/bookings")
                .then()
                .statusCode(422)
                .body("violations", hasKey("ends_at"));
    }

    @Test
    @DisplayName("rend 422 sur un creneau deja passe, et c'est bien la regle metier qui parle")
    void answers_422_for_a_slot_already_gone() {
        OffsetDateTime yesterday = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        authenticated()
                .body(Map.of(
                        "resource_id", KILIMANDJARO,
                        "starts_at", yesterday.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        "ends_at", yesterday.plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .post("/api/bookings")
                .then()
                .statusCode(422)
                .body("error", equalTo("BookingInThePastException"));
    }

    @Test
    @DisplayName("rend 422 au-dela de l'horizon de reservation")
    void answers_422_beyond_the_booking_horizon() {
        OffsetDateTime farOff = OffsetDateTime.now(ZoneOffset.UTC).plusDays(60);

        authenticated()
                .body(Map.of(
                        "resource_id", KILIMANDJARO,
                        "starts_at", farOff.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        "ends_at", farOff.plusHours(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                .post("/api/bookings")
                .then()
                .statusCode(422)
                .body("error", equalTo("BookingTooFarInAdvanceException"));
    }

    @Test
    @DisplayName("rend 404 quand la ressource n'existe pas")
    void answers_404_when_the_resource_does_not_exist() {
        book("7c9e6679-0000-4000-8000-000000000000", 11, 12, false).then().statusCode(404);
    }

    /**
     * Le scenario complet de la liste d'attente, en un seul test parce que
     * c'est une seule histoire : une place prise, quelqu'un qui attend, une
     * annulation, et la place qui change de main.
     */
    @Test
    @DisplayName("donne la place liberee a celui qui attendait")
    void hands_the_freed_seat_to_whoever_was_waiting() {
        String taken = book(KILIMANDJARO, 13, 14, false)
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
        authenticated().post("/api/bookings/" + taken + "/confirm").then().statusCode(200);

        // La salle est maintenant complete sur ce creneau : sans liste
        // d'attente, c'est un conflit.
        book(KILIMANDJARO, 13, 14, false).then().statusCode(409).body("error", equalTo("OverlappingBookingException"));

        // Avec liste d'attente, la demande est acceptee mais marquee.
        String waiting = book(KILIMANDJARO, 13, 14, true)
                .then()
                .statusCode(201)
                .body("data.waitlisted", equalTo(true))
                .body("data.status", equalTo("EN_ATTENTE"))
                .extract()
                .path("data.id");

        // L'annulation libere la place et promeut celui qui attendait.
        authenticated()
                .post("/api/bookings/" + taken + "/cancel")
                .then()
                .statusCode(200)
                .body("status", equalTo("ANNULEE"))
                .body("penalty_applied", equalTo(false))
                .body("promoted_booking_id", equalTo(waiting));

        authenticated()
                .get("/api/bookings")
                .then()
                .statusCode(200)
                .body("data.find { it.id == '%s' }.status".formatted(waiting), equalTo("CONFIRMEE"));
    }

    @Test
    @DisplayName("n'annonce aucune promotion quand personne n'attendait")
    void announces_no_promotion_when_nobody_was_waiting() {
        String id = book(KILIMANDJARO, 15, 16, false)
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");
        authenticated().post("/api/bookings/" + id + "/confirm").then().statusCode(200);

        authenticated()
                .post("/api/bookings/" + id + "/cancel")
                .then()
                .statusCode(200)
                .body("promoted_booking_id", nullValue());
    }

    @Test
    @DisplayName("laisse plusieurs personnes sur une ressource partagee")
    void lets_several_people_onto_a_shared_resource() {
        // L'open space a une capacite de 4 : deux reservations confirmees sur
        // le meme creneau n'ont aucune raison de se gener.
        String coworking = "44444444-4444-4444-8444-444444444444";

        String first =
                book(coworking, 17, 18, false).then().statusCode(201).extract().path("data.id");
        authenticated().post("/api/bookings/" + first + "/confirm").then().statusCode(200);

        String second =
                book(coworking, 17, 18, false).then().statusCode(201).extract().path("data.id");
        authenticated().post("/api/bookings/" + second + "/confirm").then().statusCode(200);
    }

    @Test
    @DisplayName("rend le planning d'une ressource sur une fenetre")
    void hands_back_a_resource_schedule_over_a_window() {
        String id = book(KILIMANDJARO, 19, 20, false)
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");

        OffsetDateTime from = OffsetDateTime.now(ZoneOffset.UTC).plusDays(9);

        authenticated()
                .queryParam("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .queryParam("to", from.plusDays(2).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .get("/api/resources/" + KILIMANDJARO + "/schedule")
                .then()
                .statusCode(200)
                .body("data.size()", greaterThanOrEqualTo(1))
                .body("data.id", org.hamcrest.Matchers.hasItem(id));
    }

    @Test
    @DisplayName("exige les bornes de la fenetre de planning")
    void insists_on_the_schedule_window_bounds() {
        authenticated()
                .get("/api/resources/" + KILIMANDJARO + "/schedule")
                .then()
                .statusCode(422);
    }

    @Test
    @DisplayName("ne montre a chacun que ses reservations")
    void shows_each_person_only_their_own_bookings() {
        String mine = book(KILIMANDJARO, 21, 22, false)
                .then()
                .statusCode(201)
                .extract()
                .path("data.id");

        String bobToken = given().contentType(ContentType.JSON)
                .body(Map.of("email", "bob@booking.test", "password", "password"))
                .post("/api/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");

        given().header("Authorization", "Bearer " + bobToken)
                .get("/api/bookings")
                .then()
                .statusCode(200)
                .body("data.id", not(org.hamcrest.Matchers.hasItem(mine)));
    }

    @Test
    @DisplayName("invalide le jeton a la deconnexion")
    void invalidates_the_token_on_logout() {
        authenticated().get("/api/me").then().statusCode(200);
        authenticated().post("/api/logout").then().statusCode(204);
        authenticated().get("/api/bookings").then().statusCode(401);
    }
}
