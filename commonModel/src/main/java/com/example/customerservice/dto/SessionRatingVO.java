package com.example.customerservice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会话满意度评价结果。 */
public class SessionRatingVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private int rating;
    private String comment;
    private LocalDateTime createTime;

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
