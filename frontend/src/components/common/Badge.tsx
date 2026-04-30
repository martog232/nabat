import type { AlertSeverity } from '../../types'
import { SEVERITY_COLORS } from '../../types'

interface BadgeProps {
  severity: AlertSeverity
  label?: string
}

export function SeverityBadge({ severity, label }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold border ${SEVERITY_COLORS[severity]}`}
    >
      {label ?? severity}
    </span>
  )
}
