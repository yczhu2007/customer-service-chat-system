package com.example.customerservice.storage;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

public interface AttachmentObjectStorage {
    void put(String objectName, InputStream input, long size, String contentType);

    InputStream open(String objectName);

    StoredObject stat(String objectName);

    void delete(String objectName);

    List<StoredObject> list(String prefix);

    record StoredObject(String objectName, long size, Instant lastModified) { }
}
