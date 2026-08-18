package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** 客服对已结束会话设置归档状态的请求。 */
public class SessionArchiveDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "归档状态不能为空")
    @Size(max = 16, message = "归档状态长度不能超过16个字符")
    private String archiveStatus;

    @Size(max = 255, message = "归档备注不能超过255个字符")
    private String remark;

    public String getArchiveStatus() { return archiveStatus; }
    public void setArchiveStatus(String archiveStatus) { this.archiveStatus = archiveStatus; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
