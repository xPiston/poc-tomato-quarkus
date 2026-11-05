export type BookingStatus =
  | 'EN_ATTENTE'
  | 'CONFIRMEE'
  | 'TERMINEE'
  | 'ANNULEE'
  | 'ANNULEE_AVEC_PENALITE'
  | 'EXPIREE';

export interface Resource {
  id: string;
  name: string;
  capacity: number;
}

export interface Booking {
  id: string;
  resource_id: string;
  attendee_id: string;
  status: BookingStatus;
  waitlisted: boolean;
  starts_at: string;
  ends_at: string;
  requested_at: string;
}

export interface User {
  id: number;
  name: string;
  email: string;
  attendee_id: string;
}

export interface CancellationResult {
  id: string;
  status: BookingStatus;
  penalty_applied: boolean;
  promoted_booking_id: string | null;
}
