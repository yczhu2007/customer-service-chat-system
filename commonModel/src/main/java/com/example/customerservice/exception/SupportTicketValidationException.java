package com.example.customerservice.exception;

/** 可安全返回给客户端的工单业务校验异常。 */
public class SupportTicketValidationException extends IllegalArgumentException {
    public SupportTicketValidationException(String message) {
        super(message);
    }
}
