package com.example.customerservice.dto;

public record AttachmentUpload(String filename, byte[] content) {
    public AttachmentUpload {
        content = content == null ? new byte[0] : content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public long size() {
        return content.length;
    }
}
