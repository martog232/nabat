import type { Alert, CreateAlertRequest } from '../types'
import { apiClient } from './client'

export const alertsApi = {
  getNearby: (lat: number, lng: number, radiusKm = 5) =>
    apiClient
      .get<Alert[]>('/alerts/nearby', { params: { latitude: lat, longitude: lng, radiusKm } })
      .then((r) => r.data),

  create: (data: CreateAlertRequest) =>
    apiClient.post<Alert>('/alerts', data).then((r) => r.data),
}
