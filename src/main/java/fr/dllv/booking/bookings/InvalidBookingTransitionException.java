package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;

/** Transition refusee par la machine a etats. */
public class InvalidBookingTransitionException extends BusinessException {

    public InvalidBookingTransitionException(BookingStatus from, BookingStatus to) {
        super(CONFLICT, "Une reservation %s ne peut pas passer a %s.".formatted(from.label(), to.label()));
    }
}
