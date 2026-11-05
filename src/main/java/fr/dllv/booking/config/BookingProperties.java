package fr.dllv.booking.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Les reglages metier, tires de {@code application.properties}.
 *
 * C'est une interface sans implementation, ce qui a l'air de contredire le "No
 * unnecessary interfaces" de tomato. Elle y echappe pour la raison exacte que
 * tomato donne : c'est une fonctionnalite du framework, pas une abstraction
 * ecrite a la main. SmallRye genere l'implementation, valide les types au
 * demarrage et fait echouer l'application si une cle manque. L'alternative
 * maison -- une classe de Value Objects assemblee dans une fabrique -- serait
 * du code a maintenir pour un service que le framework rend deja.
 */
@ConfigMapping(prefix = "booking")
public interface BookingProperties {

    /** Duree minimale d'une reservation, en minutes. */
    @WithDefault("15")
    int minDurationMinutes();

    /** Duree maximale d'une reservation, en minutes. */
    @WithDefault("480")
    int maxDurationMinutes();

    /** Horizon de reservation : on ne peut pas reserver plus loin que ca. */
    @WithDefault("30")
    int maxDaysAhead();

    /** Delai au-dela duquel une reservation restee en attente expire. */
    @WithDefault("15")
    int pendingExpirationMinutes();

    /** En-deca de ce delai avant le debut du creneau, annuler coute une penalite. */
    @WithDefault("24")
    int penaltyThresholdHours();

    /** Expediteur des notifications. */
    @WithDefault("no-reply@booking.test")
    String mailFrom();
}
