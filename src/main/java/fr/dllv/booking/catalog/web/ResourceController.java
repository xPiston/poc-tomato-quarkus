package fr.dllv.booking.catalog.web;

import fr.dllv.booking.catalog.CatalogService;
import fr.dllv.booking.catalog.ResourceDto;
import fr.dllv.booking.common.ApiEnvelope;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

/**
 * La liste des ressources reservables, en lecture.
 *
 * <p>Elle est publique : le frontend doit pouvoir peindre la page de
 * connexion avec les salles visibles. Le planning d'une ressource, lui, exige
 * un jeton et vit du cote {@code bookings} -- c'est une liste de reservations,
 * pas une propriete du catalogue.
 */
@Path("/api/resources")
@Produces(MediaType.APPLICATION_JSON)
public class ResourceController {

    private final CatalogService catalog;

    public ResourceController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GET
    public ApiEnvelope<List<ResourceDto>> list() {
        List<ResourceDto> resources =
                catalog.findAll().stream().map(ResourceDto::of).toList();

        return new ApiEnvelope<>(resources);
    }
}
