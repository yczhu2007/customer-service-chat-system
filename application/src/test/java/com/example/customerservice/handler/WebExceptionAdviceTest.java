package com.example.customerservice.handler;

import com.example.customerservice.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebExceptionAdviceTest {

    @Test
    void preservesTheGenericLoginFailureMessageForInvalidCredentials() {
        WebExceptionAdvice advice = new WebExceptionAdvice();

        ResponseEntity<Result<Void>> response = advice.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误")
        );

        assertEquals("用户名或密码错误", response.getBody().getMessage());
    }
}
