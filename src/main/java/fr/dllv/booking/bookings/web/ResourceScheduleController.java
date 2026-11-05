package fr.dllv.booking.bookings.web;

import fr.dllv.booking.bookings.BookingDto;
import fr.dllv.booking.bookings.BookingService;
import fr.dllv.booking.bookings.TimeSlot;
import fr.dllv.booking.common.ApiEnvelope;
import fr.dllv.booking.common.ErrorResponse;
import fr.dllv.booking.common.IsoDateTime;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Le planning d'une ressource : {@code GET /api/resources/{id}/schedule}.
 *
 * <p>L'URL parle de ressources, le contenu est une liste de reservations --
 * et c'est le contenu qui decide ou le code vit. Ce controleur appartient donc
 * a la fonctionnalite reservation, pas au catalogue, qui n'a aucune raison de
 * savoir ce qu'est une reservation. Deux fonctionnalites servent des chemins
 * sous {@code /api/resources} ; JAX-RS s'en accommode tant que les chemins
 * complets diffèrent.
 */
@Path("/api/resources/{resourceId}/schedule")
@Authenticated
@Produces(MediaType.APPLICATION_JSON)
public class ResourceScheduleController {

    private final BookingService bookings;

    public ResourceScheduleController(BookingService bookings) {
        this.bookings = bookings;
    }

    @GET
    public Response schedule(
            @PathParam("resourceId") UUID resourceId, @QueryParam("from") String from, @QueryParam("to") String to) {

        OffsetDateTime start = IsoDateTime.parseOrNull(from);
        OffsetDateTime end = IsoDateTime.parseOrNull(to);

        if (start == null || end == null) {
            return Response.status(422)
                    .entity(new ErrorResponse("ValidationError", "Parametres from/to requis."))
                    .build();
        }

        List<BookingDto> schedule = bookings.findSchedule(resourceId, TimeSlot.of(start, end)).stream()
                .map(BookingDto::of)
                .toList();

        return Response.ok(new ApiEnvelope<>(schedule)).build();
    }
}
