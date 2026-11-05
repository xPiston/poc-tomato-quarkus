package fr.dllv.booking.auth.persistence;

import fr.dllv.booking.auth.ApiToken;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Acces aux jetons d'API.
 *
 * <p>Le compte porteur est charge dans la meme requete ({@code join fetch}) :
 * la validation d'un jeton a besoin des deux, et sans ca l'association LAZY
 * provoque un second aller-retour -- voire une {@code LazyInitializationException},
 * la validation ayant lieu avant l'entree dans la ressource REST.
 */
@ApplicationScoped
public class ApiTokenRepository implements PanacheRepositoryBase<ApiToken, String> {

    public Optional<ApiToken> findWithUser(String token) {
        return find("from ApiToken t join fetch t.user where t.token = ?1", token)
                .firstResultOptional();
    }
}
