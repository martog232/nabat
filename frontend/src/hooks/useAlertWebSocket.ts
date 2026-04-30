import { useEffect, useRef, useCallback } from 'react'
import { API_BASE } from '../api/client'
import { useAlertStore } from '../store/alertStore'
import type { WsFrame } from '../types'

const WS_BASE = API_BASE.replace(/^http/, 'ws')

export function useAlertWebSocket(userId: string | null) {
  const addAlert = useAlertStore((s) => s.addAlert)
  const wsRef = useRef<WebSocket | null>(null)
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const connect = useCallback(() => {
    if (!userId) return
    const url = `${WS_BASE}/ws/alerts?userId=${userId}`
    const ws = new WebSocket(url)
    wsRef.current = ws

    ws.onmessage = (evt) => {
      try {
        const frame: WsFrame = JSON.parse(evt.data as string)
        if (frame.type === 'NEW_ALERT') {
          addAlert(frame.alert)
        }
      } catch {
        // ignore malformed frames
      }
    }

    ws.onclose = () => {
      // Reconnect after 3 s
      reconnectTimer.current = setTimeout(connect, 3000)
    }

    ws.onerror = () => ws.close()
  }, [userId, addAlert])

  useEffect(() => {
    connect()
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current)
      wsRef.current?.close()
    }
  }, [connect])
}
