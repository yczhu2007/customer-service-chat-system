#!/usr/bin/env python3
"""Real HTTP, STOMP/WebSocket, Redis and MySQL-facing chat regression test."""

import argparse
import json
import os
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


def mysql_query(sql, user, password, database="springboot"):
    environment = os.environ.copy()
    environment["MYSQL_PWD"] = password
    completed = subprocess.run(
        [
            "mysql",
            "--default-character-set=utf8mb4",
            "--batch",
            "--skip-column-names",
            "-u",
            user,
            database,
            "-e",
            sql,
        ],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        env=environment,
    )
    return completed.stdout.strip()


def redis_scan(pattern):
    completed = subprocess.run(
        ["redis-cli", "--raw", "--scan", "--pattern", pattern],
        check=True,
        capture_output=True,
        text=True,
    )
    return [line for line in completed.stdout.splitlines() if line]


def cleanup_test_data(prefix, created_ids, db_user, db_password, db_name):
    session_ids = mysql_query(
        "SELECT id FROM chat_session "
        f"WHERE user_id LIKE '{prefix}%' OR agent_id LIKE '{prefix}%';",
        db_user,
        db_password,
        db_name,
    ).splitlines()
    message_ids = []
    if session_ids:
        quoted_sessions = ",".join("'" + item + "'" for item in session_ids)
        message_ids = mysql_query(
            f"SELECT id FROM chat_message WHERE session_id IN ({quoted_sessions});",
            db_user,
            db_password,
            db_name,
        ).splitlines()
        mysql_query(
            f"DELETE FROM chat_message WHERE session_id IN ({quoted_sessions});"
            f"DELETE FROM chat_session WHERE id IN ({quoted_sessions});",
            db_user,
            db_password,
            db_name,
        )

    if created_ids:
        quoted_users = ",".join("'" + item + "'" for item in created_ids)
        mysql_query(
            f"DELETE FROM sys_user_role WHERE user_id IN ({quoted_users});"
            f"DELETE FROM sys_user WHERE id IN ({quoted_users});",
            db_user,
            db_password,
            db_name,
        )

    for key in redis_scan("*" + prefix + "*"):
        redis("DEL", key)
    for user_id in created_ids:
        for key in (
            "user:online:" + user_id,
            "user:ws:" + user_id,
            "user:active:session:" + user_id,
            "offline:msg:" + user_id,
            "chat:assign:lock:" + user_id,
        ):
            redis("DEL", key)
        redis("SREM", "online:users", user_id)
        for key in (
            "online:heartbeat", "agent:load", "agent:last-assigned",
            "agent:reconnect:grace", "queue:pending", "queue:enqueued-at",
        ):
            redis("ZREM", key, user_id)
        for key in ("queue:vip-level", "queue:sequence"):
            redis("HDEL", key, user_id)
    for session_id in session_ids:
        for key in (
            "session:user:" + session_id,
            "session:agent:" + session_id,
            "session:meta:" + session_id,
            "session:msg:" + session_id,
            "session:operation:lock:" + session_id,
        ):
            redis("DEL", key)
        redis("ZREM", "session:last-activity", session_id)
    for message_id in message_ids:
        for key in (
            "msg:ack:" + message_id,
            "persist:pending:payload:" + message_id,
            "persist:retry:count:" + message_id,
            "persist:retry:lease:" + message_id,
        ):
            redis("DEL", key)
        redis("ZREM", "persist:pending", message_id)
        redis("ZREM", "persist:deadletter", message_id)


