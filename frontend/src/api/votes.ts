import type { VoteRequest, VoteStats } from '../types'
import { apiClient } from './client'

export const votesApi = {
  vote: (alertId: string, data: VoteRequest) =>
    apiClient.post(`/alerts/${alertId}/votes`, data).then((r) => r.data),

  removeVote: (alertId: string) =>
    apiClient.delete(`/alerts/${alertId}/votes`).then((r) => r.data),

  getStats: (alertId: string) =>
    apiClient.get<VoteStats>(`/alerts/${alertId}/votes/stats`).then((r) => r.data),

  getMyVote: (alertId: string) =>
    apiClient
      .get<{ hasVoted: boolean }>(`/alerts/${alertId}/votes/me`)
      .then((r) => r.data),
}
