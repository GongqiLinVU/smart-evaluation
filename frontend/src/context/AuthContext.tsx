import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import type { UserResponse } from '../types';
import { refreshToken } from '../api/client';

interface AuthContextType {
  user: UserResponse | null;
  token: string | null;
  setAuth: (token: string, user: UserResponse) => void;
  logout: () => void;
  refreshUser: () => Promise<void>;
  isAdmin: boolean;
  isTutor: boolean;
  isStudent: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(() => {
    const stored = localStorage.getItem('user');
    return stored ? JSON.parse(stored) : null;
  });
  const [token, setToken] = useState<string | null>(() =>
    localStorage.getItem('token'),
  );

  const setAuth = useCallback((newToken: string, newUser: UserResponse) => {
    localStorage.setItem('token', newToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    setToken(newToken);
    setUser(newUser);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  }, []);

  const refreshUser = useCallback(async () => {
    if (!token) return;
    try {
      const auth = await refreshToken();
      setToken(auth.token);
      setUser(auth.user);
      localStorage.setItem('token', auth.token);
      localStorage.setItem('user', JSON.stringify(auth.user));
    } catch {
      logout();
    }
  }, [token, logout]);

  useEffect(() => {
    if (token) {
      refreshUser();
    }
  }, []);

  const role = user?.role?.toUpperCase() ?? '';

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        setAuth,
        logout,
        refreshUser,
        isAdmin: role === 'ADMIN',
        isTutor: role === 'TUTOR',
        isStudent: role === 'STUDENT',
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
