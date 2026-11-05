package fr.dllv.booking;

import fr.dllv.booking.bookings.cli.BookingCli;
import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import picocli.CommandLine;

/**
 * Le point d'entree, pour les deux facons d'entrer.
 *
 * <p>Sans argument, l'application sert l'API et la tache planifiee tourne.
 * Avec un argument, elle execute la commande demandee et s'arrete. Un seul
 * jar, deux usages.
 */
@QuarkusMain
public class Main implements QuarkusApplication {

    private final CommandLine.IFactory factory;
    private final BookingCli cli;

    public Main(CommandLine.IFactory factory, BookingCli cli) {
        this.factory = factory;
        this.cli = cli;
    }

    public static void main(String[] args) {
        Quarkus.run(Main.class, args);
    }

    @Override
    public int run(String... args) {
        if (args.length == 0) {
            Quarkus.waitForExit();

            return 0;
        }

        return new CommandLine(cli, factory).execute(args);
    }
}
