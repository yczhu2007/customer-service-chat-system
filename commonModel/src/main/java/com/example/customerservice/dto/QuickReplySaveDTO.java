package com.example.customerservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

public class QuickReplySaveDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    @NotBlank(message = "快捷回复标题不能为空")
    @Size(max = 50, message = "快捷回复标题不能超过50个字符")
    private String title;
    @NotBlank(message = "快捷回复内容不能为空")
    @Size(max = 1000, message = "快捷回复内容不能超过1000个字符")
    private String content;
    @Min(value = 0, message = "排序值不能小于0")
    @Max(value = 9999, message = "排序值不能大于9999")
    private Integer sortOrder;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
