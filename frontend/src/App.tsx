import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'sonner';
import { AuthProvider } from '@/context/AuthContext';
import { ThemeProvider, useTheme } from '@/context/ThemeContext';
import { ProtectedRoute } from '@/components/ProtectedRoute';
import { Layout } from '@/components/Layout';
import { LoginPage } from '@/pages/LoginPage';
import { PlanningPage } from '@/pages/PlanningPage';
import { MyBookingsPage } from '@/pages/MyBookingsPage';

function AppToaster() {
  const { theme } = useTheme();
  return <Toaster theme={theme} richColors position="top-right" />;
}

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route
              element={
                <ProtectedRoute>
                  <Layout />
                </ProtectedRoute>
              }
            >
              <Route path="/" element={<PlanningPage />} />
              <Route path="/mes-reservations" element={<MyBookingsPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
        <AppToaster />
      </AuthProvider>
    </ThemeProvider>
  );
}
