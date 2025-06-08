package com.cmms.ui;

import com.cmms.Main;
import com.cmms.ServiceAwareController;
import com.cmms.dto.SessionSettings;
import com.cmms.dto.WebSocketMessage;
import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
// Updated imports
import com.cmms.taskManager.AppMonitorService; 
import com.cmms.networkManager.WebsiteMonitorService; // <-- UNCOMMENTED
// import com.cmms.driverManager.UsbMonitorService; // REMOVED
import com.cmms.networkManager.NetworkManagerWin; // COMMENTED OUT
import com.cmms.driverManager.DriverManager; // ADDED
import com.cmms.logging.SessionLoggerService; // Import logger service

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList; // For empty list creation

/**
 * Controller for the Student Monitoring screen.
 * Connects to WebSocket, receives settings/commands, and manages enforcement services.
 */
public class StudentMonitorController implements ServiceAwareController, WebSocketService.WebSocketListener {

    @FXML private Label sessionCodeLabel;
    @FXML private Label studentIdLabel;
    @FXML private Label connectionStatusLabel;
    @FXML private TextArea logArea;
    @FXML private Button disconnectButton;

    private ApiService apiService; // Might not be needed here, but injected
    private WebSocketService webSocketService;
    private SessionLoggerService sessionLoggerService; // Keep instance if needed later

    // Instance variables to hold session data
    private SessionSettings currentSettings;
    private String authToken;
    private String sessionCode;
    private String studentId;
    private String studentName; 
    private String studentClass; // Added to hold class info
    private String studentRollNo; // Added to hold roll number

    // Enforcement service instances
    private AppMonitorService appMonitorService;
    private WebsiteMonitorService websiteMonitorService; // <-- ADDED
    // private UsbMonitorService usbMonitorService; // REMOVED (Keep removed if DriverManager handles it)
    // No instance needed for static NetworkManagerWin or DriverManager

    private boolean isCleanupDone = false;
    private boolean isInitialized = false; // Flag to prevent double init

    @Override
    public void setApiService(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        if (this.webSocketService != null) {
            this.webSocketService.addListener(this);
        }
    }

    // Implement required method from ServiceAwareController
    @Override
    public void setSessionLoggerService(SessionLoggerService sessionLoggerService) {
        this.sessionLoggerService = sessionLoggerService;
        // We *could* use this logger service here for student-side logging if desired
        // logInfo("SessionLoggerService injected into Student Monitor.");
    }

    // Updated method to receive more student details
    public void setSessionData(String authToken, SessionSettings settings, String sessionCode, String studentId, String studentName, String studentClass, String studentRollNo) {
        this.authToken = authToken;
        this.currentSettings = settings;
        this.sessionCode = sessionCode;
        this.studentId = studentId;
        this.studentName = studentName; 
        this.studentClass = studentClass; // Store class
        this.studentRollNo = studentRollNo; // Store roll number
        
        // Now that data is set, proceed with initialization that depends on it
        initializeMonitor(); 
    }

    @FXML
    public void initialize() {
        logInfo("Initializing Student Monitor UI components...");
        connectionStatusLabel.setText("Status: Initializing...");
    }

    private void initializeMonitor() {
        if (isInitialized) return; 
        isInitialized = true;
        
        logInfo("Configuring Student Monitor with session data...");

        // Check if data was set correctly via setSessionData
        // Added checks for new fields
        if (currentSettings == null || authToken == null || sessionCode == null || studentId == null || studentName == null || studentClass == null || studentRollNo == null) {
            logError("Critical error: Session data was not fully provided to the monitor controller.");
            connectionStatusLabel.setText("Status: Error - Missing session data");
            Main.showError("Initialization Error", "Could not retrieve complete session details. Please restart the join process.");
            disconnectButton.setDisable(true);
            return;
        }

        // Update UI labels
        sessionCodeLabel.setText("Session: " + this.sessionCode);
        studentIdLabel.setText("Your ID: " + this.studentId);
        logInfo("Session Type: " + this.currentSettings.getSessionType());
        logInfo("USB Blocking: " + this.currentSettings.isBlockUsb());
        logInfo("Website Whitelist: " + this.currentSettings.getWebsiteWhitelist());

        // Instantiate necessary services
        appMonitorService = new AppMonitorService(webSocketService, this.studentId);
        websiteMonitorService = new WebsiteMonitorService(webSocketService, this.studentId); // <-- ADDED Instantiation
        // usbMonitorService = new UsbMonitorService(webSocketService, this.studentId); // REMOVED

        // Connect to WebSocket
        connectionStatusLabel.setText("Status: Connecting...");
        webSocketService.connectAndAuthenticate(this.authToken);

        initializeServices();
    }

