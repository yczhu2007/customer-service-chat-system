package com.example.customerservice.service;

import java.io.InputStream;

public interface OfficePreviewConverter {
    byte[] convertToPdf(String sourceName, InputStream source);
}
