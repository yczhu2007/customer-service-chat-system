package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class SupportTicketCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "问题描述不能为空")
    @Size(max = 1000, message = "问题描述长度不能超过1000个字符")
    private String description;

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
