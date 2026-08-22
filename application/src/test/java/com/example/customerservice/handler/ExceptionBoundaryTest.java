package com.example.customerservice.handler;

import com.example.customerservice.common.Result;
import com.example.customerservice.exception.BusinessStateException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionBoundaryTest {

    @Test
    void onlyBusinessStateExceptionIsExposedAsHttpConflict() {
        WebExceptionAdvice advice = new WebExceptionAdvice();

        ResponseEntity<Result<Void>> response = advice.handleBusinessStateException(
                new BusinessStateException("会话正在转接")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("会话正在转接", response.getBody().getMessage());
    }

    @Test
    void internalIllegalStateExceptionIsHiddenAsHttpServerError() {
        WebExceptionAdvice advice = new WebExceptionAdvice();

        ResponseEntity<Result<Void>> response = advice.handleException(
                new IllegalStateException("消息序列化失败")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("服务器内部错误", response.getBody().getMessage());
    }

    @Test
    void httpClientExceptionsDoNotExposeInternalDetails() {
        WebExceptionAdvice advice = new WebExceptionAdvice();

        ResponseEntity<Result<Void>> response = advice.handleIllegalArgumentException(
                new IllegalArgumentException("jdbc:mysql://secret-host:3306/private")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("请求参数不正确", response.getBody().getMessage());
    }

    @Test
    void stompArgumentExceptionDoesNotExposeInternalDetails() {
        StompExceptionHandler handler = new StompExceptionHandler();

        Map<String, Object> response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("redis password=top-secret")
        );

        assertEquals("ERROR", response.get("event"));
        assertEquals("请求参数不正确", response.get("message"));
    }

    @Test
    void stompBusinessConflictKeepsSafeActionableMessage() {
        StompExceptionHandler handler = new StompExceptionHandler();

        Map<String, Object> response = handler.handleBusinessStateException(
                new BusinessStateException("会话正在转接，请稍后重试")
        );

        assertEquals("CONFLICT", response.get("event"));
        assertEquals("会话正在转接，请稍后重试", response.get("message"));
    }
}
