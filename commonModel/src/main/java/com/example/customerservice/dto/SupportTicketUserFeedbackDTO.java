package com.example.customerservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.io.Serializable;

/** 用户对已解决工单的确认或继续处理请求。 */
public class SupportTicketUserFeedbackDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "工单反馈操作不能为空")
    @Pattern(regexp = "CONFIRM|REOPEN", message = "工单反馈操作不合法")
    private String action;

    @Min(value = 0, message = "工单版本不合法")
    private Integer version;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
