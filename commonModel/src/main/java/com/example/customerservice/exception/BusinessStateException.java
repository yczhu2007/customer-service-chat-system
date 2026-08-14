package com.example.customerservice.exception;

/** 当前业务状态不允许继续操作。 */
public class BusinessStateException extends RuntimeException {

    public BusinessStateException(String message) {
        super(message);
    }

    public BusinessStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
