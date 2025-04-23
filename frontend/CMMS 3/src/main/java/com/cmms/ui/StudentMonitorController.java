package com.cmms.ui;

import com.cmms.Main;
import com.cmms.ServiceAwareController;
import com.cmms.dto.SessionSettings;
import com.cmms.dto.WebSocketMessage;
import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
// Placeholder imports for enforcement services - Replace with actual classes later
import com.cmms.taskManager.AppMonitorService; 
import com.cmms.networkManager.WebsiteMonitorService;
import com.cmms.driverManager.UsbMonitorService;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

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

    // Instance variables to hold session data (instead of relying on static gets)
    private SessionSettings currentSettings;
    private String authToken;
    private String sessionCode;
    private String studentId;
    private String studentName; // If needed for display

    // Enforcement service instances (to be implemented)
    private AppMonitorService appMonitorService;
    private WebsiteMonitorService websiteMonitorService;
    private UsbMonitorService usbMonitorService;

    private boolean isCleanupDone = false;
    private boolean isInitialized = false; // Flag to prevent double init

    @Override
    public void setApiService(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.webSocketService.addListener(this); // Register for WS messages
    }

    // New method to explicitly set session data AFTER controller is loaded
    public void setSessionData(String authToken, SessionSettings settings, String sessionCode, String studentId, String studentName) {
        this.authToken = authToken;
        this.currentSettings = settings;
        this.sessionCode = sessionCode;
        this.studentId = studentId;
        this.studentName = studentName; // Store if needed
        
        // Now that data is set, proceed with initialization that depends on it
        initializeMonitor(); 
    }

    @FXML
    public void initialize() {
        // Basic FXML initialization only (e.g., setting styles)
        logInfo("Initializing Student Monitor UI components...");
        connectionStatusLabel.setText("Status: Initializing...");
        // DO NOT retrieve static data here anymore
    }

    // Renamed original initialize logic, called by setSessionData
    private void initializeMonitor() {
        if (isInitialized) return; // Prevent running twice
        isInitialized = true;
        
        logInfo("Configuring Student Monitor with session data...");

        // Check if data was set correctly via setSessionData
        if (currentSettings == null || authToken == null || sessionCode == null || studentId == null) {
            logError("Critical error: Session data was not provided to the monitor controller.");
            connectionStatusLabel.setText("Status: Error - Missing session data");
            Main.showError("Initialization Error", "Could not retrieve session details. Please restart the join process.");
            disconnectButton.setDisable(true);
            return;
        }

        // Update UI labels with instance data
        sessionCodeLabel.setText("Session: " + this.sessionCode);
        studentIdLabel.setText("Your ID: " + this.studentId);
        logInfo("Session Type: " + this.currentSettings.getSessionType());
        logInfo("USB Blocking: " + this.currentSettings.isBlockUsb());
        logInfo("Website Blacklist: " + this.currentSettings.getWebsiteBlacklist());
        logInfo("Website Whitelist: " + this.currentSettings.getWebsiteWhitelist());

        // Instantiate enforcement services 
        appMonitorService = new AppMonitorService(webSocketService, this.studentId);
        websiteMonitorService = new WebsiteMonitorService(webSocketService, this.studentId);
        usbMonitorService = new UsbMonitorService(webSocketService, this.studentId);

        // Connect to WebSocket
        connectionStatusLabel.setText("Status: Connecting...");
        webSocketService.connectAndAuthenticate(this.authToken);
    }

    private void startEnforcement(SessionSettings settings) {
        logInfo("Starting enforcement based on initial settings...");
        this.currentSettings = settings; // Update local settings copy

        // Start App Monitoring (always active, gets blacklist via WS)
        // TODO: Pass initial app blacklist if available in settings
        appMonitorService.startMonitoring(settings.getAppBlacklist()); 
        logInfo("App monitoring started.");

        // Start Website Monitoring based on type
        websiteMonitorService.startMonitoring(settings.getSessionType(), 
                                            settings.getWebsiteBlacklist(), 
                                            settings.getWebsiteWhitelist());
        logInfo("Website monitoring started (Mode: " + settings.getSessionType() + ").");

        // Start USB Monitoring if enabled
        if (settings.isBlockUsb()) {
            usbMonitorService.startMonitoring(true);
            logInfo("USB monitoring started (Blocking Enabled).");
        } else {
            usbMonitorService.startMonitoring(false); // Start in non-blocking mode to detect attempts
             logInfo("USB monitoring started (Blocking Disabled - Reporting only).");
        }
        
        isCleanupDone = false; // Reset cleanup flag
    }

    private void stopEnforcementAndCleanup() {
        if(isCleanupDone) return; // Prevent double cleanup
        
        logInfo("Stopping enforcement and performing cleanup...");
        if (appMonitorService != null) {
            appMonitorService.stopMonitoring();
            logInfo("App monitoring stopped.");
        }
        if (websiteMonitorService != null) {
            websiteMonitorService.stopMonitoring();
            logInfo("Website monitoring stopped & hosts file reverted.");
        }
        if (usbMonitorService != null) {
            usbMonitorService.stopMonitoring();
            logInfo("USB monitoring stopped & blocking reverted.");
        }
        isCleanupDone = true;
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
                 case "response": // Handle responses like successful authentication
                    // Check if the payload indicates successful authentication
                    if (message.getPayload() != null && 
                        message.getPayload().containsKey("message") &&
                        String.valueOf(message.getPayload().get("message")).toLowerCase().contains("authentication successful")) {
                            
                        logInfo("WebSocket Authenticated successfully by server.");
                        connectionStatusLabel.setText("Status: Connected & Authenticated");
                        // Now that authentication is confirmed, start enforcement
                        // Make sure currentSettings are available (should be from REST call)
                        if(this.currentSettings != null) {
                            startEnforcement(this.currentSettings);
                        } else {
                            logError("Cannot start enforcement: Initial settings missing after authentication.");
                            // Request settings again? Or disconnect?
                        }
                    } else if ("error".equalsIgnoreCase(message.getStatus())) {
                          logError("WS Error Response: " + message.getPayload());
                     } else {
                         // Log other non-auth success responses if needed
                         logInfo("WS Response: " + message.getPayload());
                     }
                    break;
                    
                case "initial_settings":
                    logInfo("Received initial settings from server.");
                     if (message.getPayload() != null) {
                         try {
                            SessionSettings initialSettings = parseSettingsFromPayload(message.getPayload());
                            // Apply settings, but DON'T start enforcement here, wait for auth confirmation
                            this.currentSettings = initialSettings; // Update local copy
                            logInfo("Initial settings applied locally.");
                            // If already authenticated, re-apply/update enforcement
                            if (connectionStatusLabel.getText().contains("Authenticated")) {
                                logInfo("Re-applying enforcement based on initial_settings received after auth.");
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
                            updateLocalSettings(updatedSettings); // Update internal state
                            logInfo("Applying updated server settings to enforcement...");
                            updateEnforcementServices(this.currentSettings); // Apply changes to services
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

    // Extracted logic to update enforcement services based on current settings
    private void updateEnforcementServices(SessionSettings settings) {
        if (settings == null) {
            logError("Cannot update enforcement: Settings are null.");
            return;
        }
        logInfo("Updating enforcement services...");
        if (appMonitorService != null) {
            appMonitorService.updateAppBlacklist(settings.getAppBlacklist() != null ? settings.getAppBlacklist() : List.of());
        }
        if (websiteMonitorService != null) {
            websiteMonitorService.updateMonitoringMode(settings.getSessionType(),
                                                   settings.getWebsiteBlacklist() != null ? settings.getWebsiteBlacklist() : List.of(),
                                                   settings.getWebsiteWhitelist() != null ? settings.getWebsiteWhitelist() : List.of());
        }
        if (usbMonitorService != null) {
            usbMonitorService.updateBlockingState(settings.isBlockUsb());
        }
        logInfo("Enforcement services updated.");
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