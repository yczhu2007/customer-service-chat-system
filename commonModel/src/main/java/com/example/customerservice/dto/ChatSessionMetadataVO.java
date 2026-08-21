package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class ChatSessionMetadataVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String title;
    private String priority;
    private String category;
    private List<String> tags;
    private LocalDateTime metadataUpdatedAt;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public LocalDateTime getMetadataUpdatedAt() {
        return metadataUpdatedAt;
    }

    public void setMetadataUpdatedAt(LocalDateTime metadataUpdatedAt) {
        this.metadataUpdatedAt = metadataUpdatedAt;
    }
}
