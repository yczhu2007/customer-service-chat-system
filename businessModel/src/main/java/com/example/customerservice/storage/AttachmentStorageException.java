package com.example.customerservice.storage;

public class AttachmentStorageException extends RuntimeException {
    public AttachmentStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public AttachmentStorageException(String message) {
        super(message);
    }
}
