package fr.dllv.booking.bookings;

/**
 * Ce que repond une annulation : le nouveau statut, si une penalite s'applique,
 * et l'identifiant de la reservation promue depuis la liste d'attente s'il y en
 * a eu une.
 */
public record CancellationDto(String id, String status, boolean penaltyApplied, String promotedBookingId) {}
