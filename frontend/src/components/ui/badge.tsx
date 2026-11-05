import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';
import type { BookingStatus } from '@/types';

const badgeVariants = cva(
  'inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
  {
    variants: {
      variant: {
        default: 'border-transparent bg-primary text-primary-foreground shadow hover:bg-primary/80',
        secondary: 'border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80',
        destructive: 'border-transparent bg-destructive text-destructive-foreground shadow hover:bg-destructive/80',
        outline: 'text-foreground',
      },
    },
    defaultVariants: {
      variant: 'default',
    },
  },
);

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />;
}

// -----------------------------------------------------------------------------
// Badge applicatif : mappe le statut metier d'une reservation sur une couleur.
// -----------------------------------------------------------------------------
const STATUS_STYLES: Record<BookingStatus, string> = {
  EN_ATTENTE: 'bg-warning/15 text-warning border-warning/30',
  CONFIRMEE: 'bg-success/15 text-success border-success/30',
  TERMINEE: 'bg-muted text-muted-foreground border-border',
  ANNULEE: 'bg-muted text-muted-foreground border-border',
  ANNULEE_AVEC_PENALITE: 'bg-destructive/15 text-destructive border-destructive/30',
  EXPIREE: 'bg-muted text-muted-foreground border-border line-through',
};

const STATUS_LABELS: Record<BookingStatus, string> = {
  EN_ATTENTE: 'En attente',
  CONFIRMEE: 'Confirmée',
  TERMINEE: 'Terminée',
  ANNULEE: 'Annulée',
  ANNULEE_AVEC_PENALITE: 'Annulée (pénalité)',
  EXPIREE: 'Expirée',
};

export function StatusBadge({ status, waitlisted }: { status: BookingStatus; waitlisted?: boolean }) {
  const label = waitlisted && status === 'EN_ATTENTE' ? "Liste d'attente" : STATUS_LABELS[status];
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
        STATUS_STYLES[status],
      )}
    >
      {label}
    </span>
  );
}

export { Badge, badgeVariants };
