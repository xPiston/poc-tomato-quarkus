package fr.dllv.booking.auth;

import fr.dllv.booking.auth.persistence.ApiTokenRepository;
import fr.dllv.booking.auth.persistence.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Connexion, deconnexion, et validation d'un jeton.
 *
 * <p>C'est l'API publique de la fonctionnalite "comptes". Le mecanisme
 * d'authentification HTTP de Quarkus (sous-package {@code security}) passe par
 * elle plutot que par les repositories, comme tout le monde.
 */
@ApplicationScoped
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final ApiTokenRepository tokens;
    private final Clock clock;

    public AuthService(UserRepository users, ApiTokenRepository tokens, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.clock = clock;
    }

    /**
     * Verifie les identifiants et emet un jeton.
     *
     * @throws InvalidCredentialsException email inconnu ou mot de passe faux
     */
    @Transactional
    public Authentication login(String email, String password) {
        User user = users.findByEmail(email == null ? "" : email).orElseThrow(InvalidCredentialsException::new);

        if (!BcryptUtil.matches(password == null ? "" : password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        ApiToken token = new ApiToken(user, generateToken(), OffsetDateTime.now(clock));
        tokens.persist(token);

        return new Authentication(token.token(), AuthenticatedUser.of(user));
    }

    /** Resout un jeton en utilisateur, ou rien si le jeton est inconnu. */
    @Transactional
    public Optional<AuthenticatedUser> resolve(String token) {
        return tokens.findWithUser(token).map(ApiToken::user).map(AuthenticatedUser::of);
    }

    /** Revoque un jeton. Silencieux si le jeton n'existe pas. */
    @Transactional
    public void logout(String token) {
        tokens.deleteById(token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        return HexFormat.of().formatHex(bytes);
    }

    /** Ce que rend une connexion reussie. */
    public record Authentication(String token, AuthenticatedUser user) {}
}
