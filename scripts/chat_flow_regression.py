#!/usr/bin/env python3
"""Real HTTP, STOMP/WebSocket, Redis and MySQL-facing chat regression test."""

import argparse
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid

import websocket


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")

    def call(self, method, path, body=None, token=None, expected=(200, 201)):
        data = None if body is None else json.dumps(body).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers=headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                payload = json.loads(response.read().decode("utf-8"))
                if response.status not in expected:
                    raise AssertionError((response.status, payload))
                return payload
        except urllib.error.HTTPError as error:
            payload = error.read().decode("utf-8", errors="replace")
            raise AssertionError(f"{method} {path} failed: {error.code} {payload}")

    def login(self, username, password):
        return self.call(
            "POST",
            "/chat/login",
            {"username": username, "password": password},
        )["data"]["token"]


class StompClient:
    def __init__(self, base_url, token):
        ws_url = base_url.replace("http://", "ws://").replace("https://", "wss://")
        self.socket = websocket.create_connection(
            ws_url.rstrip("/") + "/ws/chat?token=" + token,
            origin=base_url,
            timeout=5,
        )
        self.subscription_index = 0
        self.send_raw("CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n\n\x00")
        command, _, _ = self.receive_frame(5)
        if command != "CONNECTED":
            raise AssertionError("STOMP connection failed: " + command)

    def send_raw(self, frame):
        self.socket.send(frame)

    def subscribe(self, destination):
        self.subscription_index += 1
        self.send_raw(
            "SUBSCRIBE\nid:sub-%d\ndestination:%s\nack:auto\n\n\x00"
            % (self.subscription_index, destination)
        )

    def send_json(self, destination, body):
        payload = json.dumps(body, ensure_ascii=False, separators=(",", ":"))
        frame = (
            "SEND\ndestination:%s\ncontent-type:application/json\n"
            "content-length:%d\n\n%s\x00"
            % (destination, len(payload.encode("utf-8")), payload)
        )
        self.send_raw(frame)

    def receive_frame(self, timeout):
        deadline = time.time() + timeout
        while time.time() < deadline:
            self.socket.settimeout(max(0.1, deadline - time.time()))
            raw = self.socket.recv()
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8")
            raw = raw.strip("\n\r\x00")
            if not raw:
                continue
            head, _, body = raw.partition("\n\n")
            lines = head.splitlines()
            command = lines[0]
            headers = {}
            for line in lines[1:]:
                key, separator, value = line.partition(":")
                if separator:
                    headers[key] = value
            return command, headers, body.rstrip("\x00")
        raise TimeoutError("No STOMP frame received")

    def receive_json(self, timeout=8, event=None, destination_suffix=None):
        deadline = time.time() + timeout
        while time.time() < deadline:
            command, headers, body = self.receive_frame(deadline - time.time())
            if command == "ERROR":
                return {"event": "STOMP_ERROR", "message": body, "headers": headers}
            if command != "MESSAGE":
                continue
            if destination_suffix and not headers.get("destination", "").endswith(destination_suffix):
                continue
            payload = json.loads(body)
            if event is None or payload.get("event") == event:
                return payload
        raise TimeoutError("Expected STOMP message was not received: " + str(event))

    def close(self):
        try:
            self.socket.close()
        except Exception:
            pass


