package com.example.customerservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * 查询聊天历史时客户端提交的数据
 */
public class HistoryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "sessionId不能为空")
    @Size(max = 64, message = "sessionId长度不能超过64个字符")
    private String sessionId;

    @Size(max = 64, message = "历史请求标识长度不能超过64个字符")
    private String requestId;

    @Size(max = 64, message = "历史消息游标长度不能超过64个字符")
    private String beforeMessageId;

    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 20;


    public String getSessionId() {
        return sessionId;
    }


    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }


    public String getBeforeMessageId() {
        return beforeMessageId;
    }

    public void setBeforeMessageId(String beforeMessageId) {
        this.beforeMessageId = beforeMessageId;
    }


    public Integer getPageSize() {
        return pageSize;
    }


    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
