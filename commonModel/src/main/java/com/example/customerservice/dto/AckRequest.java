package com.example.customerservice.dto;


/**
 * 客户端消息确认请求
 */
public class AckRequest {

    private String messageId;


    public String getMessageId() {

        return messageId;
    }


    public void setMessageId(
            String messageId
    ) {

        this.messageId =
                messageId;
    }
}