package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;

/** La capacite de la ressource est deja atteinte sur ce creneau. */
public class OverlappingBookingException extends BusinessException {

    public OverlappingBookingException(TimeSlot slot) {
        super(CONFLICT, "Le creneau %s n'est plus disponible sur cette ressource.".formatted(slot));
    }
}
