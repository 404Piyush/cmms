package com.cmms.dto;

import java.util.List;

/**
 * Data Transfer Object for session settings received from the backend.
 */
public class SessionSettings {
    private String sessionType;
    private boolean blockUsb;
    private List<String> websiteBlacklist;
    private List<String> websiteWhitelist;
    private List<String> appBlacklist; // May need adjustment based on how apps are handled

    // Getters and setters (or public fields if preferred)
    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public boolean isBlockUsb() {
        return blockUsb;
    }

    public void setBlockUsb(boolean blockUsb) {
        this.blockUsb = blockUsb;
    }

    public List<String> getWebsiteBlacklist() {
        return websiteBlacklist;
    }

    public void setWebsiteBlacklist(List<String> websiteBlacklist) {
        this.websiteBlacklist = websiteBlacklist;
    }

    public List<String> getWebsiteWhitelist() {
        return websiteWhitelist;
    }

    public void setWebsiteWhitelist(List<String> websiteWhitelist) {
        this.websiteWhitelist = websiteWhitelist;
    }

    public List<String> getAppBlacklist() {
        return appBlacklist;
    }

    public void setAppBlacklist(List<String> appBlacklist) {
        this.appBlacklist = appBlacklist;
    }
    
    @Override
    public String toString() {
        return "SessionSettings{" +
               "sessionType='" + sessionType + '\'' +
               ", blockUsb=" + blockUsb +
               ", websiteBlacklist=" + websiteBlacklist +
               ", websiteWhitelist=" + websiteWhitelist +
               ", appBlacklist=" + appBlacklist +
               '}';
    }
} 