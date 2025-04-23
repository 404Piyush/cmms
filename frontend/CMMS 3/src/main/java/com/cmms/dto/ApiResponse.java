package com.cmms.dto;

import com.cmms.dto.SessionSettings;
import com.google.gson.annotations.SerializedName;

import java.util.List; // Import List

/**
 * Generic structure for API responses.
 * Specific data can be nested within the `payload` field or directly.
 */
public class ApiResponse<T> { // Use generics for flexible payload
    private String message;
    private T payload; // Generic payload

    // Example specific fields for known responses (adjust as needed)
    private String sessionCode;
    private String adminPc;
    @SerializedName("student_pc")
    private String studentId;
    private String token;
    private SessionSettings settings; // For join responses

    // Added field for validation errors
    private List<ApiError> errors;

    // Getters and setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public String getSessionCode() {
        return sessionCode;
    }

    public void setSessionCode(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    public String getAdminPc() {
        return adminPc;
    }

    public void setAdminPc(String adminPc) {
        this.adminPc = adminPc;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public SessionSettings getSettings() {
        return settings;
    }

    public void setSettings(SessionSettings settings) {
        this.settings = settings;
    }

    // Added getter for errors
    public List<ApiError> getErrors() {
        return errors;
    }

    // Added setter for errors (optional, depending on backend serialization)
    public void setErrors(List<ApiError> errors) {
        this.errors = errors;
    }

    @Override
    public String toString() {
        return "ApiResponse{" +
               "message='" + message + '\'' +
               ", payload=" + payload +
               ", sessionCode='" + sessionCode + '\'' +
               ", adminPc='" + adminPc + '\'' +
               ", studentId='" + studentId + '\'' +
               ", token='" + token + '\'' +
               ", settings=" + settings +
               ", errors=" + errors +
               '}';
    }
    
    // Inner class to represent individual validation errors
    public static class ApiError {
        private String type;
        private String value;
        private String msg;
        private String path;
        private String location;

        // Getters (Setters might not be needed if only deserializing)
        public String getType() { return type; }
        public String getValue() { return value; }
        public String getMsg() { return msg; }
        public String getPath() { return path; }
        public String getLocation() { return location; }

        @Override
        public String toString() {
            return "ApiError{" +
                   "type='" + type + '\'' +
                   ", value='" + value + '\'' +
                   ", msg='" + msg + '\'' +
                   ", path='" + path + '\'' +
                   ", location='" + location + '\'' +
                   '}';
        }
    }
} 