import { Client } from '@stomp/stompjs'
export function createStompClient({ brokerURL, connectHeaders = {}, onConnect, onMessage, onDisconnect, onError } = {}) {
  const client = new Client({
    brokerURL,
    connectHeaders,
    onConnect,
    onStompError: (frame) => onError?.(frame?.headers?.message || 'STOMP 服务端错误'),
    onWebSocketError: () => onError?.('WebSocket 连接错误'),
    onWebSocketClose: () => onDisconnect?.(),
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    reconnectDelay: 0,
  })
  return { client, connect: () => client.activate(), disconnect: () => client.deactivate(), subscribe: (destination, callback = onMessage) => client.subscribe(destination, callback), publish: (destination, body) => client.publish({ destination, body: JSON.stringify(body) }) }
}
