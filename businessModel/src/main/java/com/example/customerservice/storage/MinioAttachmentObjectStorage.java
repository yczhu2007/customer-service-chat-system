package com.example.customerservice.storage;

import com.example.customerservice.config.MinioAttachmentProperties;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.messages.Item;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class MinioAttachmentObjectStorage implements AttachmentObjectStorage {
    private final MinioClient client;
    private final String bucket;

    public MinioAttachmentObjectStorage(MinioClient client, MinioAttachmentProperties properties) {
        this.client = client;
        this.bucket = properties.getMinio().getBucket();
    }

    @Override
    public void put(String objectName, InputStream input, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket).object(objectName).stream(input, size, -1)
                    .contentType(contentType).build());
        } catch (Exception exception) {
            throw new AttachmentStorageException("附件写入对象存储失败", exception);
        }
    }

    @Override
    public InputStream open(String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception exception) {
            throw new AttachmentStorageException("附件读取失败", exception);
        }
    }

    @Override
    public StoredObject stat(String objectName) {
        try {
            var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return new StoredObject(objectName, stat.size(), stat.lastModified().toInstant());
        } catch (Exception exception) {
            throw new AttachmentStorageException("附件对象不存在或无法读取", exception);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
        } catch (Exception exception) {
            throw new AttachmentStorageException("附件删除失败", exception);
        }
    }

    @Override
    public List<StoredObject> list(String prefix) {
        try {
            List<StoredObject> objects = new ArrayList<>();
            Iterable<Result<Item>> results = client.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket).prefix(prefix == null ? "" : prefix).recursive(true).build());
            for (Result<Item> result : results) {
                Item item = result.get();
                objects.add(new StoredObject(item.objectName(), item.size(), item.lastModified().toInstant()));
            }
            return objects;
        } catch (Exception exception) {
            throw new AttachmentStorageException("附件列表读取失败", exception);
        }
    }
}
