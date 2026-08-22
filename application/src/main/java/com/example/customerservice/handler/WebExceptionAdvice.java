package com.example.customerservice.handler;

import com.example.customerservice.common.Result;
import com.example.customerservice.exception.BusinessStateException;
import com.example.customerservice.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.UnauthenticatedException;
import org.apache.shiro.authz.UnauthorizedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * HTTP接口统一异常处理。
 */
@RestControllerAdvice
@Slf4j
public class WebExceptionAdvice {

    @ExceptionHandler(
            MethodArgumentNotValidException.class
    )
    public ResponseEntity<Result<Void>>
    handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        FieldError fieldError =
                exception.getBindingResult()
                        .getFieldErrors()
                        .stream()
                        .findFirst()
                        .orElse(null);

        String message;

        if (fieldError == null) {
            message = "请求参数不正确";
        } else {
            message = fieldError.getDefaultMessage();
        }

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    @ExceptionHandler(
            HttpMessageNotReadableException.class
    )
    public ResponseEntity<Result<Void>>
    handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "请求体不能为空，并且必须是正确的JSON格式"
        );
    }

    @ExceptionHandler(
            IllegalArgumentException.class
    )
    public ResponseEntity<Result<Void>>
    handleIllegalArgumentException(
            IllegalArgumentException exception
    ) {
        log.warn("HTTP请求参数异常", exception);
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "请求参数不正确"
        );
    }

    @ExceptionHandler(
            NotFoundException.class
    )
    public ResponseEntity<Result<Void>>
    handleNotFoundException(
            NotFoundException exception
    ) {
        log.warn("HTTP资源不存在", exception);
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "请求的资源不存在"
        );
    }

    @ExceptionHandler(BusinessStateException.class)
    public ResponseEntity<Result<Void>> handleBusinessStateException(
            BusinessStateException exception
    ) {
        log.warn("业务状态冲突：{}", exception.getMessage());
        return buildResponse(
                HttpStatus.CONFLICT,
                exception.getMessage() == null
                        ? "当前业务状态不允许执行该操作"
                        : exception.getMessage()
        );
    }

    @ExceptionHandler(
            NoResourceFoundException.class
    )
    public ResponseEntity<Result<Void>>
    handleNoResourceFoundException(
            NoResourceFoundException exception
    ) {
        return buildResponse(
                HttpStatus.NOT_FOUND,
                "请求地址不存在"
        );
    }

    @ExceptionHandler(
            UnauthenticatedException.class
    )
    public ResponseEntity<Result<Void>>
    handleUnauthenticatedException(
            UnauthenticatedException exception
    ) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "当前用户尚未登录"
        );
    }

    @ExceptionHandler(
            UnauthorizedException.class
    )
    public ResponseEntity<Result<Void>>
    handleUnauthorizedException(
            UnauthorizedException exception
    ) {
        log.warn("HTTP访问被拒绝", exception);
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "当前操作没有权限"
        );
    }

    @ExceptionHandler(
            ResponseStatusException.class
    )
    public ResponseEntity<Result<Void>>
    handleResponseStatusException(
            ResponseStatusException exception
    ) {
        HttpStatus status =
                HttpStatus.valueOf(
                        exception.getStatusCode()
                                .value()
                );

        log.warn("HTTP状态异常：{}", status, exception);
        return buildResponse(
                status,
                safeStatusMessage(status)
        );
    }

    @ExceptionHandler(
            Exception.class
    )
    public ResponseEntity<Result<Void>>
    handleException(
            Exception exception
    ) {
        log.error(
                "服务器内部异常",
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器内部错误"
        );
    }

    private String safeStatusMessage(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "请求参数不正确";
            case UNAUTHORIZED -> "当前用户尚未登录";
            case FORBIDDEN -> "当前操作没有权限";
            case NOT_FOUND -> "请求的资源不存在";
            default -> status.is5xxServerError() ? "服务器内部错误" : "请求处理失败";
        };
    }

    private ResponseEntity<Result<Void>>
    buildResponse(
            HttpStatus status,
            String message
    ) {
        Result<Void> result =
                Result.failure(
                        status.value(),
                        message
                );

        return ResponseEntity
                .status(status)
                .body(result);
    }
}
