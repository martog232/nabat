import type { Alert } from '../../types'
import { ALERT_TYPE_ICONS, ALERT_TYPE_LABELS, SEVERITY_COLORS } from '../../types'
import { useAlertStore } from '../../store/alertStore'

interface AlertCardProps {
  alert: Alert
}

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'Just now'
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.floor(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  return `${Math.floor(hrs / 24)}d ago`
}

export function AlertCard({ alert }: AlertCardProps) {
  const { selectedAlert, selectAlert } = useAlertStore()
  const isSelected = selectedAlert?.id === alert.id

  return (
    <button
      onClick={() => selectAlert(isSelected ? null : alert)}
      className={`
        w-full text-left p-3 rounded-xl border transition-all duration-150 cursor-pointer
        ${isSelected
          ? 'bg-surface-elevated border-brand-500/50 shadow-lg shadow-brand-600/10'
          : 'bg-surface-card border-surface-border hover:border-surface-elevated hover:bg-surface-elevated/50'
        }
      `}
    >
      <div className="flex items-start gap-3">
        <div className="text-2xl flex-shrink-0 mt-0.5">{ALERT_TYPE_ICONS[alert.type]}</div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2 mb-0.5">
            <span className="font-semibold text-sm text-white truncate">{alert.title}</span>
            <span className="text-xs text-slate-500 whitespace-nowrap">{timeAgo(alert.createdAt)}</span>
          </div>
          <p className="text-xs text-slate-400 line-clamp-2 mb-1.5">{alert.description}</p>
          <div className="flex items-center gap-2">
            <span className="text-xs text-slate-500">{ALERT_TYPE_LABELS[alert.type]}</span>
            <span
              className={`inline-flex items-center px-1.5 py-0.5 rounded text-xs font-semibold ${SEVERITY_COLORS[alert.severity]}`}
            >
              {alert.severity}
            </span>
          </div>
        </div>
      </div>
    </button>
  )
}