    private void initializeServices() {
        logInfo("Initializing monitoring services...");
        try {
            // Initialize any needed monitoring services here
            
            // Make sure critical services like Cursor can stay connected
            // NetworkManagerWin.whitelistCriticalServices(); // Ensure critical services are allowed
            
            // Start monitoring based on the current settings
            updateEnforcementServicesCompared(null, false, this.currentSettings);
        } catch (Exception e) {
            String errorMsg = "Error initializing services: " + e.getMessage();
            logError(errorMsg);
            e.printStackTrace();
        }
    }

    private void startEnforcement(SessionSettings settings) {
        logInfo("Starting enforcement based on initial settings...");
        this.currentSettings = settings; // Update local settings copy

        // Start App Monitoring
        List<String> appBlacklist = settings.getAppBlacklist() != null ? settings.getAppBlacklist() : new ArrayList<>();
        appMonitorService.startMonitoring(appBlacklist);
        logInfo("App monitoring started.");

        // Start Website Monitoring (using WebsiteMonitorService)
        if (websiteMonitorService != null) { // <-- ADDED Check
             logInfo("Starting initial website monitoring (Hosts File)...");
             websiteMonitorService.startMonitoring(
                settings.getSessionType(),
                settings.getWebsiteBlacklist() != null ? settings.getWebsiteBlacklist() : new ArrayList<>(),
                settings.getWebsiteWhitelist() != null ? settings.getWebsiteWhitelist() : new ArrayList<>()
             ); // <-- ADDED Call
        } // <-- ADDED Check

        // Start USB Monitoring (PnP)
        if (settings.isBlockUsb()) {
            logInfo("USB monitoring (PnP) starting (Blocking Enabled)... Session: " + this.sessionCode + ", PC: " + this.studentId);
             // Start in a new thread as it contains an indefinite loop
            new Thread(() -> DriverManager.startMonitoring(
                    this.sessionCode, 
                    this.studentId, 
                    this.studentName, // Pass necessary details
                    this.studentClass,
                    this.studentRollNo
            ), "DriverManager-Monitor").start(); // Give thread a name
        } else {
            logInfo("USB monitoring (PnP) is disabled by session settings.");
            // Ensure any previous PnP monitoring is stopped
            DriverManager.stopMonitoring();
        }
        
        isCleanupDone = false; // Reset cleanup flag
    }

    private void stopEnforcementAndCleanup() {
        if(isCleanupDone) return;
        isCleanupDone = true; // Set flag early
        
        logInfo("Stopping enforcement and performing cleanup...");
        
        // Stop App Monitor
        if (appMonitorService != null) {
            appMonitorService.stopMonitoring();
            logInfo("App monitoring stopped.");
        }
        
        // Disable Firewall Rules
        logInfo("Disabling network restrictions (Hosts File & Firewall)... Session: " + this.sessionCode); // <-- UPDATED Log Message
        // Remove firewall call:
        // new Thread(() -> NetworkManagerWin.disableInternetRestrictions(this.sessionCode)).start(); // <-- COMMENTED OUT
        // Stop Website Monitor (reverts hosts file)
        if (websiteMonitorService != null) { // <-- ADDED
            websiteMonitorService.stopMonitoring(); // <-- ADDED
            logInfo("Website monitoring stopped (Hosts file reverted)."); // <-- ADDED
        } // <-- ADDED
        
        // Stop USB Monitor (PnP) and Re-enable Devices
        logInfo("Stopping USB monitoring (PnP) and re-enabling devices...");
        // This call now handles re-enabling within it
        DriverManager.stopMonitoring(); 
        logInfo("USB monitoring (PnP) stopped.");
        
    }

