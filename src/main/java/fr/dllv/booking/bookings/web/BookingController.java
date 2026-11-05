package fr.dllv.booking.bookings.web;

import fr.dllv.booking.auth.AuthenticatedUser;
import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.BookingDto;
import fr.dllv.booking.bookings.BookingService;
import fr.dllv.booking.bookings.CancellationDto;
import fr.dllv.booking.bookings.CreateBookingCommand;
import fr.dllv.booking.bookings.TimeSlot;
import fr.dllv.booking.common.ApiEnvelope;
import fr.dllv.booking.common.ErrorResponse;
import fr.dllv.booking.common.IsoDateTime;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les reservations, en HTTP.
 *
 * <p>Ce controleur ne contient aucune regle metier, et c'est verifiable : il
 * n'y a pas une seule comparaison de dates, pas un {@code if} sur un statut.
 * Ce qu'il fait tient en trois gestes -- lire l'utilisateur authentifie,
 * convertir la charge JSON en types du metier, appeler une methode du service.
 * Les erreurs metier remontent et {@code BusinessExceptionMapper} les traduit.
 *
 * <p>Il ne porte pas non plus de {@code @Transactional} : la transaction est
 * declaree sur le service, la ou le cas d'usage est defini. Les POC
 * hexagonaux de la famille la posent sur leur ressource HTTP, ce qui oblige a
 * la reposer a l'identique sur chaque nouvelle entree -- la commande CLI l'a
 * d'ailleurs aussi chez eux.
 *
 * <p>La seule validation ici est de forme : un UUID qui n'en est pas un, une
 * date illisible. Elle repond 422 avec le detail par champ, parce qu'un
 * formulaire a besoin de savoir quoi surligner. Les regles de validite du
 * creneau -- passe, duree, horizon -- ne sont pas de ce ressort et vivent dans
 * {@code Booking}.
 */
@Path("/api/bookings")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class BookingController {

    private final BookingService bookings;
    private final SecurityIdentity identity;

    public BookingController(BookingService bookings, SecurityIdentity identity) {
        this.bookings = bookings;
        this.identity = identity;
    }

    @GET
    public ApiEnvelope<List<BookingDto>> mine() {
        List<BookingDto> mine = bookings.findForAttendee(currentUser().attendeeId()).stream()
                .map(BookingDto::of)
                .toList();

        return new ApiEnvelope<>(mine);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(CreateBookingRequest request) {
        Map<String, String> violations = formViolations(request);

        if (!violations.isEmpty()) {
            return Response.status(422)
                    .entity(ErrorResponse.validation(violations))
                    .build();
        }

        Booking booking = bookings.create(new CreateBookingCommand(
                UUID.fromString(request.resourceId()),
                currentUser().attendeeId(),
                TimeSlot.of(IsoDateTime.parse(request.startsAt()), IsoDateTime.parse(request.endsAt())),
                request.joinWaitlistOrDefault()));

        return Response.status(201)
                .entity(new ApiEnvelope<>(BookingDto.of(booking)))
                .build();
    }

    @POST
    @Path("/{id}/confirm")
    public ApiEnvelope<BookingDto> confirm(@PathParam("id") UUID id) {
        return new ApiEnvelope<>(BookingDto.of(bookings.confirm(id)));
    }

    @POST
    @Path("/{id}/cancel")
    public CancellationDto cancel(@PathParam("id") UUID id) {
        BookingService.CancellationOutcome outcome = bookings.cancel(id);

        return new CancellationDto(
                outcome.cancelled().id().toString(),
                outcome.cancelled().status().label(),
                outcome.penaltyApplied(),
                outcome.promotedBooking().map(b -> b.id().toString()).orElse(null));
    }

    private AuthenticatedUser currentUser() {
        return (AuthenticatedUser) identity.getPrincipal();
    }

    /** Ce que le JSON a de mal forme, champ par champ. Vide si tout est lisible. */
    private Map<String, String> formViolations(CreateBookingRequest request) {
        Map<String, String> violations = new LinkedHashMap<>();

        if (parseUuidOrNull(request.resourceId()) == null) {
            violations.put("resource_id", "UUID de ressource requis.");
        }

        OffsetDateTime start = IsoDateTime.parseOrNull(request.startsAt());
        OffsetDateTime end = IsoDateTime.parseOrNull(request.endsAt());

        if (start == null) {
            violations.put("starts_at", "Date de debut invalide.");
        }
        if (end == null) {
            violations.put("ends_at", "Date de fin invalide.");
        }
        if (start != null && end != null && !end.isAfter(start)) {
            violations.put("ends_at", "La fin doit etre posterieure au debut.");
        }

        return violations;
    }

    private static UUID parseUuidOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** La charge JSON attendue. Tout est en chaine : rien n'est encore valide. */
    public record CreateBookingRequest(String resourceId, String startsAt, String endsAt, Boolean joinWaitlist) {

        public boolean joinWaitlistOrDefault() {
            return joinWaitlist != null && joinWaitlist;
        }
    }
}
