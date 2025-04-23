package com.cmms.dto;

import java.util.Map;

/**
 * Represents a generic WebSocket message structure.
 * Uses Map<String, Object> for flexibility in payload.
 */
public class WebSocketMessage {
    private String type;
    private Map<String, Object> payload;
    private String requestId; // Optional, for request-response patterns
    private String status; // Optional, for response messages

    // Constructors
    public WebSocketMessage() { }

    public WebSocketMessage(String type, Map<String, Object> payload) {
        this.type = type;
        this.payload = payload;
    }

    public WebSocketMessage(String type, Map<String, Object> payload, String requestId) {
        this.type = type;
        this.payload = payload;
        this.requestId = requestId;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

     @Override
    public String toString() {
        return "WebSocketMessage{" +
               "type='" + type + '\'' +
               ", payload=" + payload +
               ", requestId='" + requestId + '\'' +
               ", status='" + status + '\'' +
               '}';
    }
} 