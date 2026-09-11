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

  // 先获取一次性握手票据，再建立连接。
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
        stomp.subscribe('/user/queue/chat', (frame) => {
          const body = JSON.parse(frame.body)
          this._handleChatEvent(body)
        })
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
        // 连接成功后重推尚未确认的消息。
        this.pullOfflineMessages()
      },
      onMessage: (frame) => {
        // 兼容 STOMP 客户端的兜底回调；业务事件由专用订阅处理。
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

    // 统一释放当前连接及本地状态。
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

/** 断开传输连接；手动断开不会触发自动重连。 */
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


/** 发送后端在线状态服务所需的业务心跳。 */
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
