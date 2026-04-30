import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { User } from '../types'
import { authApi } from '../api/auth'

interface AuthState {
  user: User | null
  accessToken: string | null
  refreshToken: string | null
  isLoading: boolean
  error: string | null

  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string, username: string) => Promise<void>
  logout: () => void
  clearError: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      accessToken: null,
      refreshToken: null,
      isLoading: false,
      error: null,

      login: async (email, password) => {
        set({ isLoading: true, error: null })
        try {
          const data = await authApi.login({ email, password })
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          set({ user: data.user, accessToken: data.accessToken, refreshToken: data.refreshToken, isLoading: false })
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Login failed'
          set({ isLoading: false, error: msg })
          throw err
        }
      },

      register: async (email, password, username) => {
        set({ isLoading: true, error: null })
        try {
          const data = await authApi.register({ email, password, username })
          localStorage.setItem('accessToken', data.accessToken)
          localStorage.setItem('refreshToken', data.refreshToken)
          set({ user: data.user, accessToken: data.accessToken, refreshToken: data.refreshToken, isLoading: false })
        } catch (err: unknown) {
          const msg = err instanceof Error ? err.message : 'Registration failed'
          set({ isLoading: false, error: msg })
          throw err
        }
      },

      logout: () => {
        localStorage.removeItem('accessToken')
        localStorage.removeItem('refreshToken')
        set({ user: null, accessToken: null, refreshToken: null })
      },

      clearError: () => set({ error: null }),
    }),
    { name: 'nabat-auth', partialize: (s) => ({ user: s.user, accessToken: s.accessToken, refreshToken: s.refreshToken }) },
  ),
)
