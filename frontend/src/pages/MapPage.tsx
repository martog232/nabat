import { useState } from 'react'
import { Layout } from '../components/layout/Layout'
import { AlertMap } from '../components/map/AlertMap'
import { AlertSidebar } from '../components/alerts/AlertSidebar'
import { AlertDetail } from '../components/alerts/AlertDetail'
import { CreateAlertModal } from '../components/alerts/CreateAlertModal'
import { useAlertWebSocket } from '../hooks/useAlertWebSocket'
import { useNearbyAlerts } from '../hooks/useAlerts'
import { useAuthStore } from '../store/authStore'
import { Button } from '../components/common/Button'

export function MapPage() {
  const user = useAuthStore((s) => s.user)
  const [showCreateModal, setShowCreateModal] = useState(false)

  // Live alerts
  useNearbyAlerts()

  // Real-time WebSocket
  useAlertWebSocket(user?.id ?? null)

  return (
    <Layout>
      {/* Full-screen map */}
      <div className="absolute inset-0">
        <AlertMap />
      </div>

      {/* Left sidebar */}
      <AlertSidebar />

      {/* Alert detail panel */}
      <AlertDetail />

      {/* FAB: Report incident */}
      {user && (
        <div className="absolute bottom-6 right-4 z-[1000]">
          <Button
            onClick={() => setShowCreateModal(true)}
            className="px-5 py-3 text-sm shadow-2xl shadow-brand-600/40 rounded-2xl"
            size="lg"
          >
            <span className="text-lg">🚨</span>
            Report Incident
          </Button>
        </div>
      )}

      {/* Live indicator */}
      <div className="absolute bottom-6 left-1/2 -translate-x-1/2 z-[1000]">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-surface-card/80 backdrop-blur border border-surface-border text-xs text-slate-400">
          <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse-fast" />
          Live
        </div>
      </div>

      {/* Create alert modal */}
      {showCreateModal && (
        <CreateAlertModal onClose={() => setShowCreateModal(false)} />
      )}
    </Layout>
  )
}
