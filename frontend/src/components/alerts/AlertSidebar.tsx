import { useAlertStore } from '../../store/alertStore'
import { useNearbyAlerts } from '../../hooks/useAlerts'
import { AlertCard } from './AlertCard'
import type { AlertSeverity, AlertType } from '../../types'
import { ALERT_TYPE_LABELS, ALERT_TYPE_ICONS } from '../../types'
import { useState } from 'react'

type SeverityFilter = AlertSeverity | 'ALL'

export function AlertSidebar() {
  const { alerts } = useAlertStore()
  const { isLoading, isFetching } = useNearbyAlerts()
  const [severityFilter, setSeverityFilter] = useState<SeverityFilter>('ALL')
  const [typeFilter, setTypeFilter] = useState<AlertType | 'ALL'>('ALL')
  const [isOpen, setIsOpen] = useState(true)

  const filtered = alerts.filter((a) => {
    if (severityFilter !== 'ALL' && a.severity !== severityFilter) return false
    if (typeFilter !== 'ALL' && a.type !== typeFilter) return false
    return true
  })

  const severities: SeverityFilter[] = ['ALL', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

  return (
    <>
      {/* Toggle button */}
      <button
        onClick={() => setIsOpen((v) => !v)}
        className={`
          absolute top-16 z-[1000] flex items-center gap-2 px-3 py-2
          bg-surface-card/90 backdrop-blur border border-surface-border rounded-r-xl
          text-sm font-medium text-slate-300 hover:text-white transition-all duration-300
          ${isOpen ? 'left-80' : 'left-0'}
        `}
      >
        {isOpen ? '◀' : '▶'}
        {!isOpen && (
          <span className="flex items-center gap-1">
            <span className="w-2 h-2 rounded-full bg-brand-500 animate-pulse" />
            {alerts.length}
          </span>
        )}
      </button>

      {/* Sidebar panel */}
      <div
        className={`
          absolute top-0 bottom-0 left-0 z-[999] w-80
          bg-surface-DEFAULT/95 backdrop-blur-xl border-r border-surface-border
          flex flex-col transition-transform duration-300
          ${isOpen ? 'translate-x-0' : '-translate-x-full'}
        `}
      >
        {/* Header */}
        <div className="pt-16 pb-3 px-4 border-b border-surface-border">
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <h2 className="font-bold text-white">Live Alerts</h2>
              {isFetching && (
                <svg className="animate-spin h-3.5 w-3.5 text-brand-500" viewBox="0 0 24 24" fill="none">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
                </svg>
              )}
            </div>
            <span className="px-2 py-0.5 rounded-full bg-brand-600/20 text-brand-400 text-xs font-bold">
              {filtered.length}
            </span>
          </div>

          {/* Severity pills */}
          <div className="flex gap-1.5 flex-wrap">
            {severities.map((s) => (
              <button
                key={s}
                onClick={() => setSeverityFilter(s)}
                className={`
                  px-2.5 py-1 rounded-full text-xs font-medium transition-all cursor-pointer
                  ${severityFilter === s
                    ? 'bg-brand-600 text-white'
                    : 'bg-surface-elevated text-slate-400 hover:text-slate-200'
                  }
                `}
              >
                {s === 'ALL' ? 'All' : s.charAt(0) + s.slice(1).toLowerCase()}
              </button>
            ))}
          </div>
        </div>

        {/* Type filter scrollable row */}
        <div className="flex gap-2 px-4 py-2.5 overflow-x-auto border-b border-surface-border">
          <button
            onClick={() => setTypeFilter('ALL')}
            className={`flex-shrink-0 px-2.5 py-1 rounded-full text-xs font-medium transition-all cursor-pointer ${typeFilter === 'ALL' ? 'bg-brand-600 text-white' : 'bg-surface-elevated text-slate-400 hover:text-slate-200'}`}
          >
            All types
          </button>
          {(Object.keys(ALERT_TYPE_LABELS) as AlertType[]).map((t) => (
            <button
              key={t}
              onClick={() => setTypeFilter(t)}
              className={`flex-shrink-0 flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-medium transition-all cursor-pointer ${typeFilter === t ? 'bg-brand-600 text-white' : 'bg-surface-elevated text-slate-400 hover:text-slate-200'}`}
            >
              <span>{ALERT_TYPE_ICONS[t]}</span>
              <span>{ALERT_TYPE_LABELS[t]}</span>
            </button>
          ))}
        </div>

        {/* Alert list */}
        <div className="flex-1 overflow-y-auto p-3 space-y-2">
          {isLoading ? (
            <div className="flex flex-col gap-2">
              {[1, 2, 3].map((i) => (
                <div key={i} className="h-20 rounded-xl bg-surface-elevated animate-pulse" />
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500">
              <span className="text-4xl mb-3">🔍</span>
              <p className="text-sm font-medium">No alerts in this area</p>
              <p className="text-xs mt-1">Move the map to explore other areas</p>
            </div>
          ) : (
            filtered.map((alert) => <AlertCard key={alert.id} alert={alert} />)
          )}
        </div>
      </div>
    </>
  )
}
