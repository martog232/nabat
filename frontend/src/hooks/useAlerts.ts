import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { alertsApi } from '../api/alerts'
import { votesApi } from '../api/votes'
import { useAlertStore } from '../store/alertStore'
import type { CreateAlertRequest } from '../types'

export function useNearbyAlerts() {
  const { mapCenter, radiusKm, setAlerts } = useAlertStore()
  return useQuery({
    queryKey: ['alerts', 'nearby', mapCenter, radiusKm],
    queryFn: async () => {
      const alerts = await alertsApi.getNearby(mapCenter[0], mapCenter[1], radiusKm)
      setAlerts(alerts)
      return alerts
    },
    refetchInterval: 30_000,
  })
}

export function useCreateAlert() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateAlertRequest) => alertsApi.create(data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })
}

export function useVoteStats(alertId: string) {
  return useQuery({
    queryKey: ['votes', alertId, 'stats'],
    queryFn: () => votesApi.getStats(alertId),
    enabled: !!alertId,
  })
}

export function useMyVote(alertId: string) {
  return useQuery({
    queryKey: ['votes', alertId, 'me'],
    queryFn: () => votesApi.getMyVote(alertId),
    enabled: !!alertId,
  })
}

export function useVote(alertId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (voteType: string) => votesApi.vote(alertId, { voteType: voteType as never }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['votes', alertId] })
    },
  })
}

export function useRemoveVote(alertId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: () => votesApi.removeVote(alertId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['votes', alertId] })
    },
  })
}
