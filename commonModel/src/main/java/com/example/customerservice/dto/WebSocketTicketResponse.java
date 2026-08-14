package com.example.customerservice.dto;

import java.io.Serializable;

/** WebSocket 握手使用的一次性短期票据。 */
public class WebSocketTicketResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String ticket;
    private long expiresInSeconds;

    public WebSocketTicketResponse() {
    }

    public WebSocketTicketResponse(String ticket, long expiresInSeconds) {
        this.ticket = ticket;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
