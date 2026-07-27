package com.example.customerservice.common;

import java.time.LocalDateTime;

/**
 * HTTP接口统一返回类型。
 *
 * @param <T> 返回数据类型
 */
public class Result<T> {

    private int code;

    private String message;

    private T data;

    private LocalDateTime time;

    public Result() {
    }

    public Result(
            int code,
            String message,
            T data,
            LocalDateTime time
    ) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.time = time;
    }

    public static <T> Result<T> success(
            int code,
            String message,
            T data
    ) {
        return new Result<>(
                code,
                message,
                data,
                LocalDateTime.now()
        );
    }

    public static <T> Result<T> success(
            T data
    ) {
        return new Result<>(
                200,
                "操作成功",
                data,
                LocalDateTime.now()
        );
    }

    public static Result<Void> successMessage(
            String message
    ) {
        return new Result<>(
                200,
                message,
                null,
                LocalDateTime.now()
        );
    }

    public static Result<Void> failure(
            int code,
            String message
    ) {
        return new Result<>(
                code,
                message,
                null,
                LocalDateTime.now()
        );
    }

    public int getCode() {
        return code;
    }

    public void setCode(
            int code
    ) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(
            String message
    ) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(
            T data
    ) {
        this.data = data;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(
            LocalDateTime time
    ) {
        this.time = time;
    }
}