    @FXML
    void handleDisconnectAction(ActionEvent event) {
        logInfo("Manual disconnect initiated by user.");
        disconnectButton.setDisable(true);
        stopEnforcementAndCleanup();
        if (webSocketService.isConnected()) {
            webSocketService.disconnect();
        }
        // Navigate back to role selection
        Main.loadRoleSelectionView();
    }

    // --- WebSocketListener Implementation ---
    @Override
    public void onWebSocketOpen() {
        Platform.runLater(() -> {
            connectionStatusLabel.setText("Status: Connected, Authenticating...");
            logInfo("WebSocket Connected. Waiting for authentication confirmation...");
        });
    }

    @Override
    public void onWebSocketMessage(WebSocketMessage message) {
        Platform.runLater(() -> {
            logInfo("WS Received: Type=" + message.getType());

            switch (message.getType()) {
                 case "response": 
                    if (message.getPayload() != null && 
                        message.getPayload().containsKey("message") &&
                        String.valueOf(message.getPayload().get("message")).toLowerCase().contains("authentication successful")) {
                        logInfo("WebSocket Authenticated successfully by server.");
                        connectionStatusLabel.setText("Status: Connected & Authenticated");
                        if(this.currentSettings != null) {
                            startEnforcement(this.currentSettings); // Start enforcement AFTER auth
                        } else {
                            logError("Cannot start enforcement: Initial settings missing after authentication.");
                        }
                    } else if ("error".equalsIgnoreCase(message.getStatus())) {
                          logError("WS Error Response: " + message.getPayload());
                     } else {
                         logInfo("WS Response: " + message.getPayload());
                     }
                    break;
                    
                case "initial_settings":
                    logInfo("Received initial settings from server.");
                     if (message.getPayload() != null) {
                         try {
                            SessionSettings initialSettings = parseSettingsFromPayload(message.getPayload());
                            this.currentSettings = initialSettings; 
                            logInfo("Initial settings applied locally.");
                            // If already authenticated, update enforcement immediately
                            if (connectionStatusLabel.getText().contains("Authenticated")) {
                                logInfo("Applying initial_settings received after auth.");
                                updateEnforcementServices(initialSettings);
                            }
                         } catch (Exception e) {
                             logError("Failed to parse initial_settings payload: " + e.getMessage());
                         }
                    }
                    break;

                case "settings_update":
                    logInfo("Received settings update from server.");
                    if (message.getPayload() != null) {
                         try {
                            SessionSettings updatedSettings = parseSettingsFromPayload(message.getPayload());
                            // Store the *previous* settings before updating
                            boolean wasUsbBlocked = this.currentSettings.isBlockUsb();
                            String previousSessionType = this.currentSettings.getSessionType();
                            
                            updateLocalSettings(updatedSettings); // Update internal state
                            logInfo("Applying updated server settings to enforcement...");
                            
                            // Compare previous and new settings to apply changes correctly
                            updateEnforcementServicesCompared(previousSessionType, wasUsbBlocked, this.currentSettings);
                         } catch (Exception e) {
                             logError("Failed to parse settings_update payload: " + e.getMessage());
                         }
                    }
                    break;

                case "app_added": // Specific app added update
                    if (appMonitorService != null && message.getPayload() != null && message.getPayload().containsKey("app_name")) {
                        String appName = (String) message.getPayload().get("app_name");
                        appMonitorService.addToBlacklist(appName);
                        logInfo("App added to blacklist via WS: " + appName);
                    }
                    break;
                
                 case "app_removed": // Specific app removed update
                    if (appMonitorService != null && message.getPayload() != null && message.getPayload().containsKey("app_name")) {
                        String appName = (String) message.getPayload().get("app_name");
                        appMonitorService.removeFromBlacklist(appName);
                         logInfo("App removed from blacklist via WS: " + appName);
                    }
                    break;
                
                case "force_disconnect":
                    logWarn("Received force_disconnect command from teacher/server.");
                    connectionStatusLabel.setText("Status: Disconnected by Teacher");
                    disconnectButton.setDisable(true);
                    stopEnforcementAndCleanup();
                    if (webSocketService.isConnected()) webSocketService.removeListener(this);
                    webSocketService.disconnect(); // Disconnect WS
                    Main.showInfo("Disconnected", "The teacher has ended the session or removed you.");
                    // Navigate back to role selection
                    Main.loadRoleSelectionView();
                    break;
                    
                case "session_ending": // ADDED Handler
                     logWarn("Session is ending as signaled by the server.");
                     connectionStatusLabel.setText("Status: Session Ended by Teacher");
                     disconnectButton.setDisable(true);
                     stopEnforcementAndCleanup();
                     if (webSocketService.isConnected()) webSocketService.removeListener(this);
                     // WS should be terminated by server, but call disconnect locally too
                     webSocketService.disconnect(); 
                     Main.showInfo("Session Ended", "The session has been ended by the teacher.");
                     // Navigate back to role selection
                     Main.loadRoleSelectionView();
                     break;
                    
                case "error": // Errors sent by backend explicitly
                    if (message.getPayload() != null && message.getPayload().containsKey("message")) {
                        logError("Backend WS Error: " + message.getPayload().get("message"));
                        // Potentially disconnect or show error to user depending on severity
                    }
                    break;

                // Ignore messages meant for the teacher
                case "student_joined":
                case "student_left":
                case "student_data": 
                case "initial_student_list":
                    break; 

                case "connection_ack": // Explicitly ignore connection_ack
                     logInfo("Connection acknowledged by server.");
                     break;

                default:
                    logWarn("Received unhandled WS message type: " + message.getType());
            }
        });
    }

