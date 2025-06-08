package com.cmms.dto;

/**
 * Data Transfer Object holding basic student identification details.
 */
public record StudentInfo(String studentId, String studentName, String rollNo, String studentClass) {
    @Override
    public String toString() {
        // Default display representation if needed elsewhere
        return studentName != null ? studentName : studentId;
    }
} 