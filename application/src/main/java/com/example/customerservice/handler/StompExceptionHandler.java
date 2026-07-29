package com.example.customerservice.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


/**
 * 统一处理STOMP消息业务中的异常
 */
@ControllerAdvice
@Slf4j
public class StompExceptionHandler {

    /**
     * 处理主动抛出的业务参数异常
     *
     * 例如：
     * 会话不存在
     * 会话已经结束
     * 当前用户无权查看
     * sessionId为空
     */
    @MessageExceptionHandler(IllegalArgumentException.class)
    @SendToUser(
            value = "/queue/errors",
            broadcast = false
    )
    public Map<String, Object> handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {

        Map<String, Object> response =
                new HashMap<>();


        response.put(
                "event",
                "ERROR"
        );

        response.put(
                "message",
                exception.getMessage()
        );

        response.put(
                "time",
                LocalDateTime.now()
        );


        log.info(
                "STOMP业务异常："
                        + exception.getMessage()
        );


        return response;
    }


    /**
     * 处理没有预料到的系统异常
     *
     * 不把完整异常内容发送给客户端，
     * 避免暴露服务器内部信息。
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser(
            value = "/queue/errors",
            broadcast = false
    )
    public Map<String, Object> handleOtherException(
            Exception exception
    ) {

        Map<String, Object> response =
                new HashMap<>();


        response.put(
                "event",
                "ERROR"
        );

        response.put(
                "message",
                "服务器处理消息失败"
        );

        response.put(
                "time",
                LocalDateTime.now()
        );


        /*
         * 详细异常只保留在服务器控制台，
         * 不发送给浏览器。
         */
        log.error(
                "STOMP系统异常："
                        + exception.getMessage()
        );


        exception.printStackTrace();


        return response;
    }
}
