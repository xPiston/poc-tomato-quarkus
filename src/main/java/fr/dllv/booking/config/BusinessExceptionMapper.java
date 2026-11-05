package fr.dllv.booking.config;

import fr.dllv.booking.common.BusinessException;
import fr.dllv.booking.common.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Le seul endroit qui traduit une erreur metier en reponse HTTP.
 *
 * Il n'a aucune connaissance des exceptions concretes : chacune porte son
 * propre statut (voir {@link BusinessException}). Ajouter une regle metier
 * n'oblige donc pas a revenir ici.
 */
@Provider
public class BusinessExceptionMapper implements ExceptionMapper<BusinessException> {

    @Override
    public Response toResponse(BusinessException exception) {
        return Response.status(exception.status())
                .entity(new ErrorResponse(exception.code(), exception.getMessage()))
                .build();
    }
}
