package com.cmms.logging;

import com.cmms.dto.SessionSettings;
import com.cmms.dto.StudentInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service responsible for logging session details to a file.
 * Uses a SessionLogData object to hold the state of the current session being logged.
 */
public class SessionLoggerService {

    private static final Logger log = LoggerFactory.getLogger(SessionLoggerService.class);
    private static final String BASE_LOG_DIR = System.getProperty("user.home") + "/Documents/CMMS_Session_Logs";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
                                                                                .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
                                                                              .withZone(ZoneId.systemDefault());

    // Holds all data for the session currently being logged. Null if no session is active.
    private SessionLogData currentSessionData;
    // Tracks actively connected students for max count calculation. Cleared on session start.
    private final Set<String> currentConnectedStudents = new HashSet<>();
    private boolean sessionActive = false; // Flag indicating if logging is currently in progress

    /**
     * Starts logging for a new session. Initializes the SessionLogData object.
     * If a session is already active, it logs a warning and attempts to end it before starting the new one.
     *
     * @param sessionCode The unique code for the session.
     * @param initialSettings The initial settings applied at the start of the session (can be null).
     */
    public synchronized void startSession(String sessionCode, SessionSettings initialSettings) {
        if (sessionActive) {
            log.warn("startSession called while a session is already active ({}). Ending previous one first.",
                     currentSessionData != null ? currentSessionData.getSessionCode() : "UNKNOWN");
            endSession(); // Attempt to end the previous session cleanly
        }

        log.info("Starting log for session: {}", sessionCode);
        currentSessionData = new SessionLogData(); // Create the holder for session data
        currentSessionData.setSessionCode(sessionCode);
        currentSessionData.setStartTime(Instant.now());
        currentSessionData.setStudentDetailsMap(new HashMap<>()); // Initialize map

        // Record initial settings from the provided DTO
        if (initialSettings != null) {
            currentSessionData.setSessionMode(initialSettings.getSessionType());
            currentSessionData.setInitialBlockedApps(copyList(initialSettings.getAppBlacklist()));
            currentSessionData.setInitialBlockedWebsites(copyList(initialSettings.getWebsiteBlacklist()));
            currentSessionData.setInitialWebsiteWhitelist(copyList(initialSettings.getWebsiteWhitelist()));

            // Initialize final settings to initial ones; they will be updated if changes occur via settingsUpdated()
            currentSessionData.setFinalBlockedApps(copyList(initialSettings.getAppBlacklist()));
            currentSessionData.setFinalBlockedWebsites(copyList(initialSettings.getWebsiteBlacklist()));
            currentSessionData.setFinalWebsiteWhitelist(copyList(initialSettings.getWebsiteWhitelist()));
        } else {
            log.warn("Initial settings for session {} were null. Log may be incomplete.", sessionCode);
            // Initialize with empty lists to avoid NullPointerExceptions later
             currentSessionData.setInitialBlockedApps(Collections.emptyList());
             currentSessionData.setInitialBlockedWebsites(Collections.emptyList());
             currentSessionData.setInitialWebsiteWhitelist(Collections.emptyList());
             currentSessionData.setFinalBlockedApps(Collections.emptyList());
             currentSessionData.setFinalBlockedWebsites(Collections.emptyList());
             currentSessionData.setFinalWebsiteWhitelist(Collections.emptyList());
        }


        currentConnectedStudents.clear(); // Reset for the new session
        currentSessionData.setMaxStudentCount(0); // Initialize max count
        currentSessionData.setUniqueStudentIds(new HashSet<>()); // Initialize set for all unique students
        sessionActive = true;
    }

