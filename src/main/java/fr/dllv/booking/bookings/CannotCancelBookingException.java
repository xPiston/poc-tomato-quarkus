package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;
import java.util.UUID;

/** Le creneau est deja passe ou la reservation deja honoree. */
public class CannotCancelBookingException extends BusinessException {

    public CannotCancelBookingException(UUID bookingId) {
        super(CONFLICT, "La reservation %s ne peut plus etre annulee : son creneau est passe.".formatted(bookingId));
    }
}
