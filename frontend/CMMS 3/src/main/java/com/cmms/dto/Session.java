package com.cmms.dto;

import java.util.Date;

/**
 * Represents an active session's basic information.
 */
public class Session {
    private String sessionCode;
    private Date startTime;
    private boolean active;

    // Constructor
    public Session(String sessionCode) {
        this.sessionCode = sessionCode;
        this.startTime = new Date(); // Set start time on creation
        this.active = true;
    }

    // Getters
    public String getSessionCode() {
        return sessionCode;
    }

    public Date getStartTime() {
        return startTime;
    }

    public boolean isActive() {
        return active;
    }

    // Setter to mark as inactive
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public String toString() {
        return "Session{" +
               "sessionCode='" + sessionCode + '\'' +
               ", startTime=" + startTime +
               ", active=" + active +
               '}';
    }
} 