    /**
     * Records that a student has joined the session. Updates the current count and max count.
     * Adds the student ID to the set of unique students for the session and stores their details.
     *
     * @param student The StudentInfo object for the joining student.
     */
    public synchronized void studentJoined(StudentInfo student) {
        if (!sessionActive || currentSessionData == null) {
             log.warn("studentJoined called but no active session log.");
             return;
        }
        if (student != null && student.studentId() != null && !student.studentId().isBlank()) {
            String studentId = student.studentId();
            // Add/update student details in the map
            currentSessionData.getStudentDetailsMap().put(studentId, student);
            
            // Add to unique set for the entire session log (if not already present)
            if (currentSessionData.getUniqueStudentIds() == null) { // Defensive init
                 currentSessionData.setUniqueStudentIds(new HashSet<>());
            }
            currentSessionData.getUniqueStudentIds().add(studentId);

            // Add to currently connected set and update max count if it's a new connection
            if (currentConnectedStudents.add(studentId)) {
                 currentSessionData.setMaxStudentCount(Math.max(currentSessionData.getMaxStudentCount(), currentConnectedStudents.size()));
                 log.debug("Student joined: {}. Current count: {}. Max count: {}. Unique total: {}",
                           studentId, currentConnectedStudents.size(), currentSessionData.getMaxStudentCount(), currentSessionData.getUniqueStudentIds().size());
                // Log join event to individual student log
                logStudentActivity(student, "Joined session."); 
            } else {
                 log.trace("Student {} reconnected or event duplicated, not incrementing current count.", studentId);
            }
        } else {
            log.warn("studentJoined called with null or invalid StudentInfo.");
        }
    }

    /**
     * Records that a student has left the session. Decrements the current count.
     * The student remains in the unique student ID set for the log.
     *
     * @param studentId Unique identifier for the student.
     */
    public synchronized void studentLeft(String studentId) {
        if (!sessionActive || currentSessionData == null) {
            log.warn("studentLeft called but no active session log.");
            return;
        }
         if (studentId != null && !studentId.isBlank()) {
             // Remove from the set tracking *currently* connected students
             if (currentConnectedStudents.remove(studentId)) {
                 log.debug("Student left: {}. Current count: {}", studentId, currentConnectedStudents.size());
                 // Log leave event to individual student log
                 StudentInfo student = currentSessionData.getStudentDetailsMap().get(studentId); // Get details
                 if (student != null) { // Log only if we have details
                     logStudentActivity(student, "Left session.");
                 }
             } else {
                 log.trace("Student {} left but was not in the currently connected set.", studentId);
             }
             // Note: We DO NOT remove from currentSessionData.getUniqueStudentIds() or studentDetailsMap
         } else {
             log.warn("studentLeft called with null or blank studentId.");
         }
    }
    
