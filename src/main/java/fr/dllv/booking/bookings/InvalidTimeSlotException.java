package fr.dllv.booking.bookings;

import fr.dllv.booking.common.BusinessException;
import java.time.OffsetDateTime;

/** La fin du creneau n'est pas posterieure a son debut. */
public class InvalidTimeSlotException extends BusinessException {

    public InvalidTimeSlotException(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        super(
                UNPROCESSABLE,
                "La fin du creneau (%s) doit etre posterieure a son debut (%s).".formatted(endsAt, startsAt));
    }
}
