package fr.dllv.booking.bookings;

import java.util.UUID;

/**
 * Ce qu'il faut pour demander une reservation.
 *
 * <p>Les types sont deja ceux du metier -- des {@link UUID}, un
 * {@link TimeSlot} valide. Convertir et valider le format est le travail de
 * l'entree (le controleur, la commande CLI) ; le service recoit des valeurs
 * dont il n'a plus a douter.
 */
public record CreateBookingCommand(UUID resourceId, UUID attendeeId, TimeSlot slot, boolean joinWaitlistIfFull) {}
