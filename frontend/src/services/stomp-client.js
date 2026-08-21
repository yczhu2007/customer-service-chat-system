import { Client } from '@stomp/stompjs'
export function createStompClient({ brokerURL, connectHeaders = {}, onConnect, onMessage } = {}) {
  const client = new Client({ brokerURL, connectHeaders, onConnect, onStompError: console.error })
  return { client, connect: () => client.activate(), disconnect: () => client.deactivate(), subscribe: (destination, callback = onMessage) => client.subscribe(destination, callback), publish: (destination, body) => client.publish({ destination, body: JSON.stringify(body) }) }
}
