package com.example.customerservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.List;

public class ChatSessionMetadataUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题长度不能超过100个字符")
    private String title;

    @NotBlank(message = "优先级不能为空")
    @Pattern(
            regexp = "LOW|NORMAL|HIGH|URGENT",
            message = "优先级只支持 LOW、NORMAL、HIGH、URGENT"
    )
    private String priority;

    @Pattern(
            regexp = "ACCOUNT|PAYMENT|TECHNICAL|AFTER_SALES|OTHER",
            message = "分类只支持 ACCOUNT、PAYMENT、TECHNICAL、AFTER_SALES、OTHER"
    )
    private String category;

    @Size(max = 10, message = "标签数量不能超过10个")
    private List<
            @NotBlank(message = "标签不能为空")
            @Size(max = 32, message = "单个标签长度不能超过32个字符")
            String> tags;

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
}
