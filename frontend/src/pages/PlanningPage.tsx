import { useCallback, useEffect, useMemo, useState } from 'react';
import { addDays, addWeeks, format, parseISO, startOfWeek } from 'date-fns';
import { fr } from 'date-fns/locale';
import { toast } from 'sonner';
import { ChevronLeft, ChevronRight, Loader2 } from 'lucide-react';
import axios from 'axios';
import { bookingApi, resourceApi } from '@/lib/api';
import type { Booking, Resource } from '@/types';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

const HOURS = Array.from({ length: 11 }, (_, i) => i + 8); // 08:00 -> 18:00

/** Format naif local "yyyy-MM-ddTHH:mm:ss" attendu par l'API. */
const iso = (d: Date) => format(d, "yyyy-MM-dd'T'HH:mm:ss");

interface SlotSelection {
  start: Date;
  end: Date;
}

export function PlanningPage() {
  const [resources, setResources] = useState<Resource[]>([]);
  const [resourceId, setResourceId] = useState<string>('');
  const [weekOffset, setWeekOffset] = useState(0);
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [loading, setLoading] = useState(false);
  const [selection, setSelection] = useState<SlotSelection | null>(null);
  const [offerWaitlist, setOfferWaitlist] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const weekStart = useMemo(
    () => startOfWeek(addWeeks(new Date(), weekOffset), { weekStartsOn: 1 }),
    [weekOffset],
  );
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart]);
  const selectedResource = resources.find((r) => r.id === resourceId);

  useEffect(() => {
    resourceApi.list().then((list) => {
      setResources(list);
      if (list.length > 0) setResourceId((current) => current || list[0].id);
    });
  }, []);

  const loadSchedule = useCallback(async () => {
    if (!resourceId) return;
    setLoading(true);
    try {
      const from = iso(weekStart);
      const to = iso(addDays(weekStart, 7));
      setBookings(await resourceApi.schedule(resourceId, from, to));
    } catch {
      toast.error('Impossible de charger le planning');
    } finally {
      setLoading(false);
    }
  }, [resourceId, weekStart]);

  useEffect(() => {
    void loadSchedule();
  }, [loadSchedule]);

  const bookingAt = (day: Date, hour: number): Booking | undefined => {
    const cellStart = new Date(day);
    cellStart.setHours(hour, 0, 0, 0);
    const cellEnd = new Date(cellStart);
    cellEnd.setHours(hour + 1);
    return bookings.find((b) => {
      if (b.status !== 'CONFIRMEE') return false;
      const s = parseISO(b.starts_at);
      const e = parseISO(b.ends_at);
      return s < cellEnd && cellStart < e;
    });
  };

  const openSlot = (day: Date, hour: number) => {
    const start = new Date(day);
    start.setHours(hour, 0, 0, 0);
    if (start.getTime() < Date.now()) {
      toast.warning('Impossible de réserver dans le passé');
      return;
    }
    const end = new Date(start);
    end.setHours(hour + 1);
    setSelection({ start, end });
    setOfferWaitlist(false);
  };

  const submitBooking = async (joinWaitlist: boolean) => {
    if (!selection || !resourceId) return;
    setSubmitting(true);
    try {
      const booking = await bookingApi.create({
        resource_id: resourceId,
        starts_at: iso(selection.start),
        ends_at: iso(selection.end),
        join_waitlist: joinWaitlist,
      });
      toast.success(
        booking.waitlisted
          ? "Ajouté à la liste d'attente — vous serez notifié si une place se libère"
          : 'Réservation créée (en attente de confirmation)',
      );
      setSelection(null);
      await loadSchedule();
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 409) {
        // Ressource complète : on propose la liste d'attente.
        setOfferWaitlist(true);
      } else if (axios.isAxiosError(error) && error.response?.status === 422) {
        toast.error(error.response.data?.message ?? 'Créneau invalide');
      } else {
        toast.error('Une erreur est survenue');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Planning</h1>
          <p className="text-sm text-muted-foreground">
            Cliquez sur un créneau libre pour réserver.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={resourceId}
            onChange={(e) => setResourceId(e.target.value)}
            className="h-10 rounded-md border border-input bg-background px-3 text-sm"
          >
            {resources.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name} {r.capacity > 1 ? `(capacité ${r.capacity})` : ''}
              </option>
            ))}
          </select>
          <div className="flex items-center gap-1">
            <Button variant="outline" size="icon" onClick={() => setWeekOffset((w) => w - 1)}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <span className="min-w-[9rem] text-center text-sm font-medium">
              {format(weekStart, 'dd MMM', { locale: fr })} –{' '}
              {format(addDays(weekStart, 6), 'dd MMM', { locale: fr })}
            </span>
            <Button variant="outline" size="icon" onClick={() => setWeekOffset((w) => w + 1)}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      <Card className="overflow-x-auto">
        <div className="min-w-[720px]">
          <div className="grid grid-cols-8 border-b bg-muted/40 text-xs font-medium">
            <div className="p-2 text-muted-foreground">Heure</div>
            {days.map((day) => (
              <div key={day.toISOString()} className="p-2 text-center">
                <div className="capitalize">{format(day, 'EEE', { locale: fr })}</div>
                <div className="text-muted-foreground">{format(day, 'dd/MM')}</div>
              </div>
            ))}
          </div>

          {loading ? (
            <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" /> Chargement…
            </div>
          ) : (
            HOURS.map((hour) => (
              <div key={hour} className="grid grid-cols-8 border-b last:border-0">
                <div className="p-2 text-xs text-muted-foreground">{`${hour}:00`}</div>
                {days.map((day) => {
                  const booked = bookingAt(day, hour);
                  return (
                    <div key={day.toISOString() + hour} className="border-l p-1">
                      {booked ? (
                        <div className="flex h-9 items-center justify-center rounded-md bg-primary/15 text-xs font-medium text-primary">
                          Occupé
                        </div>
                      ) : (
                        <button
                          onClick={() => openSlot(day, hour)}
                          className="h-9 w-full rounded-md text-xs text-muted-foreground transition-colors hover:bg-secondary"
                        >
                          libre
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </div>
      </Card>

      <Dialog open={selection !== null} onOpenChange={(open) => !open && setSelection(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{offerWaitlist ? 'Ressource complète' : 'Nouvelle réservation'}</DialogTitle>
            <DialogDescription>
              {selectedResource && selection
                ? `${selectedResource.name} · ${format(selection.start, "EEEE dd MMM 'de' HH:mm", {
                    locale: fr,
                  })} → ${format(selection.end, 'HH:mm')}`
                : ''}
            </DialogDescription>
          </DialogHeader>

          {offerWaitlist ? (
            <>
              <p className="text-sm text-muted-foreground">
                Ce créneau est déjà réservé. Souhaitez-vous rejoindre la liste d'attente ? Vous serez
                automatiquement promu et notifié si une place se libère.
              </p>
              <DialogFooter>
                <Button variant="outline" onClick={() => setSelection(null)}>
                  Annuler
                </Button>
                <Button onClick={() => submitBooking(true)} disabled={submitting}>
                  Rejoindre la liste d'attente
                </Button>
              </DialogFooter>
            </>
          ) : (
            <>
              <p className="text-sm text-muted-foreground">
                La réservation sera créée en statut <strong>En attente</strong>, puis à confirmer
                depuis « Mes réservations ».
              </p>
              <DialogFooter>
                <Button variant="outline" onClick={() => setSelection(null)}>
                  Annuler
                </Button>
                <Button onClick={() => submitBooking(false)} disabled={submitting}>
                  {submitting ? 'Réservation…' : 'Réserver'}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
