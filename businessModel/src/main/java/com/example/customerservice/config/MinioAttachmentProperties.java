package com.example.customerservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.chat.attachment")
public class MinioAttachmentProperties {
    private final Minio minio = new Minio();
    private long orphanGracePeriodSeconds = 3600;
    private final Migration migration = new Migration();
    private final Preview preview = new Preview();

    public Minio getMinio() { return minio; }
    public long getOrphanGracePeriodSeconds() { return orphanGracePeriodSeconds; }
    public void setOrphanGracePeriodSeconds(long value) { orphanGracePeriodSeconds = value; }
    public Migration getMigration() { return migration; }
    public Preview getPreview() { return preview; }

    public static class Minio {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "chat-attachments";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String value) { endpoint = value; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String value) { accessKey = value; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String value) { secretKey = value; }
        public String getBucket() { return bucket; }
        public void setBucket(String value) { bucket = value; }
    }

    public static class Migration {
        private boolean enabled;
        private Path legacyStoragePath = Path.of("./data/chat-attachments");

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public Path getLegacyStoragePath() { return legacyStoragePath; }
        public void setLegacyStoragePath(Path value) { legacyStoragePath = value; }
    }

    public static class Preview {
        private String command = "soffice";
        private long timeoutSeconds = 30;
        private long maxOutputBytes = 20L * 1024 * 1024;
        private Path tempRoot;

        public String getCommand() { return command; }
        public void setCommand(String value) { command = value; }
        public long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(long value) { timeoutSeconds = value; }
        public long getMaxOutputBytes() { return maxOutputBytes; }
        public void setMaxOutputBytes(long value) { maxOutputBytes = value; }
        public Path getTempRoot() { return tempRoot; }
        public void setTempRoot(Path value) { tempRoot = value; }
    }
}
