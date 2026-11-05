package fr.dllv.booking.catalog;

import fr.dllv.booking.catalog.persistence.BookableResourceRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * L'API publique de la fonctionnalite "catalogue".
 *
 * <p>C'est par cette classe, et seulement par elle, que les autres
 * fonctionnalites atteignent les ressources reservables -- jamais par le
 * repository, qui vit dans un sous-package interne. {@code bookings} a besoin
 * de connaitre la capacite d'une salle, pas la facon dont elle est stockee.
 * Un test d'architecture verifie que la regle tient (voir
 * {@code ArchitectureTest}).
 *
 * <p>Elle est tres mince, et c'est normal : le catalogue n'a presque pas de
 * regles. Tomato ne demande pas d'etoffer une couche pour la symetrie.
 */
@ApplicationScoped
public class CatalogService {

    private final BookableResourceRepository resources;

    public CatalogService(BookableResourceRepository resources) {
        this.resources = resources;
    }

    @Transactional
    public List<BookableResource> findAll() {
        return resources.listAllOrderedByName();
    }

    /**
     * @throws ResourceNotFoundException si la ressource n'existe pas
     */
    @Transactional
    public BookableResource getById(UUID id) {
        BookableResource resource = resources.findById(id);

        if (resource == null) {
            throw new ResourceNotFoundException(id);
        }

        return resource;
    }
}
