package fr.dllv.booking.catalog.persistence;

import fr.dllv.booking.catalog.BookableResource;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

/**
 * Acces aux ressources reservables.
 *
 * <p>Une classe concrete, pas une interface doublee d'une implementation :
 * {@code PanacheRepositoryBase} vient du framework et apporte deja
 * {@code findById}, {@code persist} et la pagination. Ce fichier n'ajoute que
 * la requete que le framework ne devine pas.
 */
@ApplicationScoped
public class BookableResourceRepository implements PanacheRepositoryBase<BookableResource, UUID> {

    public List<BookableResource> listAllOrderedByName() {
        return list("order by name");
    }
}
