package com.example.customerservice.dto;

import jakarta.validation.constraints.Size;

import java.io.Serializable;

/** 客服独立保存会话归档备注的请求。 */
public class SessionArchiveRemarkDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 255, message = "归档备注不能超过255个字符")
    private String remark;

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
