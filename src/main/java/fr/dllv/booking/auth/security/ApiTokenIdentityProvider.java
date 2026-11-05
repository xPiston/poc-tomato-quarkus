package fr.dllv.booking.auth.security;

import fr.dllv.booking.auth.AuthService;
import fr.dllv.booking.auth.AuthenticatedUser;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.function.Supplier;

/**
 * Transforme un jeton en identite Quarkus.
 *
 * <p>Il demande la resolution a {@code AuthService} au lieu d'interroger la
 * base lui-meme : l'authentification est une fonctionnalite, pas une requete
 * SQL que chaque appelant refait a sa facon.
 *
 * <p>{@code runBlocking} est necessaire parce que cette phase tourne sur la
 * boucle d'evenements Vert.x, alors que la lecture en base est bloquante.
 */
@ApplicationScoped
public class ApiTokenIdentityProvider implements IdentityProvider<TokenAuthenticationRequest> {

    private final AuthService auth;

    public ApiTokenIdentityProvider(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public Class<TokenAuthenticationRequest> getRequestType() {
        return TokenAuthenticationRequest.class;
    }

    @Override
    public Uni<SecurityIdentity> authenticate(
            TokenAuthenticationRequest request, AuthenticationRequestContext context) {
        Supplier<SecurityIdentity> lookup =
                () -> auth.resolve(request.getToken().getToken())
                        .map(ApiTokenIdentityProvider::identityOf)
                        .orElse(null);

        return context.runBlocking(lookup);
    }

    private static SecurityIdentity identityOf(AuthenticatedUser user) {
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(user)
                .addRole("user")
                .build();
    }
}
