package fr.dllv.booking.catalog;

import fr.dllv.booking.common.BusinessException;
import java.util.UUID;

/** Aucune ressource reservable sous cet identifiant. */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(UUID resourceId) {
        super(NOT_FOUND, "Ressource %s introuvable.".formatted(resourceId));
    }
}
