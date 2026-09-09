package com.example.customerservice.dto;

import org.springframework.core.io.Resource;

public record AttachmentDownload(String filename, String contentType, long size,
                                 String messageType, Resource resource) { }
