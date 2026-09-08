import { useAuthStore } from './auth'
import { request } from '../services/http-client'
import { createStompClient } from '../services/stomp-client'

export const connectionActions = {
connectStomp() {
  const auth = useAuthStore()
  if (!auth.token) return
  if (this.connected || this.connectionState === 'connecting') return
  this.connectionState = 'connecting'
  this.connectionError = null
  this.nextReconnectAt = null
  this.manualDisconnect = false
  this.logConnection('正在连接 WebSocket')
  const attempt = ++this._connectAttempt
  const ticketAbortController = new AbortController()
  this._ticketAbortController = ticketAbortController

  // Acquire ws ticket, then connect
  request('/chat/ws-ticket', {
  method: 'POST',
  signal: ticketAbortController.signal,
  })
  .then((ticketResult) => {
    if (attempt !== this._connectAttempt || this.manualDisconnect) return
    const ticket = ticketResult?.data?.ticket || ticketResult?.ticket
    if (!ticket) throw new Error('未获取到WebSocket票据')

    const wsUrl =
      (location.protocol === 'https:' ? 'wss://' : 'ws://') +
      location.host +
      `/ws/chat?ticket=${encodeURIComponent(ticket)}`

    const stomp = createStompClient({
      brokerURL: wsUrl,
      connectHeaders: { Authorization: `Bearer ${auth.token}` },
      onConnect: () => {
        if (attempt !== this._connectAttempt || this.manualDisconnect) {
          stomp.disconnect()
          return
        }
        this.connected = true
        this.connectionState = 'connected'
        this.reconnectAttempts = 0
        this.nextReconnectAt = null
        this.logConnection('WebSocket 已连接')
        this.startHeartbeat()
        // Subscribe to chat events
        stomp.subscribe('/user/queue/chat', (frame) => {
          const body = JSON.parse(frame.body)
          this._handleChatEvent(body)
        })
        // Subscribe to message events (read, recall, ack)
        stomp.subscribe('/user/queue/messages', (frame) => {
          const body = JSON.parse(frame.body)
          this._handleMessageEvent(body)
        })
        stomp.subscribe('/user/queue/errors', (frame) => {
          try {
            const body = JSON.parse(frame.body)
            this._handleStompErrorEvent(body)
          } catch {
            this._handleStompErrorEvent({ message: frame.body || '操作失败' })
          }
        })
        if (this._restoreAgentOnlineAfterReconnect) {
          this._restoreAgentOnlineAfterReconnect = false
          request('/chat/agent/online', { method: 'POST' })
            .catch((error) => this.handleConnectionError(`恢复客服接待失败: ${error.message}`))
        }
        // Pull offline messages on connect
        this.pullOfflineMessages()
      },
      onMessage: (frame) => {
        // fallback handler
      },
      onError: (message) => this.handleConnectionError(message),
      onDisconnect: () => {
        const wasManual = this.manualDisconnect
        this.manualDisconnect = false
        if (!wasManual && auth.role === 'AGENT' && this.agentOnline) {
          this._restoreAgentOnlineAfterReconnect = true
        }
        this.stopHeartbeat()
        this.connected = false
        this.connectionState = 'disconnected'
        this.logConnection('WebSocket 已断开')
        if (!wasManual) this.reconnectStomp()
      },
    })

    this._stomp = stomp
    stomp.connect()

    // Store deactivate for cleanup
    this._deactivateStomp = () => {
      stomp.disconnect()
      this.connected = false
      this.connectionState = 'disconnected'
      this._stomp = null
    }
  })
  .catch((e) => {
    if (e.name === 'AbortError' || attempt !== this._connectAttempt) return
    this.error = 'WebSocket连接失败: ' + e.message
    this.handleConnectionError(this.error)
    if (!this.manualDisconnect) this.reconnectStomp()
  })
},

/**
 * Disconnect STOMP.
 */
/** Disconnect transport. Manual disconnects suppress automatic reconnect. */
disconnectStomp({ manual = true } = {}) {
  this._connectAttempt += 1
  if (this._ticketAbortController) {
  this._ticketAbortController.abort()
  this._ticketAbortController = null
  }
  this.manualDisconnect = manual
  this.stopHeartbeat()
  if (this._reconnectTimer) {
  clearTimeout(this._reconnectTimer)
  this._reconnectTimer = null
  }
  if (this._deactivateStomp) {
  this._deactivateStomp()
  this._deactivateStomp = null
  }
  this.connectionState = 'disconnected'
  this.connected = false
  this.nextReconnectAt = null
  if (manual) this.logConnection('已手动断开 WebSocket')
},


/** Send the business heartbeat expected by the backend presence tracker. */
startHeartbeat() {
  this.stopHeartbeat()
  if (!this._stomp || !this.connected) return
  const send = () => {
  if (this._stomp && this.connected) this._stomp.publish('/app/chat.heartbeat', {})
  }
  send()
  this._heartbeatTimer = setInterval(send, 30000)
},

stopHeartbeat() {
  if (this._heartbeatTimer) {
  clearInterval(this._heartbeatTimer)
  this._heartbeatTimer = null
  }
},


}