    /**
     * Logs a specific activity for a student to their individual log file.
     *
     * @param student The StudentInfo object for the student.
     * @param activityMessage The message describing the activity.
     */
    public synchronized void logStudentActivity(StudentInfo student, String activityMessage) {
         if (!sessionActive || currentSessionData == null) {
            log.warn("logStudentActivity called but no active session log.");
            return;
        }
        if (student == null || student.studentId() == null || student.studentId().isBlank()) {
             log.warn("logStudentActivity called with invalid student info.");
             return;
        }
        if (currentSessionData.getSessionCode() == null) {
            log.warn("logStudentActivity called but session code is null.");
            return;
        }
        
        String studentId = student.studentId();
        String studentName = student.studentName() != null ? student.studentName() : "UnknownName";
        String studentRoll = student.rollNo() != null ? student.rollNo() : "N/A";
        String studentClass = student.studentClass() != null ? student.studentClass() : "N/A";
        String sessionCode = currentSessionData.getSessionCode();

        // Sanitize student name for filename (replace non-alphanumeric)
        String sanitizedName = studentName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        // Construct file path: BASE_LOG_DIR / sessionCode / students / studentId_studentName.log
        Path studentLogDirPath = Paths.get(BASE_LOG_DIR, sessionCode, "students");
        Path studentLogFilePath = studentLogDirPath.resolve(studentId + "_" + sanitizedName + ".log");

        try {
            Files.createDirectories(studentLogDirPath); // Ensure students subdirectory exists
            
            boolean fileExists = Files.exists(studentLogFilePath);
            
            // Use try-with-resources for BufferedWriter
            try (BufferedWriter writer = Files.newBufferedWriter(studentLogFilePath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                        
                // Write header if file is newly created
                if (!fileExists) {
                    writer.write("CMMS Student Activity Log");
                    writer.newLine();
                    writer.write("=========================");
                    writer.newLine();
                    writer.write("Session Code: " + sessionCode);
                    writer.newLine();
                    writer.write("Student ID:   " + studentId);
                    writer.newLine();
                    writer.write("Student Name: " + studentName);
                    writer.newLine();
                    writer.write("Roll Number:  " + studentRoll);
                    writer.newLine();
                    writer.write("Class/Batch:  " + studentClass);
                    writer.newLine();
                    writer.write("=========================");
                    writer.newLine();
                    writer.newLine(); // Add blank line before logs start
                }
                
                // Write the timestamped activity message
                String timestamp = TIME_FORMATTER.format(Instant.now());
                writer.write(String.format("[%s] %s", timestamp, activityMessage));
                writer.newLine();
            }
            
            // log.trace("Successfully logged activity for student {}", studentId); // Optional trace log

        } catch (IOException e) {
            log.error("Failed to write to student log file: {}", studentLogFilePath, e);
        } catch (Exception e) {
            log.error("Unexpected error writing student log file: {}", studentLogFilePath, e);
        }
    }

    /**
     * Updates the log with the initial settings, typically called after startSession
     * once the settings are fetched. Does not overwrite final settings.
     *
     * @param initialSettings The settings fetched shortly after session start.
     */
    public synchronized void updateInitialSettings(SessionSettings initialSettings) {
        if (!sessionActive || currentSessionData == null) {
            log.warn("updateInitialSettings called but no active session log.");
            return;
        }
        if (initialSettings == null) {
            log.warn("updateInitialSettings called with null initialSettings for session {}. No changes recorded.", currentSessionData.getSessionCode());
            return;
        }

        log.debug("Updating initial settings in log for session {}", currentSessionData.getSessionCode());
        // Only update the 'initial' fields
        currentSessionData.setSessionMode(initialSettings.getSessionType()); // Might update mode if it wasn't set at start
        currentSessionData.setInitialBlockedApps(copyList(initialSettings.getAppBlacklist()));
        currentSessionData.setInitialBlockedWebsites(copyList(initialSettings.getWebsiteBlacklist()));
        currentSessionData.setInitialWebsiteWhitelist(copyList(initialSettings.getWebsiteWhitelist()));

        // If final settings haven't been set by a specific update yet, sync them with initial
        if (currentSessionData.getFinalBlockedApps().isEmpty() &&
            currentSessionData.getFinalBlockedWebsites().isEmpty() &&
            currentSessionData.getFinalWebsiteWhitelist().isEmpty()) {
                log.debug("Synchronizing final settings with initial settings as no specific update has occurred yet.");
                currentSessionData.setFinalBlockedApps(copyList(initialSettings.getAppBlacklist()));
                currentSessionData.setFinalBlockedWebsites(copyList(initialSettings.getWebsiteBlacklist()));
                currentSessionData.setFinalWebsiteWhitelist(copyList(initialSettings.getWebsiteWhitelist()));
        }
    }

    /**
     * Updates the log with the latest applied session settings. Overwrites the 'final' settings fields.
     * Should be called whenever settings are actively changed *during* the session.
     *
     * @param newSettings The new settings DTO that was just applied.
     */
    public synchronized void settingsUpdated(SessionSettings newSettings) {
        if (!sessionActive || currentSessionData == null) {
            log.warn("settingsUpdated called but no active session log.");
            return;
        }
        if (newSettings == null) {
             log.warn("settingsUpdated called with null newSettings for session {}. No changes recorded.", currentSessionData.getSessionCode());
             return;
        }

        log.debug("Updating final settings in log for session {}", currentSessionData.getSessionCode());
        // Overwrite the 'final' settings fields in the SessionLogData object
        currentSessionData.setSessionMode(newSettings.getSessionType()); // Update mode as well
        currentSessionData.setFinalBlockedApps(copyList(newSettings.getAppBlacklist()));
        currentSessionData.setFinalBlockedWebsites(copyList(newSettings.getWebsiteBlacklist()));
        currentSessionData.setFinalWebsiteWhitelist(copyList(newSettings.getWebsiteWhitelist()));
    }

    /**
     * Logs a generic, timestamped event message to the main session log file.
     * Useful for logging system actions like USB blocking/unblocking.
     *
     * @param eventMessage The message to log.
     */
    public synchronized void logGenericEvent(String eventMessage) {
        if (!sessionActive || currentSessionData == null) {
            log.warn("logGenericEvent called but no active session log.");
            return;
        }
        if (eventMessage == null || eventMessage.isBlank()) {
            log.warn("logGenericEvent called with empty message.");
            return;
        }
        if (currentSessionData.getSessionCode() == null) {
            log.warn("logGenericEvent called but session code is null.");
            return;
        }
        
        String sessionCode = currentSessionData.getSessionCode();
        Path logFilePath = Paths.get(BASE_LOG_DIR, sessionCode, "session_summary.log");

        try {
             // Ensure the directory exists (though startSession likely created it)
             Files.createDirectories(logFilePath.getParent());
             
             // Append the generic event message with a timestamp
             String timestamp = TIME_FORMATTER.format(Instant.now());
             String logLine = String.format("[%s] [EVENT] %s", timestamp, eventMessage);
             
             Files.writeString(logFilePath, logLine + System.lineSeparator(), StandardCharsets.UTF_8,
                     StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                     
        } catch (IOException e) {
            log.error("Failed to write generic event to log file: {}", logFilePath, e);
        } catch (Exception e) {
            log.error("Unexpected error writing generic event to log file: {}", logFilePath, e);
        }
    }

    /**
     * Ends the current session logging. Calculates final duration, records unique student IDs,
     * triggers writing the log file, and resets the service state.
     */
    public synchronized void endSession() {
        if (!sessionActive || currentSessionData == null) {
            log.info("endSession called but no active session to end.");
            return;
        }

        log.info("Ending log for session: {}", currentSessionData.getSessionCode());
        Instant endTime = Instant.now();
        currentSessionData.setEndTime(endTime);
        currentSessionData.setSessionDurationSeconds(
            Duration.between(currentSessionData.getStartTime(), endTime).getSeconds()
        );
        // The uniqueStudentIds set has been populated throughout the session by studentJoined()

        writeLogToFile(); // Write the accumulated data

        // Reset state for the next session
        currentSessionData = null;
        currentConnectedStudents.clear();
        sessionActive = false;
        log.info("Session log ended and state reset.");
    }

    /**
     * Writes the completed SessionLogData to a structured log file.
     * Creates directories if they don't exist.
     */
    private void writeLogToFile() {
        if (currentSessionData == null) {
            log.error("Attempted to write log file, but currentSessionData is null. This should not happen if called from endSession().");
            return;
        }
        if (currentSessionData.getSessionCode() == null || currentSessionData.getSessionCode().isBlank()) {
            log.error("Attempted to write log file, but session code is missing. Skipping write.");
            return;
        }


        // Use session code as a sub-directory name for organization
        Path logDirPath = Paths.get(BASE_LOG_DIR, currentSessionData.getSessionCode());
        Path logFilePath = logDirPath.resolve("session_summary.log"); // Standardized file name

        try {
            log.info("Attempting to write session log to: {}", logFilePath);
            Files.createDirectories(logDirPath); // Ensure directory exists

            String logContent = formatLogContent(currentSessionData); // Format the data into a string

            // Write the log content to the file, overwriting if it exists
            Files.writeString(logFilePath, logContent, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Successfully wrote session log: {}", logFilePath);

        } catch (IOException e) {
            log.error("Failed to create directory or write session log file: {}", logFilePath, e);
        } catch (Exception e) {
            // Catch unexpected errors during formatting or writing
            log.error("An unexpected error occurred while writing session log for session {}: {}",
                      currentSessionData.getSessionCode(), logFilePath, e);
        }
    }

    /**
     * Formats the SessionLogData object into a human-readable string for the log file.
     *
     * @param data The SessionLogData to format.
     * @return A formatted string representation of the session log.
     */
    private String formatLogContent(SessionLogData data) {
        if (data == null) return "Error: SessionLogData was null during formatting.";

        StringBuilder sb = new StringBuilder();
        sb.append("=================================================\n");
        sb.append("           CMMS Session Log Summary\n");
        sb.append("=================================================\n\n");

        sb.append("Session Code: ").append(data.getSessionCode() != null ? data.getSessionCode() : "N/A").append("\n");
        sb.append("Start Time:   ").append(data.getStartTime() != null ? DATE_TIME_FORMATTER.format(data.getStartTime()) : "N/A").append("\n");
        sb.append("End Time:     ").append(data.getEndTime() != null ? DATE_TIME_FORMATTER.format(data.getEndTime()) : "N/A").append("\n");
        sb.append("Duration:     ").append(data.getEndTime() != null && data.getStartTime() != null ? formatDuration(data.getSessionDurationSeconds()) : "N/A").append("\n");
        sb.append("Session Mode: ").append(data.getSessionMode() != null ? data.getSessionMode() : "N/A").append("\n");
        sb.append("-------------------------------------------------\n");
        sb.append("Students:\n");
        sb.append("  Max Concurrent: ").append(data.getMaxStudentCount()).append("\n");
        Set<String> uniqueIds = data.getUniqueStudentIds();
        Map<String, StudentInfo> studentDetails = data.getStudentDetailsMap() != null ? data.getStudentDetailsMap() : Collections.emptyMap();
        sb.append("  Total Unique IDs Joined: ").append(uniqueIds != null ? uniqueIds.size() : 0).append("\n");
        if (uniqueIds != null && !uniqueIds.isEmpty()) {
            sb.append("  Student Details:\n");
            // Sort IDs for consistent log output
            List<String> sortedIds = uniqueIds.stream().sorted().collect(Collectors.toList());
            for (String id : sortedIds) {
                StudentInfo info = studentDetails.get(id);
                String name = info != null && info.studentName() != null ? info.studentName() : "(Unknown Name)";
                String roll = info != null && info.rollNo() != null ? info.rollNo() : "(N/A)";
                String cls = info != null && info.studentClass() != null ? info.studentClass() : "(N/A)";
                sb.append(String.format("    - ID: %s, Name: %s, Roll: %s, Class: %s\n", id, name, roll, cls));
            }
        } else {
             sb.append("  Student IDs: [None]\n");
        }
        sb.append("-------------------------------------------------\n");

        // --- Initial Settings ---
        sb.append("Initial Settings:\n");
        appendList(sb, "  Blocked Apps", data.getInitialBlockedApps());
        appendList(sb, "  Blocked Websites", data.getInitialBlockedWebsites());
        // Only show initial whitelist if the initial mode was ALLOW_SPECIFIC
        // Use the final mode for this check, assuming mode doesn't change drastically or log reflects final state's relevance
        if ("ALLOW_SPECIFIC".equals(data.getSessionMode())) {
             appendList(sb, "  Whitelisted Websites", data.getInitialWebsiteWhitelist());
        }
        sb.append("-------------------------------------------------\n");

        // --- Final Settings ---
        sb.append("Final Settings (at session end):\n");
        appendList(sb, "  Blocked Apps", data.getFinalBlockedApps());
        appendList(sb, "  Blocked Websites", data.getFinalBlockedWebsites());
        // Only show final whitelist if the final mode was ALLOW_SPECIFIC
        if ("ALLOW_SPECIFIC".equals(data.getSessionMode())) {
            appendList(sb, "  Whitelisted Websites", data.getFinalWebsiteWhitelist());
        }
        sb.append("=================================================\n");
        sb.append("End of Log\n");
        sb.append("=================================================\n");

        return sb.toString();
    }

    /** Helper to safely copy lists to prevent external modification. Returns an immutable list. */
    private List<String> copyList(List<String> source) {
        return source == null ? Collections.emptyList() : List.copyOf(source);
    }

    /** Helper to format lists neatly for the log file. */
    private void appendList(StringBuilder sb, String title, List<String> list) {
        sb.append(title).append(": ");
        if (list == null || list.isEmpty()) {
            sb.append("[None]\n");
        } else {
            sb.append(list.size()).append(" item(s)\n");
             // Sort list items for consistent log output
             List<String> sortedList = list.stream().sorted().collect(Collectors.toList());
            for (String item : sortedList) {
                sb.append("    - ").append(item).append("\n");
            }
        }
    }

     /** Formats duration in seconds into a more readable HH:MM:SS format. */
    private String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) {
            return "N/A";
        }
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
} 