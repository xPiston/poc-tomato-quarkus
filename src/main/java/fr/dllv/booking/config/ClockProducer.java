package fr.dllv.booking.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.time.Clock;

/**
 * Rend l'horloge injectable.
 *
 * Les POC hexagonaux declarent pour cela un port {@code Clock} maison, avec
 * {@code SystemClock} et {@code FixedClock} derriere. Tomato refuse
 * l'interface, mais la raison d'etre du port -- ne jamais appeler
 * {@code OffsetDateTime.now()} au milieu d'une regle, pour que le temps soit
 * un parametre de test -- reste entierement valable.
 *
 * Il se trouve que le JDK fournit deja l'abstraction : {@link Clock} est une
 * classe du {@code java.time}, avec {@link Clock#systemUTC()} pour la
 * production et {@link Clock#fixed} pour les tests. On injecte celle-la. Zero
 * interface ecrite, et l'horloge reste deterministe sous test.
 */
@ApplicationScoped
public class ClockProducer {

    @Produces
    @ApplicationScoped
    public Clock clock() {
        return Clock.systemUTC();
    }
}
