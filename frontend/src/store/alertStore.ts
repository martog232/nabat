import { create } from 'zustand'
import type { Alert } from '../types'

interface AlertStore {
  alerts: Alert[]
  selectedAlert: Alert | null
  mapCenter: [number, number]
  mapZoom: number
  radiusKm: number

  setAlerts: (alerts: Alert[]) => void
  addAlert: (alert: Alert) => void
  selectAlert: (alert: Alert | null) => void
  setMapCenter: (center: [number, number]) => void
  setMapZoom: (zoom: number) => void
  setRadiusKm: (r: number) => void
}

export const useAlertStore = create<AlertStore>((set) => ({
  alerts: [],
  selectedAlert: null,
  mapCenter: [42.6977, 23.3219], // Sofia, Bulgaria default
  mapZoom: 13,
  radiusKm: 5,

  setAlerts: (alerts) => set({ alerts }),
  addAlert: (alert) =>
    set((s) => {
      const exists = s.alerts.some((a) => a.id === alert.id)
      if (exists) return s
      return { alerts: [alert, ...s.alerts] }
    }),
  selectAlert: (alert) => set({ selectedAlert: alert }),
  setMapCenter: (center) => set({ mapCenter: center }),
  setMapZoom: (zoom) => set({ mapZoom: zoom }),
  setRadiusKm: (r) => set({ radiusKm: r }),
}))
