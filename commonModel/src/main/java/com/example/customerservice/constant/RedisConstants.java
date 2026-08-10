package com.example.customerservice.constant;


public class RedisConstants {

    public static final String AGENT_LOAD = "agent:load";
    /** 客服上次被分配时间，score越小表示空闲或等待分配时间越长。 */
    public static final String AGENT_LAST_ASSIGNED = "agent:last-assigned";
    public static final int AGENT_MAX_CONCURRENCY = 5;
    /** 等待用户 ZSET，score 为入队时间戳。 */
    public static final String QUEUE_PENDING = "queue:pending";
    /** 等待用户真实入队时间，避免VIP加权score影响超时判断。 */
    public static final String QUEUE_ENQUEUED_AT = "queue:enqueued-at";
    /** 排队用户VIP等级Hash，供Lua分配和超时任务读取。 */
    public static final String QUEUE_VIP_LEVEL = "queue:vip-level";
    /** 保存上一次入队 score，用于在同一毫秒内生成严格递增的 FIFO score。 */
    public static final String QUEUE_SEQUENCE = "queue:sequence";
    private static final String AGENT_PREFIX = "agent:";
    private static final String AGENT_SESSIONS_SUFFIX = ":sessions";
    public static final String AGENT_RECONNECT_GRACE = "agent:reconnect:grace";
    /** 具有VIP接待技能的客服集合。 */
    public static final String AGENT_SKILL_VIP = "agent:skill:vip";
    /** VIP用户等待时长统计，member为sessionId，score为毫秒。 */
    public static final String STATS_VIP_WAIT = "stats:vip:wait";
    /** VIP用户会话解决时长统计，member为sessionId，score为毫秒。 */
    public static final String STATS_VIP_RESOLVE = "stats:vip:resolve";
    /** 全部客服离线时等待回呼的VIP用户。 */
    public static final String VIP_CALLBACK_PENDING = "vip:callback:pending";
    public static final String CHAT_ASSIGN_LOCK = "chat:assign:lock:";
    public static final long CHAT_ASSIGN_LOCK_TTL_SECONDS = 30L;
    /** 防止转接、结束和断线清理并发修改同一会话。 */
    public static final String SESSION_OPERATION_LOCK = "session:operation:lock:";
    /** 会话结束或转接锁，覆盖一次完整数据库事务的正常执行时间。 */
    public static final long SESSION_OPERATION_LOCK_TTL_SECONDS = 30L;
    public static final String ASSIGNMENT_PENDING = "assignment:pending";
    public static final String ASSIGNMENT_PENDING_PAYLOAD = "assignment:pending:payload";
    public static final String SESSION_FINALIZE_PENDING = "session:finalize:pending";
    public static final String SESSION_FINALIZE_PENDING_PAYLOAD =
            "session:finalize:pending:payload";
    public static final String SESSION_MSG = "session:msg:";
    /** 活动会话最后一次消息时间，供会话无活动超时转分配任务扫描。 */
    public static final String SESSION_LAST_ACTIVITY = "session:last-activity";
    public static final String CLIENT_MSG_DEDUP = "client:msg:dedup:";
    /**
     * 异步落库失败的消息。
     *
     * 类型：Hash
     * field：服务端消息ID
     * value：完整消息JSON
     */
    /** 尚未被 MySQL 确认写入的消息 ZSET。 */
    public static final String PERSIST_PENDING = "persist:pending";
    /** 待落库消息的完整 JSON 内容。 */
    public static final String PERSIST_PENDING_PAYLOAD = "persist:pending:payload:";
    /** 达到最大重试次数后等待人工处理的消息 ZSET。 */
    public static final String PERSIST_DEADLETTER = "persist:deadletter";
    public static final String PERSIST_RETRY_COUNT = "persist:retry:count:";
    public static final String PERSIST_RETRY_LEASE = "persist:retry:lease:";
    public static final long PERSIST_RETRY_LEASE_SECONDS = 180L;
    //websocket
    public static final String USER_WS = "user:ws:";
    public static final String WS_SESSION = "ws:session:";
    /**
     * 用户在线状态
     *
     * 完整Key：
     * user:online:{userId}
     */
    public static final String USER_ONLINE =
            "user:online:";


    /**
     * 用户当前活动会话
     *
     * 完整Key：
     * user:active:session:{userId}
     */
    public static final String USER_ACTIVE_SESSION =
            "user:active:session:";


    /**
     * 会话对应的普通用户
     *
     * 完整Key：
     * session:user:{sessionId}
     */
    public static final String SESSION_USER =
            "session:user:";


    /**
     * 会话对应的客服
     *
     * 完整Key：
     * session:agent:{sessionId}
     */
    public static final String SESSION_AGENT =
            "session:agent:";


    /**
     * 会话基本状态
     *
     * 完整Key：
     * session:meta:{sessionId}
     */
    public static final String SESSION_META =
            "session:meta:";


    /**
     * 已结束会话的Redis元数据保留时间
     */
    public static final long SESSION_META_TTL_HOURS =
            24L;

    /**
     * 用户离线消息列表
     *
     * 完整Key：
     * offline:msg:{userId}
     */
    public static final String OFFLINE_MSG =
            "offline:msg:";


    /**
     * 每个用户最多保留的离线消息数量
     */
    public static final long OFFLINE_MSG_MAX_COUNT =
            200L;


    /**
     * 离线消息保留天数
     */
    public static final long OFFLINE_MSG_TTL_DAYS =
            7L;
    /**
     * 业务消息ACK状态
     *
     * 完整Key：
     * msg:ack:{messageId}
     */
    public static final String MSG_ACK =
            "msg:ack:";
    /**
     * 心跳超时时间表。
     *
     * 类型：ZSet
     * member：用户ID或客服ID
     * score：心跳超时时间戳
     */
    public static final String ONLINE_HEARTBEAT =
            "online:heartbeat";


    /**
     * 客户端每30秒发送一次心跳
     */
    public static final long HEARTBEAT_INTERVAL_SECONDS =
            30L;


    /**
     * 90秒没有心跳则认为超时
     */
    public static final long HEARTBEAT_TIMEOUT_SECONDS =
            90L;
    /**
     * 登录Token前缀。
     *
     * 完整Key：
     * token:{token}
     *
     * value：
     * 当前登录用户的id
     */
    public static final String TOKEN_PREFIX =
            "token:";


    /**
     * Token有效期：30分钟。
     */
    public static final long TOKEN_TTL_MINUTES =
            30L;


    /**
     * 生成完整Token Key。
     */
    public static String tokenKey(
            String token
    ) {

        return TOKEN_PREFIX + token;
    }

    /**
     * 客服持有的全部活跃会话反向索引：agent:{agentId}:sessions。
     */
    public static String agentSessionsKey(String agentId) {
        return AGENT_PREFIX + agentId + AGENT_SESSIONS_SUFFIX;
    }

    /*
     * 当前心跳超时扫描采用90秒。
     */
    public static final long HEARTBEAT_TIMEOUT_MILLIS =
            90_000L;

    /*
     * Redis只保留会话最近200条消息。
     */
    public static final long SESSION_MESSAGE_LIMIT =
            200L;

    /*
     * 文档规定在线状态Key的TTL为300秒。
     */
    public static final long ONLINE_TTL_SECONDS =
            300L;

    public static final String SESSION_STATUS_CLOSED =
            "CLOSED";

    public static final String ONLINE_USERS =
            "online:users";
}