    // Renamed old update method
    private void updateEnforcementServices(SessionSettings settings) {
        // This method is now less ideal as it doesn't know previous state.
        // Call the new comparative method instead.
        logWarn("Deprecated updateEnforcementServices called. Use comparative version.");
        updateEnforcementServicesCompared(null, !settings.isBlockUsb(), settings); // Guess previous state
    }

    // New method to handle updates by comparing old and new states
    private void updateEnforcementServicesCompared(String previousSessionType, boolean wasUsbBlocked, SessionSettings newSettings) {
        if (newSettings == null) {
            logError("Cannot update enforcement: New settings are null.");
            return;
        }
        logInfo("Comparing and updating enforcement services...");
        
        // Update App Monitor (always updates list)
        if (appMonitorService != null) {
            appMonitorService.updateAppBlacklist(newSettings.getAppBlacklist() != null ? newSettings.getAppBlacklist() : new ArrayList<>());
        }

        // Update Website Monitor (Hosts File)
        if (websiteMonitorService != null) { // <-- ADDED Check
            logInfo("Settings Update: Updating website monitoring (Hosts File)...");
            websiteMonitorService.updateMonitoringMode(
                newSettings.getSessionType(), 
                newSettings.getWebsiteBlacklist() != null ? newSettings.getWebsiteBlacklist() : new ArrayList<>(),
                newSettings.getWebsiteWhitelist() != null ? newSettings.getWebsiteWhitelist() : new ArrayList<>()
            ); // <-- ADDED Call
        } // <-- ADDED Check

        // Update USB Monitoring (PnP)
        boolean shouldBlockUsb = newSettings.isBlockUsb();
        if (shouldBlockUsb && !wasUsbBlocked) {
            // USB blocking was OFF, now turned ON
            logInfo("Settings Update: Starting USB monitoring (PnP) - Blocking Enabled.");
             new Thread(() -> DriverManager.startMonitoring(
                    this.sessionCode, this.studentId, this.studentName, 
                    this.studentClass, this.studentRollNo
             ), "DriverManager-Monitor-Update").start();
        } else if (!shouldBlockUsb && wasUsbBlocked) {
            // USB blocking was ON, now turned OFF
            logInfo("Settings Update: Stopping USB monitoring (PnP) - Blocking Disabled.");
            DriverManager.stopMonitoring();
        } // else: state didn't change, do nothing to USB monitoring

        logInfo("Enforcement services update process finished.");
    }

