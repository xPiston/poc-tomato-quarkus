package fr.dllv.booking.bookings.cli;

import fr.dllv.booking.bookings.Booking;
import fr.dllv.booking.bookings.BookingService;
import fr.dllv.booking.bookings.CreateBookingCommand;
import fr.dllv.booking.bookings.TimeSlot;
import fr.dllv.booking.common.BusinessException;
import fr.dllv.booking.common.IsoDateTime;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * La meme reservation que {@code POST /api/bookings}, depuis un terminal.
 *
 * <p>C'est la demonstration que le service ne sait pas qui l'appelle : aucune
 * ligne de {@code BookingService} ne change selon qu'on arrive par HTTP ou par
 * la. Tomato le formule comme "the Application Core should expose APIs
 * callable from a plain main()".
 *
 * <p>{@code @Unremovable} parce que Picocli ne reference cette classe que dans
 * la metadata de {@link BookingCli}, un tableau de classes qu'Arc n'analyse
 * pas : sans l'annotation, l'elimination des beans inutilises la supprime.
 */
@ApplicationScoped
@Unremovable
@Command(name = "booking:create", description = "Cree une reservation.")
public class CreateBookingCliCommand implements Callable<Integer> {

    private final BookingService bookings;

    @Parameters(index = "0", description = "UUID de la ressource")
    String resource;

    @Parameters(index = "1", description = "UUID du participant")
    String attendee;

    @Parameters(index = "2", description = "Debut ISO 8601 (ex: 2026-07-10T10:00:00Z)")
    String start;

    @Parameters(index = "3", description = "Fin ISO 8601")
    String end;

    @Option(names = "--waitlist", description = "S'inscrire en liste d'attente si complet")
    boolean waitlist;

    public CreateBookingCliCommand(BookingService bookings) {
        this.bookings = bookings;
    }

    @Override
    public Integer call() {
        try {
            Booking booking = bookings.create(new CreateBookingCommand(
                    UUID.fromString(resource),
                    UUID.fromString(attendee),
                    TimeSlot.of(IsoDateTime.parse(start), IsoDateTime.parse(end)),
                    waitlist));

            System.out.printf(
                    "Reservation %s creee (%s%s).%n",
                    booking.id(), booking.status().label(), booking.isWaitlisted() ? ", liste d'attente" : "");

            return 0;
        } catch (BusinessException e) {
            System.err.println(e.getMessage());

            return 1;
        }
    }
}
