package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;

/** Au-dela de l'horizon de reservation configure. */
public class BookingTooFarInAdvanceException extends BusinessException {

    public BookingTooFarInAdvanceException(int maxDaysAhead) {
        super(UNPROCESSABLE, "On ne peut pas reserver plus de %d jours a l'avance.".formatted(maxDaysAhead));
    }
}
