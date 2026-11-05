package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;

/** Duree hors des bornes configurees. */
public class InvalidBookingDurationException extends BusinessException {

    private InvalidBookingDurationException(String message) {
        super(UNPROCESSABLE, message);
    }

    public static InvalidBookingDurationException tooShort(long minutes, int minimum) {
        return new InvalidBookingDurationException(
                "Une reservation de %d minutes est trop courte (minimum %d).".formatted(minutes, minimum));
    }

    public static InvalidBookingDurationException tooLong(long minutes, int maximum) {
        return new InvalidBookingDurationException(
                "Une reservation de %d minutes est trop longue (maximum %d).".formatted(minutes, maximum));
    }
}
