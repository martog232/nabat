import { useState, useRef, useEffect } from 'react'
import { useAlertStore } from '../../store/alertStore'
import { useCreateAlert } from '../../hooks/useAlerts'
import type { AlertType, AlertSeverity, CreateAlertRequest } from '../../types'
import { ALERT_TYPE_LABELS, ALERT_TYPE_ICONS } from '../../types'
import { Button } from '../common/Button'
import { Input } from '../common/Input'

interface Props {
  onClose: () => void
  prefillLat?: number
  prefillLng?: number
}

const ALERT_TYPES = Object.keys(ALERT_TYPE_LABELS) as AlertType[]
const SEVERITIES: AlertSeverity[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL']

const SEVERITY_STYLES: Record<AlertSeverity, string> = {
  LOW: 'border-green-500/50 bg-green-500/10 text-green-400',
  MEDIUM: 'border-yellow-500/50 bg-yellow-500/10 text-yellow-400',
  HIGH: 'border-orange-500/50 bg-orange-500/10 text-orange-400',
  CRITICAL: 'border-red-500/50 bg-red-500/10 text-red-400',
}

export function CreateAlertModal({ onClose, prefillLat, prefillLng }: Props) {
  const { mapCenter } = useAlertStore()
  const mutation = useCreateAlert()

  const [form, setForm] = useState<CreateAlertRequest>({
    title: '',
    description: '',
    type: 'OTHER',
    severity: 'MEDIUM',
    latitude: prefillLat ?? mapCenter[0],
    longitude: prefillLng ?? mapCenter[1],
  })
  const [errors, setErrors] = useState<Partial<Record<keyof CreateAlertRequest, string>>>({})
  const overlayRef = useRef<HTMLDivElement>(null)

  // Close on overlay click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (e.target === overlayRef.current) onClose()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onClose])

  // Close on Escape
  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [onClose])

  const validate = (): boolean => {
    const e: typeof errors = {}
    if (!form.title.trim()) e.title = 'Title is required'
    if (form.title.length > 200) e.title = 'Max 200 characters'
    if (!form.description.trim()) e.description = 'Description is required'
    if (form.description.length > 2000) e.description = 'Max 2000 characters'
    setErrors(e)
    return Object.keys(e).length === 0
  }

  const handleSubmit = async () => {
    if (!validate()) return
    try {
      await mutation.mutateAsync(form)
      onClose()
    } catch {
      // error shown via mutation.error
    }
  }

  return (
    <div
      ref={overlayRef}
      className="fixed inset-0 z-[2000] flex items-center justify-center bg-black/60 backdrop-blur-sm px-4 animate-fade-in"
    >
      <div className="w-full max-w-lg bg-surface-card border border-surface-border rounded-2xl shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-surface-border">
          <h2 className="text-base font-bold text-white">Report Incident</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white transition-colors text-lg">✕</button>
        </div>

        {/* Form */}
        <div className="p-5 space-y-4 max-h-[70vh] overflow-y-auto">
          <Input
            label="Title"
            placeholder="Brief incident description"
            value={form.title}
            onChange={(e) => setForm((f) => ({ ...f, title: e.target.value }))}
            error={errors.title}
          />

          <div className="flex flex-col gap-1">
            <label className="text-xs font-medium text-slate-400 uppercase tracking-wide">Description</label>
            <textarea
              rows={3}
              placeholder="Provide more details about the incident…"
              value={form.description}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              className={`
                w-full px-3 py-2.5 rounded-lg text-sm resize-none
                bg-surface-elevated border text-slate-200 placeholder-slate-500
                focus:outline-none focus:ring-2 focus:ring-brand-500 focus:border-transparent
                ${errors.description ? 'border-red-500/60' : 'border-surface-border'}
              `}
            />
            {errors.description && <p className="text-xs text-red-400">{errors.description}</p>}
          </div>

          {/* Alert type grid */}
          <div>
            <label className="text-xs font-medium text-slate-400 uppercase tracking-wide block mb-2">Incident Type</label>
            <div className="grid grid-cols-5 gap-2">
              {ALERT_TYPES.map((t) => (
                <button
                  key={t}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, type: t }))}
                  className={`
                    flex flex-col items-center py-2 px-1 rounded-xl border text-xs transition-all cursor-pointer
                    ${form.type === t ? 'border-brand-500/60 bg-brand-500/15 text-brand-300' : 'border-surface-border bg-surface-elevated text-slate-400 hover:border-surface-elevated hover:text-slate-200'}
                  `}
                >
                  <span className="text-lg">{ALERT_TYPE_ICONS[t]}</span>
                  <span className="mt-0.5 text-center leading-tight" style={{ fontSize: '0.65rem' }}>
                    {ALERT_TYPE_LABELS[t].split(' ')[0]}
                  </span>
                </button>
              ))}
            </div>
          </div>

          {/* Severity */}
          <div>
            <label className="text-xs font-medium text-slate-400 uppercase tracking-wide block mb-2">Severity</label>
            <div className="grid grid-cols-4 gap-2">
              {SEVERITIES.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setForm((f) => ({ ...f, severity: s }))}
                  className={`
                    py-2 rounded-xl border text-xs font-semibold transition-all cursor-pointer
                    ${form.severity === s ? SEVERITY_STYLES[s] : 'border-surface-border bg-surface-elevated text-slate-400 hover:text-slate-200'}
                  `}
                >
                  {s}
                </button>
              ))}
            </div>
          </div>

          {/* Coordinates */}
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Latitude"
              type="number"
              step="any"
              value={form.latitude}
              onChange={(e) => setForm((f) => ({ ...f, latitude: parseFloat(e.target.value) }))}
            />
            <Input
              label="Longitude"
              type="number"
              step="any"
              value={form.longitude}
              onChange={(e) => setForm((f) => ({ ...f, longitude: parseFloat(e.target.value) }))}
            />
          </div>

          {mutation.isError && (
            <p className="text-sm text-red-400 bg-red-400/10 border border-red-400/30 rounded-lg px-3 py-2">
              Failed to create alert. Please try again.
            </p>
          )}
        </div>

        {/* Footer */}
        <div className="flex gap-3 px-5 py-4 border-t border-surface-border">
          <Button variant="secondary" className="flex-1" onClick={onClose}>
            Cancel
          </Button>
          <Button className="flex-1" isLoading={mutation.isPending} onClick={handleSubmit}>
            🚨 Report Incident
          </Button>
        </div>
      </div>
    </div>
  )
}
