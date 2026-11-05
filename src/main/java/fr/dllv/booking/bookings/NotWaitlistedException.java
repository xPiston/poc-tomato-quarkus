package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;
import java.util.UUID;

/** On ne promeut que ce qui etait en liste d'attente. */
public class NotWaitlistedException extends BusinessException {

    public NotWaitlistedException(UUID bookingId) {
        super(CONFLICT, "La reservation %s n'est pas en liste d'attente.".formatted(bookingId));
    }
}
