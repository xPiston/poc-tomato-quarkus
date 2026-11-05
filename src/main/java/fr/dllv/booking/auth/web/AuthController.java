package fr.dllv.booking.auth.web;

import fr.dllv.booking.auth.AuthService;
import fr.dllv.booking.auth.AuthenticatedUser;
import fr.dllv.booking.auth.UserDto;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Connexion, profil, deconnexion.
 *
 * <p>Trois methodes qui extraient la requete et delegent -- aucune regle ici,
 * pas meme un {@code if} sur les identifiants.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService auth;
    private final SecurityIdentity identity;

    public AuthController(AuthService auth, SecurityIdentity identity) {
        this.auth = auth;
        this.identity = identity;
    }

    @POST
    @Path("/login")
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResponse login(LoginRequest request) {
        AuthService.Authentication authentication = auth.login(request.email(), request.password());

        return new LoginResponse(authentication.token(), UserDto.of(authentication.user()));
    }

    @GET
    @Path("/me")
    @Authenticated
    public MeResponse me() {
        return new MeResponse(UserDto.of((AuthenticatedUser) identity.getPrincipal()));
    }

    @POST
    @Path("/logout")
    @Authenticated
    public Response logout(@HeaderParam("Authorization") String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            auth.logout(authorization.substring(BEARER_PREFIX.length()));
        }

        return Response.noContent().build();
    }

    public record LoginRequest(String email, String password) {}

    public record LoginResponse(String token, UserDto user) {}

    public record MeResponse(UserDto user) {}
}