def redis(*arguments):
    completed = subprocess.run(
        ["redis-cli", "--raw", *map(str, arguments)],
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--admin-username", default="admin")
    parser.add_argument("--admin-password", required=True)
    args = parser.parse_args()

    api = Api(args.base_url)
    admin_token = api.login(args.admin_username, args.admin_password)
    prefix = "RT" + uuid.uuid4().hex[:8].upper()
    password = "Regression@123"
    agent_ids = [prefix + "A1", prefix + "A2"]
    user_ids = [prefix + "U%d" % index for index in range(1, 10)]
    created_ids = []
    clients = []
    report = {}

    try:
        for index, agent_id in enumerate(agent_ids, 1):
            api.call(
                "POST",
                "/users",
                {"id": agent_id, "username": prefix.lower() + "agent" + str(index),
                 "password": password, "status": "ENABLED", "vipLevel": 0},
                admin_token,
            )
            created_ids.append(agent_id)
            api.call("PUT", f"/users/{agent_id}/roles/R_AGENT", token=admin_token)

        for index, user_id in enumerate(user_ids, 1):
            api.call(
                "POST",
                "/users",
                {"id": user_id, "username": prefix.lower() + "user" + str(index),
                 "password": password, "status": "ENABLED", "vipLevel": 5 if index == 7 else 0},
                admin_token,
            )
            created_ids.append(user_id)
            api.call("PUT", f"/users/{user_id}/roles/R_USER", token=admin_token)

        agent_tokens = [
            api.login(prefix.lower() + "agent" + str(index), password)
            for index in (1, 2)
        ]
        user_tokens = [
            api.login(prefix.lower() + "user" + str(index), password)
            for index in range(1, 10)
        ]

        agents = []
        for token in agent_tokens:
            client = StompClient(args.base_url, token)
            clients.append(client)
            client.subscribe("/user/queue/chat")
            client.subscribe("/user/queue/errors")
            agents.append(client)
        for token in agent_tokens:
            api.call("POST", "/chat/agent/online", token=token)
        agents[0].send_json("/app/chat.heartbeat", {})
        time.sleep(0.2)
        heartbeat_deadline = redis("ZSCORE", "online:heartbeat", agent_ids[0])
        online_ttl = redis("TTL", "user:online:" + agent_ids[0])
        if not heartbeat_deadline or int(online_ttl) <= 0:
            raise AssertionError("heartbeat did not renew online state")
        report["heartbeatRenewal"] = True

        assignments = []
        user_clients = []
        for index in range(4):
            client = StompClient(args.base_url, user_tokens[index])
            clients.append(client)
            user_clients.append(client)
            client.subscribe("/user/queue/chat")
            notice = client.receive_json(event="SESSION_CREATED")
            assignments.append(notice["session"])
        sequence = [assignment["agentId"] for assignment in assignments]
        expected = [agent_ids[0], agent_ids[1], agent_ids[0], agent_ids[1]]
        if sequence != expected:
            raise AssertionError(f"round-robin mismatch: {sequence} != {expected}")
        report["roundRobin"] = sequence

        for assignment in assignments:
            agent_index = agent_ids.index(assignment["agentId"])
            agents[agent_index].send_json(
                "/app/chat.end", {"sessionId": assignment["sessionId"]}
            )
        time.sleep(1)

        for token in agent_tokens:
            api.call("POST", "/chat/agent/offline", token=token)
        waiting = StompClient(args.base_url, user_tokens[4])
        clients.append(waiting)
        waiting.subscribe("/user/queue/chat")
        waiting_notice = waiting.receive_json()
        if waiting_notice.get("assignmentStatus") != "WAITING":
            raise AssertionError("user did not enter waiting queue")
        api.call("POST", "/chat/agent/online", token=agent_tokens[1])
        assigned_after_online = waiting.receive_json(event="SESSION_CREATED")
        if assigned_after_online["session"]["agentId"] != agent_ids[1]:
            raise AssertionError("new online agent did not receive queued user")
        report["newAgentAutoAssignment"] = True

        session = assigned_after_online["session"]
        replacement = StompClient(args.base_url, user_tokens[4])
        clients.append(replacement)
        replacement.subscribe("/user/queue/chat")
        reconnected_notice = replacement.receive_json(event="SESSION_RECONNECTED")
        if reconnected_notice["session"]["sessionId"] != session["sessionId"]:
            raise AssertionError("user reconnect created or restored the wrong session")
        waiting.close()
        clients.remove(waiting)
        waiting = replacement
        time.sleep(0.5)
        if redis("GET", "user:active:session:" + user_ids[4]) != session["sessionId"]:
            raise AssertionError("old WebSocket disconnect closed the reconnected user session")
        report["userReconnect"] = True

        agents[1].send_json(
            "/app/chat.transfer",
            {"sessionId": session["sessionId"], "targetAgentId": agent_ids[0]},
        )
        offline_error = agents[1].receive_json(destination_suffix="/queue/errors")
        if offline_error.get("event") != "ERROR":
            raise AssertionError("offline transfer target was not rejected")
        report["offlineTransferRejected"] = True

        api.call("POST", "/chat/agent/online", token=agent_tokens[0])
        redis("ZADD", "agent:load", 5, agent_ids[0])
        agents[1].send_json(
            "/app/chat.transfer",
            {"sessionId": session["sessionId"], "targetAgentId": agent_ids[0]},
        )
        full_error = agents[1].receive_json(destination_suffix="/queue/errors")
        if full_error.get("event") != "ERROR":
            raise AssertionError("full transfer target was not rejected")
        report["fullTransferRejected"] = True
        api.call("POST", "/chat/agent/online", token=agent_tokens[0])

        agents[1].send_json(
            "/app/chat.transfer",
            {"sessionId": session["sessionId"], "targetAgentId": agent_ids[0]},
        )
        transferred = waiting.receive_json(event="SESSION_TRANSFERRED")
        if transferred["agentId"] != agent_ids[0]:
            raise AssertionError("successful transfer did not update owner")
        report["successfulTransfer"] = True

        agents[0].close()
        clients.remove(agents[0])
        deadline = time.time() + 5
        while time.time() < deadline and redis("ZSCORE", "agent:load", agent_ids[0]):
            time.sleep(0.2)
        if redis("ZSCORE", "agent:load", agent_ids[0]):
            raise AssertionError("disconnected agent remained assignable")
        reconnected_agent = StompClient(args.base_url, agent_tokens[0])
        clients.append(reconnected_agent)
        reconnected_agent.subscribe("/user/queue/chat")
        time.sleep(1)
        if not redis("ZSCORE", "agent:load", agent_ids[0]):
            raise AssertionError("agent reconnect did not restore load")
        if redis("GET", "session:agent:" + session["sessionId"]) != agent_ids[0]:
            raise AssertionError("session ownership was not preserved after reconnect")
        report["agentReconnectGrace"] = True

        for token in agent_tokens:
            api.call("POST", "/chat/agent/offline", token=token)

        normal_waiter = StompClient(args.base_url, user_tokens[5])
        vip_waiter = StompClient(args.base_url, user_tokens[6])
        clients.extend([normal_waiter, vip_waiter])
        normal_waiter.subscribe("/user/queue/chat")
        normal_waiter.receive_json()
        vip_waiter.subscribe("/user/queue/chat")
        vip_notice = vip_waiter.receive_json()
        if vip_notice.get("vipLevel") != 5:
            raise AssertionError("VIP level was not included in waiting result")
        pending_order = redis("ZRANGE", "queue:pending", 0, 1).splitlines()
        if not pending_order or pending_order[0] != user_ids[6]:
            raise AssertionError("VIP user was not placed before normal user")
        api.call("POST", "/chat/agent/online", token=agent_tokens[1])
        vip_waiter.receive_json(event="SESSION_CREATED")
        normal_waiter.receive_json(event="SESSION_CREATED")
        report["vipPriority"] = True

        for token in agent_tokens:
            api.call("POST", "/chat/agent/offline", token=token)
        timeout_client = StompClient(args.base_url, user_tokens[7])
        clients.append(timeout_client)
        timeout_client.subscribe("/user/queue/chat")
        timeout_client.receive_json()
        redis("ZADD", "queue:enqueued-at", int(time.time() * 1000) - 600000, user_ids[7])
        timeout_notice = timeout_client.receive_json(timeout=20, event="WAITING_TIMEOUT")
        if timeout_notice.get("event") != "WAITING_TIMEOUT":
            raise AssertionError("queue timeout notification missing")
        report["queueTimeout"] = True

        print(json.dumps({"result": "PASS", "prefix": prefix, "checks": report}, ensure_ascii=False))
    finally:
        for client in reversed(clients):
            client.close()
        for user_id in reversed(created_ids):
            try:
                api.call("DELETE", "/users/" + user_id, token=admin_token)
            except Exception:
                pass
        for key in ("agent:load", "agent:reconnect:grace"):
            for agent_id in agent_ids:
                try:
                    redis("ZREM", key, agent_id)
                except Exception:
                    pass


if __name__ == "__main__":
    main()
