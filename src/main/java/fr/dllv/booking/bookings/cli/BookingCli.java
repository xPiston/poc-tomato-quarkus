package fr.dllv.booking.bookings.cli;

import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine.Command;

/** Racine des commandes en ligne. */
@ApplicationScoped
@Command(
        name = "booking-app",
        mixinStandardHelpOptions = true,
        subcommands = {CreateBookingCliCommand.class, ExpirePendingCliCommand.class})
public class BookingCli implements Runnable {

    @Override
    public void run() {
        System.out.println("Utilisez --help pour la liste des commandes.");
    }
}
