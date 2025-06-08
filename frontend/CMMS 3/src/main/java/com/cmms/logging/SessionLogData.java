package com.cmms.logging;

import com.cmms.dto.StudentInfo;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Transfer Object (DTO) to hold information for a single session log.
 */
public class SessionLogData {

    private String sessionCode;
    private Instant startTime;
    private Instant endTime;
    private long sessionDurationSeconds;
    private String sessionMode; // e.g., "BLOCK_ALL", "ALLOW_SPECIFIC", "BLOCK_SPECIFIC"
    private int maxStudentCount; // Peak number of concurrent students
    private Set<String> uniqueStudentIds; // All student IDs that joined
    private Map<String, StudentInfo> studentDetailsMap; // Map studentId to details

    private List<String> initialBlockedApps;
    private List<String> initialBlockedWebsites;
    private List<String> initialWebsiteWhitelist;

    private List<String> finalBlockedApps;
    private List<String> finalBlockedWebsites;
    private List<String> finalWebsiteWhitelist;

    // Getters and Setters

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getSessionDurationSeconds() {
        return sessionDurationSeconds;
    }

    public void setSessionDurationSeconds(long sessionDurationSeconds) {
        this.sessionDurationSeconds = sessionDurationSeconds;
    }

    public String getSessionMode() {
        return sessionMode;
    }

    public void setSessionMode(String sessionMode) {
        this.sessionMode = sessionMode;
    }

    public int getMaxStudentCount() {
        return maxStudentCount;
    }

    public void setMaxStudentCount(int maxStudentCount) {
        this.maxStudentCount = maxStudentCount;
    }
    
    public Set<String> getUniqueStudentIds() {
        return uniqueStudentIds;
    }

    public void setUniqueStudentIds(Set<String> uniqueStudentIds) {
        this.uniqueStudentIds = uniqueStudentIds;
    }

    public Map<String, StudentInfo> getStudentDetailsMap() {
        return studentDetailsMap;
    }

    public void setStudentDetailsMap(Map<String, StudentInfo> studentDetailsMap) {
        this.studentDetailsMap = studentDetailsMap;
    }

    public List<String> getInitialBlockedApps() {
        return initialBlockedApps;
    }

    public void setInitialBlockedApps(List<String> initialBlockedApps) {
        this.initialBlockedApps = initialBlockedApps;
    }

    public List<String> getInitialBlockedWebsites() {
        return initialBlockedWebsites;
    }

    public void setInitialBlockedWebsites(List<String> initialBlockedWebsites) {
        this.initialBlockedWebsites = initialBlockedWebsites;
    }

    public List<String> getInitialWebsiteWhitelist() {
        return initialWebsiteWhitelist;
    }

    public void setInitialWebsiteWhitelist(List<String> initialWebsiteWhitelist) {
        this.initialWebsiteWhitelist = initialWebsiteWhitelist;
    }

    public List<String> getFinalBlockedApps() {
        return finalBlockedApps;
    }

    public void setFinalBlockedApps(List<String> finalBlockedApps) {
        this.finalBlockedApps = finalBlockedApps;
    }

    public List<String> getFinalBlockedWebsites() {
        return finalBlockedWebsites;
    }

    public void setFinalBlockedWebsites(List<String> finalBlockedWebsites) {
        this.finalBlockedWebsites = finalBlockedWebsites;
    }

    public List<String> getFinalWebsiteWhitelist() {
        return finalWebsiteWhitelist;
    }

    public void setFinalWebsiteWhitelist(List<String> finalWebsiteWhitelist) {
        this.finalWebsiteWhitelist = finalWebsiteWhitelist;
    }
} 