package fr.dllv.booking.bookings.jobs;

import fr.dllv.booking.bookings.BookingService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Toutes les cinq minutes, libere les demandes que personne n'a confirmees.
 *
 * <p>Deux lignes utiles, et c'est le but. Tomato cite explicitement "business
 * logic in schedulers" parmi ses anti-patterns : une tache planifiee prepare
 * son contexte d'execution et delegue, point. Le quoi, le seuil et le compte
 * vivent dans {@code BookingService} et {@code Booking}, et la commande CLI
 * voisine declenche exactement le meme code.
 *
 * <p>C'est aussi la principale simplification d'exploitation par rapport aux
 * POC hexagonaux : eux font tourner un second conteneur qui boucle sur
 * {@code sleep 300} et relance le binaire, avec le demarrage complet de
 * l'application a chaque tour et une variable d'environnement pour l'empecher
 * de reseeder la base. Ici c'est une annotation du framework dans le processus
 * qui sert deja l'API -- ce que tomato appelle ne pas reimplementer ce que le
 * framework fait deja.
 */
@ApplicationScoped
public class ExpirePendingBookingsJob {

    private final BookingService bookings;

    public ExpirePendingBookingsJob(BookingService bookings) {
        this.bookings = bookings;
    }

    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void expirePendingBookings() {
        bookings.expirePending();
    }
}
