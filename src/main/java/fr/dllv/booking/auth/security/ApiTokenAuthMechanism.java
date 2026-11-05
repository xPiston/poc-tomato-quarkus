package fr.dllv.booking.auth.security;

import io.quarkus.security.credential.TokenCredential;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.security.identity.request.TokenAuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Lit l'en-tete {@code Authorization: Bearer <jeton>}.
 *
 * <p>C'est du branchement de framework, pas du metier : le fichier ne fait que
 * dire a Quarkus ou trouver le jeton et quoi repondre quand il manque. Il vit
 * dans un sous-package de la fonctionnalite "comptes" parce que c'est elle qui
 * emet les jetons, mais rien d'autre ne le regarde.
 */
@ApplicationScoped
public class ApiTokenAuthMechanism implements HttpAuthenticationMechanism {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviders) {
        String header = context.request().getHeader("Authorization");

        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Uni.createFrom().nullItem();
        }

        String token = header.substring(BEARER_PREFIX.length());

        return identityProviders.authenticate(new TokenAuthenticationRequest(new TokenCredential(token, "bearer")));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401, "WWW-Authenticate", "Bearer"));
    }

    @Override
    public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
        return Set.of(TokenAuthenticationRequest.class);
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(HttpCredentialTransport.Type.AUTHORIZATION, "bearer"));
    }
}
