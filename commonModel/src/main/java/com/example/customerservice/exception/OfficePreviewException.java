package com.example.customerservice.exception;

public class OfficePreviewException extends RuntimeException {
    public OfficePreviewException(String message) {
        super(message);
    }

    public OfficePreviewException(String message, Throwable cause) {
        super(message, cause);
    }
}
