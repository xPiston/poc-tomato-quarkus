package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;
import java.util.UUID;

/** Aucune reservation sous cet identifiant. */
public class BookingNotFoundException extends BusinessException {

    public BookingNotFoundException(UUID bookingId) {
        super(NOT_FOUND, "Reservation %s introuvable.".formatted(bookingId));
    }
}
