package com.example.customerservice.constant;


public class RedisConstants {

    public static final String AGENT_LOAD = "agent:load";
    public static final int AGENT_MAX_CONCURRENCY = 5;
    public static final String QUEUE_PENDING = "queue:pending";
    public static final String CHAT_ASSIGN_LOCK = "chat:assign:lock:";
    public static final long CHAT_ASSIGN_LOCK_TTL_SECONDS = 30L;
    public static final String SESSION_MSG = "session:msg:";
    public static final String CLIENT_MSG_DEDUP = "client:msg:dedup:";
    /**
     * 异步落库失败的消息。
     *
     * 类型：Hash
     * field：服务端消息ID
     * value：完整消息JSON
     */
    public static final String PERSIST_FAILED =
            "persist:failed";
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
