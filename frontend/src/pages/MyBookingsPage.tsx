import { useEffect, useMemo, useState } from 'react';
import { differenceInHours, format, parseISO } from 'date-fns';
import { fr } from 'date-fns/locale';
import { toast } from 'sonner';
import { AlertTriangle, CalendarClock, Check, Loader2, X } from 'lucide-react';
import { bookingApi, resourceApi } from '@/lib/api';
import type { Booking, Resource } from '@/types';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { StatusBadge } from '@/components/ui/badge';

export function MyBookingsPage() {
  const [bookings, setBookings] = useState<Booking[]>([]);
  const [resources, setResources] = useState<Record<string, Resource>>({});
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const [list, resourceList] = await Promise.all([bookingApi.mine(), resourceApi.list()]);
      setBookings(list);
      setResources(Object.fromEntries(resourceList.map((r) => [r.id, r])));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void load();
  }, []);

  const sorted = useMemo(
    () => [...bookings].sort((a, b) => a.starts_at.localeCompare(b.starts_at)),
    [bookings],
  );

  const willIncurPenalty = (b: Booking) => differenceInHours(parseISO(b.starts_at), new Date()) < 24;

  const confirm = async (b: Booking) => {
    setBusyId(b.id);
    try {
      await bookingApi.confirm(b.id);
      toast.success('Réservation confirmée');
      await load();
    } catch {
      toast.error('Confirmation impossible (créneau peut-être déjà pris)');
    } finally {
      setBusyId(null);
    }
  };

  const cancel = async (b: Booking) => {
    setBusyId(b.id);
    try {
      const result = await bookingApi.cancel(b.id);
      if (result.penalty_applied) {
        toast.warning('Annulée avec pénalité (moins de 24h avant le créneau)');
      } else {
        toast.success('Réservation annulée');
      }
      if (result.promoted_booking_id) {
        toast.info("Une personne en liste d'attente a été promue et notifiée");
      }
      await load();
    } catch {
      toast.error('Annulation impossible');
    } finally {
      setBusyId(null);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center gap-2 py-16 text-muted-foreground">
        <Loader2 className="h-4 w-4 animate-spin" /> Chargement…
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Mes réservations</h1>
        <p className="text-sm text-muted-foreground">Confirmez, annulez et suivez vos créneaux.</p>
      </div>

      {sorted.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-2 py-16 text-center text-muted-foreground">
            <CalendarClock className="h-8 w-8" />
            <p>Aucune réservation pour l'instant. Rendez-vous sur le planning !</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {sorted.map((b) => {
            const penalty = b.status === 'CONFIRMEE' && willIncurPenalty(b);
            const busy = busyId === b.id;
            return (
              <Card key={b.id}>
                <CardContent className="flex flex-wrap items-center justify-between gap-4 p-4">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="font-medium">
                        {resources[b.resource_id]?.name ?? 'Ressource'}
                      </span>
                      <StatusBadge status={b.status} waitlisted={b.waitlisted} />
                    </div>
                    <div className="text-sm text-muted-foreground">
                      {format(parseISO(b.starts_at), "EEEE dd MMM yyyy 'de' HH:mm", { locale: fr })} →{' '}
                      {format(parseISO(b.ends_at), 'HH:mm')}
                    </div>
                    {penalty && (
                      <div className="flex items-center gap-1 text-xs text-warning">
                        <AlertTriangle className="h-3.5 w-3.5" />
                        Annulation dans moins de 24h : pénalité applicable
                      </div>
                    )}
                  </div>

                  <div className="flex items-center gap-2">
                    {b.status === 'EN_ATTENTE' && !b.waitlisted && (
                      <Button size="sm" onClick={() => confirm(b)} disabled={busy}>
                        <Check className="h-4 w-4" /> Confirmer
                      </Button>
                    )}
                    {b.status === 'CONFIRMEE' && (
                      <Button
                        size="sm"
                        variant={penalty ? 'destructive' : 'outline'}
                        onClick={() => cancel(b)}
                        disabled={busy}
                      >
                        <X className="h-4 w-4" /> Annuler
                      </Button>
                    )}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
