package fr.dllv.booking.bookings;

import java.util.List;

/**
 * La machine a etats d'une reservation.
 *
 * <pre>
 *   EN_ATTENTE --> CONFIRMEE --> TERMINEE
 *   EN_ATTENTE --> CONFIRMEE --> ANNULEE | ANNULEE_AVEC_PENALITE
 *   EN_ATTENTE --> EXPIREE
 * </pre>
 *
 * Les transitions permises sont declarees une seule fois, dans
 * {@link #allowedTransitions()}. Tout le reste est refuse par
 * {@link Booking#transitionTo}, ce qui rend un statut incoherent impossible a
 * atteindre quel que soit le chemin emprunte -- API, CLI ou tache planifiee.
 *
 * <p>Le libelle francais ({@link #label()}) est ce que le frontend affiche ; il
 * ne sert qu'a la serialisation JSON. La base, elle, stocke le nom Java.
 */
public enum BookingStatus {
    PENDING("EN_ATTENTE"),
    CONFIRMED("CONFIRMEE"),
    COMPLETED("TERMINEE"),
    CANCELLED("ANNULEE"),
    CANCELLED_WITH_PENALTY("ANNULEE_AVEC_PENALITE"),
    EXPIRED("EXPIREE");

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public List<BookingStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> List.of(CONFIRMED, EXPIRED);
            case CONFIRMED -> List.of(COMPLETED, CANCELLED, CANCELLED_WITH_PENALTY);
            case COMPLETED, CANCELLED, CANCELLED_WITH_PENALTY, EXPIRED -> List.of();
        };
    }

    public boolean canTransitionTo(BookingStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Etat terminal : aucune transition n'en sort. */
    public boolean isFinal() {
        return allowedTransitions().isEmpty();
    }

    public boolean isCancelled() {
        return this == CANCELLED || this == CANCELLED_WITH_PENALTY;
    }
}
