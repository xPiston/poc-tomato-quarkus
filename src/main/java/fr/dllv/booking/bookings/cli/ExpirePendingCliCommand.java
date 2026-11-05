package fr.dllv.booking.bookings.cli;

import fr.dllv.booking.bookings.BookingService;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;

/**
 * Declenche a la main ce que {@code ExpirePendingBookingsJob} fait toutes les
 * cinq minutes. Utile pour ne pas attendre, en demonstration comme en
 * exploitation.
 */
@ApplicationScoped
@Unremovable
@Command(name = "booking:expire-pending", description = "Expire les demandes restees en attente.")
public class ExpirePendingCliCommand implements Callable<Integer> {

    private final BookingService bookings;

    public ExpirePendingCliCommand(BookingService bookings) {
        this.bookings = bookings;
    }

    @Override
    public Integer call() {
        System.out.printf("%d reservation(s) expiree(s).%n", bookings.expirePending());

        return 0;
    }
}
