import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { CalendarDays, LogOut, Moon, Sun, Ticket } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useTheme } from '@/context/ThemeContext';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';

export function Layout() {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    cn(
      'inline-flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium transition-colors',
      isActive ? 'bg-secondary text-secondary-foreground' : 'text-muted-foreground hover:text-foreground',
    );

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <div className="flex items-center gap-6">
            <span className="flex items-center gap-2 font-semibold">
              <Ticket className="h-5 w-5 text-primary" />
              Booking
            </span>
            <nav className="flex items-center gap-1">
              <NavLink to="/" end className={linkClass}>
                <CalendarDays className="h-4 w-4" /> Planning
              </NavLink>
              <NavLink to="/mes-reservations" className={linkClass}>
                <Ticket className="h-4 w-4" /> Mes réservations
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="icon" onClick={toggle} aria-label="Basculer le thème">
              {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
            </Button>
            {user && <span className="hidden text-sm text-muted-foreground sm:inline">{user.name}</span>}
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="h-4 w-4" /> Déconnexion
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
