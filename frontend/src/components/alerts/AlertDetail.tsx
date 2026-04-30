import { useAlertStore } from '../../store/alertStore'
import { useVoteStats, useMyVote, useVote, useRemoveVote } from '../../hooks/useAlerts'
import { useAuthStore } from '../../store/authStore'
import { ALERT_TYPE_ICONS, ALERT_TYPE_LABELS } from '../../types'
import { SeverityBadge } from '../common/Badge'

function formatDate(iso: string) {
  return new Date(iso).toLocaleString('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

export function AlertDetail() {
  const { selectedAlert, selectAlert } = useAlertStore()
  const user = useAuthStore((s) => s.user)

  const { data: stats } = useVoteStats(selectedAlert?.id ?? '')
  const { data: myVote } = useMyVote(selectedAlert?.id ?? '')
  const vote = useVote(selectedAlert?.id ?? '')
  const removeVote = useRemoveVote(selectedAlert?.id ?? '')

  if (!selectedAlert) return null

  const a = selectedAlert
  return (
    <div className="absolute bottom-20 left-0 right-0 mx-4 sm:left-auto sm:right-4 sm:mx-0 sm:w-96 z-[1000] animate-slide-in-right">
      <div className="bg-surface-card border border-surface-border rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="relative p-4 border-b border-surface-border">
          <button
            onClick={() => selectAlert(null)}
            className="absolute top-3 right-3 w-7 h-7 rounded-full flex items-center justify-center text-slate-400 hover:text-white hover:bg-surface-elevated transition-colors"
          >
            ✕
          </button>

          <div className="flex items-start gap-3 pr-8">
            <div className="text-3xl">{ALERT_TYPE_ICONS[a.type]}</div>
            <div>
              <h3 className="font-bold text-white text-base leading-tight mb-1">{a.title}</h3>
              <div className="flex items-center gap-2">
                <SeverityBadge severity={a.severity} />
                <span className="text-xs text-slate-500">{ALERT_TYPE_LABELS[a.type]}</span>
              </div>
            </div>
          </div>
        </div>

        {/* Body */}
        <div className="p-4 space-y-3">
          <p className="text-sm text-slate-300 leading-relaxed">{a.description}</p>

          <div className="flex items-center gap-4 text-xs text-slate-500">
            <span>📅 {formatDate(a.createdAt)}</span>
            <span
              className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                a.status === 'ACTIVE' ? 'bg-green-500/10 text-green-400' : 'bg-slate-500/10 text-slate-400'
              }`}
            >
              {a.status}
            </span>
          </div>

          {/* Vote stats */}
          {stats && (
            <div className="grid grid-cols-3 gap-2">
              {[
                { label: '👍 Up', val: stats.upvotes, type: 'UPVOTE' },
                { label: '👎 Down', val: stats.downvotes, type: 'DOWNVOTE' },
                { label: '✅ Confirm', val: stats.confirmations, type: 'CONFIRM' },
              ].map(({ label, val, type }) => (
                <button
                  key={type}
                  disabled={!user || vote.isPending || removeVote.isPending}
                  onClick={() => {
                    if (!user) return
                    if (myVote?.hasVoted) {
                      removeVote.mutate()
                    } else {
                      vote.mutate(type)
                    }
                  }}
                  className={`
                    flex flex-col items-center py-2 rounded-xl border transition-all cursor-pointer
                    ${myVote?.hasVoted ? 'border-brand-500/50 bg-brand-500/10' : 'border-surface-border bg-surface-elevated hover:border-brand-500/30'}
                    disabled:opacity-50 disabled:cursor-not-allowed
                  `}
                >
                  <span className="text-xs font-bold text-white">{val}</span>
                  <span className="text-xs text-slate-400">{label}</span>
                </button>
              ))}
            </div>
          )}

          {stats && (
            <div className="flex items-center justify-between text-xs text-slate-500 pt-1 border-t border-surface-border">
              <span>Credibility score</span>
              <span className={`font-bold ${stats.credibilityScore > 0 ? 'text-green-400' : stats.credibilityScore < 0 ? 'text-red-400' : 'text-slate-400'}`}>
                {stats.credibilityScore > 0 ? '+' : ''}{stats.credibilityScore}
              </span>
            </div>
          )}

          {!user && (
            <p className="text-xs text-slate-500 text-center">
              <a href="/login" className="text-brand-400 hover:underline">Sign in</a> to vote
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