    // Helper to parse settings from a payload Map (used by initial_settings & settings_update)
    private SessionSettings parseSettingsFromPayload(Map<String, Object> payload) {
         SessionSettings settings = new SessionSettings(); // Create a new DTO
         if (payload.containsKey("sessionType")) settings.setSessionType((String) payload.get("sessionType"));
         if (payload.containsKey("blockUsb")) settings.setBlockUsb(Boolean.TRUE.equals(payload.get("blockUsb")));
         if (payload.containsKey("websiteBlacklist")) settings.setWebsiteBlacklist((List<String>) payload.get("websiteBlacklist"));
         if (payload.containsKey("websiteWhitelist")) settings.setWebsiteWhitelist((List<String>) payload.get("websiteWhitelist"));
         if (payload.containsKey("appBlacklist")) settings.setAppBlacklist((List<String>) payload.get("appBlacklist")); // If backend sends full list
         return settings;
    }
    
    // Helper to update local currentSettings based on a partial update payload
    private void updateLocalSettings(SessionSettings updatedSettings) {
         if (updatedSettings.getSessionType() != null) this.currentSettings.setSessionType(updatedSettings.getSessionType());
         // Always update boolean directly if key exists
         if (updatedSettings.isBlockUsb() != this.currentSettings.isBlockUsb()) { // More robust check needed if key missing vs false
              this.currentSettings.setBlockUsb(updatedSettings.isBlockUsb());
         }
         if (updatedSettings.getWebsiteBlacklist() != null) this.currentSettings.setWebsiteBlacklist(updatedSettings.getWebsiteBlacklist());
         if (updatedSettings.getWebsiteWhitelist() != null) this.currentSettings.setWebsiteWhitelist(updatedSettings.getWebsiteWhitelist());
         if (updatedSettings.getAppBlacklist() != null) this.currentSettings.setAppBlacklist(updatedSettings.getAppBlacklist());
    }

    @Override
    public void onWebSocketClose(int code, String reason) {
        Platform.runLater(() -> {
            logWarn("WebSocket closed. Code: " + code + ", Reason: " + reason);
            connectionStatusLabel.setText("Status: Disconnected (" + reason + ")");
            disconnectButton.setDisable(true); // Disable manual disconnect if already closed
            // Perform cleanup if not already done (e.g., due to force_disconnect)
            stopEnforcementAndCleanup(); 
            // Optional: Add automatic reconnect logic here if desired
        });
    }

    @Override
    public void onWebSocketError(String message, Exception ex) {
        Platform.runLater(() -> {
            logError("WebSocket Error: " + message + (ex != null ? " - " + ex.getMessage() : ""));
            connectionStatusLabel.setText("Status: Error");
            // Maybe try reconnect or show error
            if (ex != null) ex.printStackTrace();
        });
    }

    // --- Utility Methods ---

    private void log(String message, String level) {
        String timestamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        // Use %n for platform-independent newline
        logArea.appendText(String.format("[%s] [%s] %s%n", timestamp, level, message));
    }

    private void logInfo(String message) {
        log(message, "INFO");
    }

    private void logWarn(String message) {
        log(message, "WARN");
    }

    private void logError(String message) {
        log(message, "ERROR");
    }
} 