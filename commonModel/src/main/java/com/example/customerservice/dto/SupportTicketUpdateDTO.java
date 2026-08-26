package com.example.customerservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class SupportTicketUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "工单状态不能为空")
    @Pattern(regexp = "OPEN|IN_PROGRESS|WAITING_USER|RESOLVED", message = "工单状态不合法")
    private String status;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 1000, message = "问题描述长度不能超过1000个字符")
    private String description;

    @Size(max = 1000, message = "处理结果长度不能超过1000个字符")
    private String resolution;

    @NotNull(message = "工单版本不能为空")
    @Min(value = 0, message = "工单版本不能小于0")
    private Integer version;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
