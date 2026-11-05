package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;

/** On ne reserve pas un creneau deja commence. */
public class BookingInThePastException extends BusinessException {

    public BookingInThePastException(TimeSlot slot) {
        super(UNPROCESSABLE, "Le creneau %s a deja commence.".formatted(slot));
    }
}
