package com.cmms.service;

import com.cmms.dto.ApiResponse;
// import com.cmms.dto.SessionSettings; // Currently unused
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import okhttp3.*; // Use OkHttp classes

import java.io.IOException;
import java.lang.reflect.Type;
// import java.net.URI; // No longer needed for OkHttp URL
// import java.net.http.HttpClient; // Replaced
// import java.net.http.HttpRequest; // Replaced
// import java.net.http.HttpResponse; // Replaced
import java.util.HashMap;
import java.util.Map;
import java.util.Objects; // For response body handling

/**
 * Service for interacting with the backend REST API using OkHttp.
 */
public class ApiService {

    private final String baseUrl;
    // private final HttpClient httpClient; // Replaced
    private final OkHttpClient okHttpClient; // Use OkHttp client
    private final Gson gson;
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Store the teacher token after session creation/authentication
    private String teacherAuthToken = null;
    // Store the student token after joining a session
    private String studentAuthToken = null;

    public ApiService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        // this.httpClient = HttpClient.newHttpClient(); // Replaced
        this.okHttpClient = new OkHttpClient(); // Initialize OkHttp client
        this.gson = new Gson();
    }

    // --- Public API Methods ---

    /**
     * Creates a new session using OkHttp.
     * @param adminPc The identifier for the admin/teacher PC.
     * @param sessionType The type of session (e.g., BLOCK_APPS).
     * @param blockUsb Whether to block USB drives.
     * @return ApiResponse containing session details and teacher token.
     * @throws IOException If network error occurs.
     * @throws ApiException If API returns an error status.
     */
    public ApiResponse<Object> createSession(String adminPc, String sessionType, boolean blockUsb)
            throws IOException, ApiException { // Removed InterruptedException

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("adminPc", adminPc);
        requestMap.put("sessionType", sessionType);
        requestMap.put("blockUsb", blockUsb);
        String jsonBody = gson.toJson(requestMap);

        RequestBody body = RequestBody.create(jsonBody, JSON);
        Request request = new Request.Builder()
                .url(baseUrl + "session/create") // OkHttp handles URL construction
                .post(body)
                .build();

        // --- Debug Logging Added ---
        System.out.println("--- Sending API Request (OkHttp) ---");
        System.out.println("Method: " + request.method());
        System.out.println("URL: " + request.url());
        System.out.println("Headers: " + request.headers()); 
        System.out.println("Body JSON: " + jsonBody);
        // --- End Debug Logging ---
        
        // Use try-with-resources for OkHttp response
        try (Response response = okHttpClient.newCall(request).execute()) { 
            Type responseType = new TypeToken<ApiResponse<Object>>() {}.getType();
            ApiResponse<Object> apiResponse = handleOkHttpResponse(response, responseType);

            // Store the token upon successful creation
            if (apiResponse != null && apiResponse.getToken() != null) {
                this.teacherAuthToken = apiResponse.getToken();
                System.out.println("Stored teacher token: " + this.teacherAuthToken);
            }

            return apiResponse;
        }
    }

    /**
     * Allows a student to join a session using OkHttp.
     */
    // Updated to accept Map<String, String> for student details
    public ApiResponse<Object> joinSession(String sessionCode, Map<String, String> studentDetails)
            throws IOException, ApiException {

        // Convert the map directly to JSON
        String jsonBody = gson.toJson(studentDetails);

        RequestBody body = RequestBody.create(jsonBody, JSON);
        
        // Construct URL: /api/session/:code/student/join
        String url = baseUrl + "session/" + sessionCode + "/student/join"; 

        Request request = new Request.Builder()
                .url(url)
                .post(body) // Backend expects student details in the body
                .build();

        System.out.println("--- Sending API Request (OkHttp) [Join Session] ---"); // Clarified log
        System.out.println("Method: " + request.method());
        System.out.println("URL: " + request.url());
        System.out.println("Headers: " + request.headers());
        System.out.println("Body JSON: " + jsonBody);

        try (Response response = okHttpClient.newCall(request).execute()) {
            Type responseType = new TypeToken<ApiResponse<Object>>() {}.getType();
            // Need to handle potential errors during response body processing
            ApiResponse<Object> apiResponse = handleOkHttpResponse(response, responseType);

            // Store the student token upon successful join
            if (apiResponse != null && apiResponse.getToken() != null) {
                 this.studentAuthToken = apiResponse.getToken();
                 System.out.println("Stored student token: " + this.studentAuthToken); // Log student token
            }

            return apiResponse;
        }
    }

    /**
     * Ends the session owned by the authenticated teacher using OkHttp.
     * Requires the teacher token to be set via createSession first.
     * @param sessionCode The code of the session to end.
     * @return ApiResponse indicating success or failure.
     * @throws IOException If network error occurs.
     * @throws ApiException If API returns an error status or no token is available.
     */
    public ApiResponse<Object> endSession(String sessionCode)
            throws IOException, ApiException {

        if (this.teacherAuthToken == null) {
            throw new ApiException("Cannot end session: Teacher not authenticated (token missing).");
        }

        // POST with no body is okay for OkHttp
        RequestBody body = RequestBody.create(new byte[0], null); // Empty body

        Request request = new Request.Builder()
                .url(baseUrl + "session/" + sessionCode + "/end")
                .header("Authorization", "Bearer " + this.teacherAuthToken)
                .post(body) // Send POST request
                .build();
                
        System.out.println("--- Sending API Request (OkHttp) ---");
        System.out.println("Method: " + request.method());
        System.out.println("URL: " + request.url());
        System.out.println("Headers: " + request.headers());

        try (Response response = okHttpClient.newCall(request).execute()) {
            Type responseType = new TypeToken<ApiResponse<Object>>() {}.getType();
            return handleOkHttpResponse(response, responseType);
        }
    }

    /**
     * Reports a blocked application attempt by the student.
     * Requires the student token to be set via joinSession first.
     * @param studentId The ID of the student making the report.
     * @param appName The name of the blocked application.
     * @throws IOException If network error occurs.
     * @throws ApiException If API returns an error status or no student token is available.
     */
    public void reportBlockedAppAttempt(String studentId, String appName)
            throws IOException, ApiException {

        if (this.studentAuthToken == null) {
            throw new ApiException("Cannot report blocked app: Student not authenticated (token missing).");
        }

        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("studentId", studentId);
        requestMap.put("appName", appName);
        String jsonBody = gson.toJson(requestMap);

        RequestBody body = RequestBody.create(jsonBody, JSON);

        Request request = new Request.Builder()
                .url(baseUrl + "student/report-block") // Endpoint for reporting blocked apps
                .header("Authorization", "Bearer " + this.studentAuthToken)
                .post(body)
                .build();

        System.out.println("--- Sending API Request (OkHttp) [Report Blocked App] ---");
        System.out.println("Method: " + request.method());
        System.out.println("URL: " + request.url());
        System.out.println("Headers: " + request.headers());
        System.out.println("Body JSON: " + jsonBody);

        try (Response response = okHttpClient.newCall(request).execute()) {
            // Use the existing handler, but expect no specific data back (ApiResponse<Object>)
            // We don't need the return value here, just need to check for exceptions.
            handleOkHttpResponse(response, new TypeToken<ApiResponse<Object>>() {}.getType());
            System.out.println("Successfully reported blocked app: " + appName);
        } catch (ApiException e) {
            System.err.println("API Error reporting blocked app: " + e.getMessage());
            throw e; // Re-throw API exceptions
        } catch (IOException e) {
            System.err.println("Network Error reporting blocked app: " + e.getMessage());
            throw e; // Re-throw IO exceptions
        }
    }

    // --- Helper Methods ---

    private <T> ApiResponse<T> handleOkHttpResponse(Response response, Type responseType)
            throws IOException, ApiException { // Added IOException here
        // Use try-with-resources for response body to ensure it's closed
        try (ResponseBody responseBodyObj = response.body()) { 
            String responseBodyString = Objects.requireNonNull(responseBodyObj).string();
            System.out.println("API Response Status: " + response.code());
            System.out.println("API Response Body: " + responseBodyString);
    
            ApiResponse<T> apiResponse = null;
            try {
                // Handle empty body case for success scenarios (e.g., 204 No Content)
                if (!responseBodyString.isEmpty()) {
                     apiResponse = gson.fromJson(responseBodyString, responseType);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse API response: " + e.getMessage());
                // Fallthrough if parsing fails but status code is OK?
            }
    
            if (response.isSuccessful()) { // Checks for 2xx status codes
                 // If response body was expected but couldn't be parsed, it's an issue
                 if (!responseBodyString.isEmpty() && apiResponse == null && response.code() != 204) { 
                     throw new ApiException("API request succeeded (" + response.code() + ") but response body was invalid.", response.code(), responseBodyString);
                 }
                 // If body was empty (like for 204) or parsing succeeded, return the parsed object (which might be null for 204)
                 return apiResponse; 
            } else {
                // Handle error responses
                String errorMessage = "API request failed with status code " + response.code();
                if (apiResponse != null && apiResponse.getMessage() != null) {
                    errorMessage += ": " + apiResponse.getMessage();
                } else if (!responseBodyString.isEmpty()) {
                     errorMessage += " - " + responseBodyString; // Include raw body if parsing failed
                }
                throw new ApiException(errorMessage, response.code(), responseBodyString);
            }
        }
    }

    // --- Custom Exception ---

    public static class ApiException extends Exception {
        private final int statusCode;
        private final String responseBody;

        public ApiException(String message, int statusCode, String responseBody) {
            super(message);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
         public ApiException(String message) {
            super(message);
            this.statusCode = -1; // Indicate not from HTTP response
            this.responseBody = null;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }

    // --- Token Management (Optional) ---
    public void clearTeacherToken() {
        this.teacherAuthToken = null;
    }

     // Added getter needed by TeacherDashboardController
     public String getTeacherAuthToken() {
        return teacherAuthToken;
     }

     // Potentially add method to set token if needed elsewhere
     // public void setTeacherAuthToken(String token) {
     //    this.teacherAuthToken = token;
     // }

     // Added getter for student token (optional, depends on usage)
     public String getStudentAuthToken() {
          return studentAuthToken;
     }
} 