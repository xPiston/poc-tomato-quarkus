import axios from 'axios';
import type { Booking, CancellationResult, Resource, User } from '@/types';

const TOKEN_KEY = 'booking.token';

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8000/api',
  headers: { Accept: 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Enveloppe { data: ... } des API Resources Laravel. */
interface Wrapped<T> {
  data: T;
}

export const authApi = {
  async login(email: string, password: string): Promise<{ token: string; user: User }> {
    const { data } = await api.post<{ token: string; user: User }>('/login', { email, password });
    return data;
  },
  async me(): Promise<User> {
    const { data } = await api.get<{ user: User }>('/me');
    return data.user;
  },
  async logout(): Promise<void> {
    await api.post('/logout');
  },
};

export const resourceApi = {
  async list(): Promise<Resource[]> {
    const { data } = await api.get<Wrapped<Resource[]>>('/resources');
    return data.data;
  },
  async schedule(resourceId: string, from: string, to: string): Promise<Booking[]> {
    const { data } = await api.get<Wrapped<Booking[]>>(`/resources/${resourceId}/schedule`, {
      params: { from, to },
    });
    return data.data;
  },
};

export const bookingApi = {
  async mine(): Promise<Booking[]> {
    const { data } = await api.get<Wrapped<Booking[]>>('/bookings');
    return data.data;
  },
  async create(payload: {
    resource_id: string;
    starts_at: string;
    ends_at: string;
    join_waitlist?: boolean;
  }): Promise<Booking> {
    const { data } = await api.post<Wrapped<Booking>>('/bookings', payload);
    return data.data;
  },
  async confirm(id: string): Promise<Booking> {
    const { data } = await api.post<Wrapped<Booking>>(`/bookings/${id}/confirm`);
    return data.data;
  },
  async cancel(id: string): Promise<CancellationResult> {
    const { data } = await api.post<CancellationResult>(`/bookings/${id}/cancel`);
    return data;
  },
};

export default api;