def wait_until(description, predicate, timeout=10, interval=0.1):
    deadline = time.time() + timeout
    while time.time() < deadline:
        value = predicate()
        if value:
            return value
        time.sleep(interval)
    raise AssertionError("Timed out waiting for " + description)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--admin-username", default="admin")
    parser.add_argument("--admin-password", required=True)
    parser.add_argument("--db-user", default="root")
    parser.add_argument("--db-password", required=True)
    parser.add_argument("--db-name", default="springboot")
    args = parser.parse_args()

    if redis("PING") != "PONG":
        raise AssertionError("Redis preflight failed")
    required_tables = mysql_query(
        "SELECT COUNT(*) FROM information_schema.tables "
        "WHERE table_schema = DATABASE() AND table_name IN "
        "('sys_user','chat_session','chat_message','chat_message_read');",
        args.db_user,
        args.db_password,
        args.db_name,
    )
    if required_tables != "4":
        raise AssertionError("MySQL preflight failed: required chat tables are missing")

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
            client.subscribe("/user/queue/messages")
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
        report["realRedis"] = True

        assignments = []
        user_clients = []
        for index in range(4):
            client = StompClient(args.base_url, user_tokens[index])
            clients.append(client)
            user_clients.append(client)
            client.subscribe("/user/queue/chat")
            client.subscribe("/user/queue/messages")
            client.subscribe("/user/queue/errors")
            notice = client.receive_json(event="SESSION_CREATED")
            assignments.append(notice["session"])
            assigned_agent_index = agent_ids.index(notice["session"]["agentId"])
            agents[assigned_agent_index].receive_json(event="SESSION_CREATED")
        sequence = [assignment["agentId"] for assignment in assignments]
        expected = [agent_ids[0], agent_ids[1], agent_ids[0], agent_ids[1]]
        if sequence != expected:
            raise AssertionError(f"round-robin mismatch: {sequence} != {expected}")
        report["roundRobin"] = sequence
        report["longestIdleLoadBalancing"] = True

        read_session = assignments[0]
        user_clients[0].send_json(
            "/app/chat.send",
            {
                "sessionId": read_session["sessionId"],
                "type": "TEXT",
                "content": "read-state-regression",
                "clientMsgId": prefix + "-READ-1",
            },
        )
        read_agent_index = agent_ids.index(read_session["agentId"])
        delivered_message = agents[read_agent_index].receive_json(
            destination_suffix="/queue/chat"
        )
        message_id = delivered_message["id"]
        deadline = time.time() + 8
        while time.time() < deadline and redis("ZSCORE", "persist:pending", message_id):
            time.sleep(0.1)
        persisted = wait_until(
            "message persistence in MySQL",
            lambda: mysql_query(
                "SELECT CONCAT(id, '|', content) FROM chat_message "
                f"WHERE id = '{message_id}';",
                args.db_user,
                args.db_password,
                args.db_name,
            ),
        )
        if persisted != message_id + "|read-state-regression":
            raise AssertionError("MySQL stored message does not match WebSocket message")
        report["messagePersistence"] = True
        report["realMySQL"] = True
        agents[read_agent_index].send_json(
            "/app/chat.history",
            {"sessionId": read_session["sessionId"], "pageNo": 1, "pageSize": 20},
        )
        unread_history = agents[read_agent_index].receive_json(event="CHAT_HISTORY")
        if unread_history.get("unreadCount") != 1:
            raise AssertionError("persisted unread count did not increase")
        agents[read_agent_index].send_json(
            "/app/chat.read",
            {
                "sessionId": read_session["sessionId"],
                "lastReadMessageId": message_id,
            },
        )
        read_result = agents[read_agent_index].receive_json(event="MESSAGES_READ")
        sender_receipt = user_clients[0].receive_json(event="MESSAGES_READ")
        if read_result.get("unreadCount") != 0 or sender_receipt.get("readerId") != read_session["agentId"]:
            raise AssertionError("read state or read receipt was not synchronized")
        read_row_count = mysql_query(
            "SELECT COUNT(*) FROM chat_message_read "
            f"WHERE message_id = '{message_id}' "
            f"AND user_id = '{read_session['agentId']}';",
            args.db_user,
            args.db_password,
            args.db_name,
        )
        if read_row_count != "1":
            raise AssertionError("read state was not persisted in MySQL")
        report["readStateSynchronization"] = True

        edited_content = "edited-message-regression"
        user_clients[0].send_json(
            "/app/chat.message.edit",
            {"messageId": message_id, "content": edited_content},
        )
        sender_edit = user_clients[0].receive_json(event="MESSAGE_EDITED")
        receiver_edit = agents[read_agent_index].receive_json(event="MESSAGE_EDITED")
        if sender_edit.get("content") != edited_content or receiver_edit.get("content") != edited_content:
            raise AssertionError("edited content was not synchronized to both participants")

        agents[read_agent_index].send_json(
            "/app/chat.message.edit",
            {"messageId": message_id, "content": "unauthorized-edit"},
        )
        unauthorized_edit = agents[read_agent_index].receive_json(event="ERROR")
        if "只能编辑或撤回自己发送的消息" not in unauthorized_edit.get("message", ""):
            raise AssertionError("editing another participant's message was not rejected")

        user_clients[0].send_json(
            "/app/chat.message.recall",
            {"messageId": message_id},
        )
        sender_recall = user_clients[0].receive_json(event="MESSAGE_RECALLED")
        receiver_recall = agents[read_agent_index].receive_json(event="MESSAGE_RECALLED")
        if not sender_recall.get("recalled") or receiver_recall.get("content") is not None:
            raise AssertionError("message recall was not synchronized to both participants")
        mutation_state = mysql_query(
            "SELECT CONCAT(edited, '|', recalled, '|', content, '|', original_content) "
            f"FROM chat_message WHERE id = '{message_id}';",
            args.db_user,
            args.db_password,
            args.db_name,
        )
        if mutation_state != "1|1|edited-message-regression|read-state-regression":
            raise AssertionError("edited/recall state was not persisted correctly in MySQL")

        agents[read_agent_index].send_json(
            "/app/chat.history",
            {"sessionId": read_session["sessionId"], "pageNo": 1, "pageSize": 20},
        )
        mutation_history = agents[read_agent_index].receive_json(event="CHAT_HISTORY")
        mutated_message = next(
            (item for item in mutation_history.get("messages", []) if item.get("id") == message_id),
            None,
        )
        if (mutated_message is None or not mutated_message.get("edited")
                or not mutated_message.get("recalled") or mutated_message.get("content") is not None):
            raise AssertionError("history did not reflect the final edited and recalled state")

        user_clients[0].send_json(
            "/app/chat.message.recall",
            {"messageId": message_id},
        )
        duplicate_recall = user_clients[0].receive_json(event="ERROR")
        if "消息已经撤回" not in duplicate_recall.get("message", ""):
            raise AssertionError("duplicate recall was not rejected")

        user_clients[0].send_json(
            "/app/chat.send",
            {
                "sessionId": read_session["sessionId"],
                "type": "TEXT",
                "content": "unread-recall-regression",
                "clientMsgId": prefix + "-RECALL-UNREAD",
            },
        )
        unread_recall_message = agents[read_agent_index].receive_json(
            destination_suffix="/queue/chat"
        )
        unread_recall_id = unread_recall_message["id"]
        deadline = time.time() + 8
        while time.time() < deadline and redis("ZSCORE", "persist:pending", unread_recall_id):
            time.sleep(0.1)
        user_clients[0].send_json(
            "/app/chat.message.recall",
            {"messageId": unread_recall_id},
        )
        user_clients[0].receive_json(event="MESSAGE_RECALLED")
        agents[read_agent_index].receive_json(event="MESSAGE_RECALLED")
        agents[read_agent_index].send_json(
            "/app/chat.history",
            {"sessionId": read_session["sessionId"], "pageNo": 1, "pageSize": 20},
        )
        recalled_unread_history = agents[read_agent_index].receive_json(event="CHAT_HISTORY")
        if recalled_unread_history.get("unreadCount") != 0:
            raise AssertionError("a recalled message was still counted as unread")
        report["messageEdit"] = True
        report["messageRecall"] = True

        for assignment in assignments:
            agent_index = agent_ids.index(assignment["agentId"])
            agents[agent_index].send_json(
                "/app/chat.end", {"sessionId": assignment["sessionId"]}
            )
        time.sleep(1)

        inactivity_client = StompClient(args.base_url, user_tokens[8])
        clients.append(inactivity_client)
        inactivity_client.subscribe("/user/queue/chat")
        inactivity_session = inactivity_client.receive_json(event="SESSION_CREATED")["session"]
        redis("ZADD", "session:last-activity", 1, inactivity_session["sessionId"])
        inactivity_client.receive_json(timeout=40, event="SESSION_CLOSED")
        reassigned_after_timeout = inactivity_client.receive_json(
            timeout=8,
            event="SESSION_CREATED",
        )["session"]
        if reassigned_after_timeout["agentId"] == inactivity_session["agentId"]:
            raise AssertionError("inactive session was assigned back to the same agent")
        session_states = mysql_query(
            "SELECT CONCAT(id, '|', status, '|', agent_id) FROM chat_session "
            f"WHERE id IN ('{inactivity_session['sessionId']}', "
            f"'{reassigned_after_timeout['sessionId']}') ORDER BY id;",
            args.db_user,
            args.db_password,
            args.db_name,
        )
        if "|CLOSED|" not in session_states or "|ACTIVE|" not in session_states:
            raise AssertionError("timeout reassignment was not committed in MySQL")
        if redis(
            "GET", "session:agent:" + reassigned_after_timeout["sessionId"]
        ) != reassigned_after_timeout["agentId"]:
            raise AssertionError("timeout reassignment Redis ownership is inconsistent")
        reassigned_agent_index = agent_ids.index(reassigned_after_timeout["agentId"])
        agents[reassigned_agent_index].send_json(
            "/app/chat.end",
            {"sessionId": reassigned_after_timeout["sessionId"]},
        )
        report["sessionInactivityReassignment"] = True

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
        for key in ("agent:load", "agent:reconnect:grace", "agent:last-assigned"):
            for agent_id in agent_ids:
                try:
                    redis("ZREM", key, agent_id)
                except Exception:
                    pass
        cleanup_test_data(
            prefix,
            created_ids,
            args.db_user,
            args.db_password,
            args.db_name,
        )


if __name__ == "__main__":
    main()
