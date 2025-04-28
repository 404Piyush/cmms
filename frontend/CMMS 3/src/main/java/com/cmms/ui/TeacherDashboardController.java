package com.cmms.ui;

import com.cmms.Main; // To navigate views
import com.cmms.ServiceAwareController; // Interface for service injection
import com.cmms.dto.Session; // The new Session DTO
import com.cmms.dto.ApiResponse; // Assuming ApiService returns this
import com.cmms.dto.WebSocketMessage; // Import WebSocketMessage DTO
import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;

import javafx.application.Platform;
import javafx.collections.FXCollections; // Import for observable list
import javafx.collections.ObservableList; // Import for observable list
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;      // Added for Clipboard
import javafx.scene.input.ClipboardContent; // Added for Clipboard
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.animation.PauseTransition; // Import for delay
import javafx.util.Duration; // Import for Duration

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID; // Added for unique teacher ID generation
import java.util.HashMap;
import java.util.ArrayList;

// Simple record to hold student display information
record StudentInfo(String studentId, String studentName, String rollNo, String studentClass) {
    @Override
    public String toString() {
        // This controls what is displayed in the ListView by default if no cell factory is set
        // We'll use a cell factory for better control, but this is a fallback.
        return studentName != null ? studentName : studentId; 
    }
}

// Implement ServiceAwareController AND WebSocketListener
public class TeacherDashboardController implements ServiceAwareController, WebSocketService.WebSocketListener {

    // Removed mainController field
    private Stage stage;
    private Session activeSession; // Use the DTO
    private ApiService apiService;
    private WebSocketService webSocketService; // Might be used for real-time updates

    @FXML private Label welcomeLabel;
    @FXML private Label sessionCodeLabel;
    @FXML private Label statusLabel;
    @FXML private ListView<StudentInfo> studentListView;
    @FXML private VBox mainLayout; 
    @FXML private ProgressIndicator loadingIndicator; // Add loading indicator
    @FXML private Button startSessionButton; // Reference buttons to disable/enable
    @FXML private Button endSessionButton;
    @FXML private Button logoutButton;
    @FXML private Button copySessionCodeButton; // Added Button reference

    // Use ObservableList for easier ListView updates
    private ObservableList<StudentInfo> connectedStudents = FXCollections.observableArrayList(); 

    // --- TabPane and Student Tab Fields ---
    @FXML private TabPane mainTabPane;

    // --- Settings Tab Fields ---
    @FXML private Label websiteListLabel;
    @FXML private ListView<String> websiteListView;
    @FXML private TextField websiteInputField;
    @FXML private Button deleteWebsiteButton;
    @FXML private Button addWebsiteButton; 
    @FXML private VBox websiteManagementPane; 
    private ObservableList<String> currentWebsiteList = FXCollections.observableArrayList();

    @FXML private ListView<String> appListView;
    @FXML private TextField appInputField;
    @FXML private Button deleteAppButton;
    @FXML private Button addAppButton; 
    @FXML private VBox appManagementPane; 
    private ObservableList<String> currentAppList = FXCollections.observableArrayList();
    
    @FXML private VBox settingsContainerVBox; // Added parent container

    // Current Session State
    private String desiredSessionType; // ADDED: To store type selected before starting
    private String currentSessionType = null;
    private boolean currentUsbBlocked = false;
    private boolean isWebSocketAuthenticated = false; 

    private String originalCopyButtonText = "Copy"; // Store original text
    private String originalCopyButtonStyle = ""; // Store original style (if any)

    // Store map of studentId to their detailed logs
    private Map<String, List<String>> studentLogs = new HashMap<>();

    // Implement service setters
    @Override
    public void setApiService(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        if (this.webSocketService != null) {
            this.webSocketService.addListener(this); // Register as listener
            // If WS is already connected (e.g., reconnect scenario), try authenticating
            if (this.webSocketService.isConnected() && this.apiService != null && this.apiService.getTeacherAuthToken() != null) {
                 authenticateWebSocket();
            }
        }
        
        // Ensure teacher machine is identified correctly
        com.cmms.networkManager.NetworkManagerWin.setTeacherMachine(true);
        // com.cmms.networkManager.NetworkManagerWin.whitelistCriticalServices();

        // Start listening for student connections/messages
    }

    // Removed setMainController

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML // Use @FXML initialize instead of initializeUI
    public void initialize() {
        welcomeLabel.setText("Welcome, Teacher!");
        // TODO: Optionally, display teacher's name if available (needs data)

        mainLayout.setPadding(new Insets(20));
        mainLayout.setSpacing(15);

        sessionCodeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px; -fx-text-fill: blue;");
        statusLabel.setStyle("-fx-font-style: italic;");
        studentListView.setStyle("-fx-border-color: lightgrey;");

        sessionCodeLabel.setText("Session Code: N/A");
        statusLabel.setText("Ready to start a session.");
        loadingIndicator.setVisible(false);
        
        // Initial state: Cannot end or logout if no session active
        endSessionButton.setDisable(true);
        copySessionCodeButton.setDisable(true); // Disable copy button initially

        // Bind Lists to ListViews
        studentListView.setItems(connectedStudents);
        websiteListView.setItems(currentWebsiteList);
        appListView.setItems(currentAppList);

        // Disable delete buttons initially
        deleteWebsiteButton.setDisable(true);
        deleteAppButton.setDisable(true);
        // Disable add buttons initially until authenticated
        addWebsiteButton.setDisable(true);
        addAppButton.setDisable(true);
        
        // Listeners to enable/disable delete buttons
        websiteListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            updateButtonStates(); // Update based on selection and auth state
        });
        appListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            updateButtonStates(); // Update based on selection and auth state
        });
        
        // Initially disable settings tab until session starts?
        if (mainTabPane.getTabs().size() > 1) { 
            mainTabPane.getTabs().get(1).setDisable(true);
        }

        originalCopyButtonText = copySessionCodeButton.getText(); // Store initial text
        originalCopyButtonStyle = copySessionCodeButton.getStyle(); // Store initial style

        // Set CellFactory for Student List to display name
        studentListView.setCellFactory(lv -> new ListCell<StudentInfo>() {
            @Override
            protected void updateItem(StudentInfo item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.studentName());
                // Optionally add tooltip with more info: 
                // if (!empty && item != null) {
                //    setTooltip(new Tooltip("ID: " + item.studentId() + "\nRoll: " + item.rollNo() + "\nClass: " + item.studentClass()));
                // }
            }
        });
        
        // Add Mouse Click Listener for Student List View
        studentListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double-click
                StudentInfo selectedStudent = studentListView.getSelectionModel().getSelectedItem();
                if (selectedStudent != null) {
                    openStudentDetailWindow(selectedStudent);
                }
            }
        });
    }

    @FXML
    private void handleStartSession(ActionEvent event) {
        if (activeSession != null && activeSession.isActive()) {
             showAlert("Session Active", "A session is already running. Please end it before starting a new one.");
             return;
        }
        
        // --- CRITICAL CHANGE: Use the desired session type --- 
        if (this.desiredSessionType == null || this.desiredSessionType.isEmpty()) {
            showAlert("Configuration Error", "Session type was not properly set before starting.");
            setLoadingState(false, "Configuration error.");
            return; // Don't proceed without a type
        }
        String sessionTypeToStart = this.desiredSessionType; 
        System.out.println("handleStartSession: Using desired session type: " + sessionTypeToStart);
        // --- END CRITICAL CHANGE ---
        
        // Clear previous state BEFORE making the call
        sessionCodeLabel.setText("Session Code: Generating...");
        connectedStudents.clear(); // Clear student list on new session
        copySessionCodeButton.setDisable(true); // Disable copy while generating
        setLoadingState(true, "Starting session...");

        // Background task for API call
        Task<ApiResponse<Object>> startSessionTask = new Task<>() {
            @Override
            protected ApiResponse<Object> call() throws Exception {
                // Generate a unique ID for this teacher instance for the session
                String adminPc = "Teacher_" + UUID.randomUUID().toString();
                // Define default session settings (can be made configurable later)
                boolean blockUsb = true; // TODO: Make this configurable too? Get from config?
                // ***** REMOVED Hardcoded type *****
                // String sessionType = "BLOCK_APPS_WEBSITES"; 
                
                // Make the actual API call with the selected type
                return apiService.createSession(adminPc, sessionTypeToStart, blockUsb); // Use variable
            }
        };

        startSessionTask.setOnSucceeded(workerStateEvent -> {
            ApiResponse<Object> response = startSessionTask.getValue();
            Platform.runLater(() -> {
                setLoadingState(false, ""); 
                if (response != null && response.getSessionCode() != null && response.getToken() != null) { // Check for token too
                    String actualSessionCode = response.getSessionCode();
                    activeSession = new Session(actualSessionCode);
                    sessionCodeLabel.setText("Session Code: " + actualSessionCode);
                    statusLabel.setText("Session started. Authenticating WebSocket...");
                    connectedStudents.clear(); // Clear student list on new session
                    connectedStudents.add(new StudentInfo("Waiting for students...", null, null, null)); // Initial message
                    endSessionButton.setDisable(false);
                    copySessionCodeButton.setDisable(false);
                    startSessionButton.setDisable(true);

                    // Enable Settings Tab
                    if (mainTabPane.getTabs().size() > 1) {
                        mainTabPane.getTabs().get(1).setDisable(false);
                    }
                    
                    // Authenticate WebSocket (which will trigger fetching settings)
                    authenticateWebSocket(); 

                } else {
                    // FAILURE: Reset UI to initial state
                    showAlert("Session Error", "Failed to start session: Invalid response from server (missing code or token).");
                    statusLabel.setText("Failed to start session.");
                    sessionCodeLabel.setText("Session Code: N/A"); 
                    copySessionCodeButton.setDisable(true);
                    startSessionButton.setDisable(false); // Allow retry
                    endSessionButton.setDisable(true);
                    // Disable Settings Tab on failure
                    if (mainTabPane.getTabs().size() > 1) {
                        mainTabPane.getTabs().get(1).setDisable(true);
                    }
                }
            });
        });

        startSessionTask.setOnFailed(workerStateEvent -> {
            Throwable exception = startSessionTask.getException();
            Platform.runLater(() -> {
                // FAILURE ON EXCEPTION: Reset UI to initial state
                setLoadingState(false, ""); 
                statusLabel.setText("Error starting session: " + exception.getMessage());
                showAlert("Session Error", "Failed to start session:\n" + exception.getMessage());
                sessionCodeLabel.setText("Session Code: N/A"); 
                copySessionCodeButton.setDisable(true);
                startSessionButton.setDisable(false); // Allow retry
                endSessionButton.setDisable(true);
                // Disable Settings Tab on failure
                if (mainTabPane.getTabs().size() > 1) {
                    mainTabPane.getTabs().get(1).setDisable(true);
                }
                exception.printStackTrace();
            });
        });

        new Thread(startSessionTask).start();
    }

    @FXML
    private void handleEndSession(ActionEvent event) {
        if (activeSession == null || !activeSession.isActive()) {
             showAlert("No Session", "There is no active session to end.");
             return;
        }

        String sessionCodeToEnd = activeSession.getSessionCode();
        setLoadingState(true, "Ending session...");

        // Background task for API call
        Task<ApiResponse<Object>> endSessionTask = new Task<>() {
            @Override
            protected ApiResponse<Object> call() throws Exception {
                // Use the actual API call
                 return apiService.endSession(sessionCodeToEnd);
            }
        };

        endSessionTask.setOnSucceeded(workerStateEvent -> {
             ApiResponse<Object> response = endSessionTask.getValue(); 
            Platform.runLater(() -> {
                setLoadingState(false, "");
                logToStatus("Session " + sessionCodeToEnd + " ended via API.");
                
                // --- Display Summary --- 
                String summaryMessage = "Session ended successfully.";
                if (response != null && response.getPayload() instanceof Map) {
                    Map<String, Object> summary = (Map<String, Object>) response.getPayload(); // Assuming summary is in payload
                    if (summary != null && summary.containsKey("summary")) { // Check if payload IS the summary or CONTAINS it
                        summary = (Map<String, Object>) summary.get("summary");
                    }
                    // Safely extract summary data
                    Object durationObj = summary != null ? summary.get("durationMinutes") : null;
                    Object countObj = summary != null ? summary.get("studentCountAtEnd") : null;
                    long duration = (durationObj instanceof Number) ? ((Number) durationObj).longValue() : -1;
                    long count = (countObj instanceof Number) ? ((Number) countObj).longValue() : -1;
                    
                    summaryMessage += "\n\nSession Summary:";
                    if (duration >= 0) summaryMessage += "\nDuration: " + duration + " minutes";
                    if (count >= 0) summaryMessage += "\nStudents connected at end: " + count;
                }
                // Use Main.showInfo as it's a static utility method in Main
                Main.showInfo("Session Ended", summaryMessage); 
                // --- End Summary Display ---
                
                resetSessionUI(); // Reset UI elements AFTER showing summary
                
                // Disconnect WS - Moved this logic to backend during endSession
                // if (webSocketService != null && webSocketService.isConnected()) {
                //    webSocketService.disconnect(); 
                // }
                 apiService.clearTeacherToken(); // Clear stored token only on success
            });
        });

        endSessionTask.setOnFailed(workerStateEvent -> {
            Throwable exception = endSessionTask.getException();
            Platform.runLater(() -> {
                 setLoadingState(false, "");
                logToStatus("Error ending session: " + exception.getMessage());
                showAlert("Session Error", "Failed to end session: " + exception.getMessage());
                 // Don't reset UI automatically on failure? Or maybe reset partially?
                 exception.printStackTrace();
            });
        });
        
        new Thread(endSessionTask).start();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        // Check if there's an active session before attempting to end it
        if (activeSession != null && activeSession.isActive()) {
            // First disable UI to prevent multiple clicks
            setLoadingState(true, "Ending session before logout...");
            
            // Create a Task for ending the session
            Task<ApiResponse<Object>> endSessionTask = new Task<>() {
                @Override
                protected ApiResponse<Object> call() throws Exception {
                    return apiService.endSession(activeSession.getSessionCode());
                }
            };
            
            endSessionTask.setOnSucceeded(e -> {
                Platform.runLater(() -> {
                    // Clear token after session ended successfully
                    if (apiService != null) {
                        apiService.clearTeacherToken();
                    }
                    // Navigate back after session end completes
                    Main.loadRoleSelectionView();
                });
            });
            
            endSessionTask.setOnFailed(e -> {
                Platform.runLater(() -> {
                    setLoadingState(false, "Session end failed, logging out anyway");
                    // Log the error but proceed with logout
                    System.err.println("Error ending session during logout: " + endSessionTask.getException().getMessage());
                    if (apiService != null) {
                        apiService.clearTeacherToken();
                    }
                    Main.loadRoleSelectionView();
                });
            });
            
            new Thread(endSessionTask).start();
        } else {
            // No active session, just logout directly
            if (apiService != null) {
                apiService.clearTeacherToken();
            }
            Main.loadRoleSelectionView();
        }
    }

    // Added method to handle the copy button action
    @FXML
    private void handleCopySessionCodeAction(ActionEvent event) {
        if (activeSession != null && activeSession.getSessionCode() != null) {
            String sessionCode = activeSession.getSessionCode();
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(sessionCode);
            clipboard.setContent(content);
            // logToStatus("Session code " + sessionCode + " copied to clipboard."); // Optional status log

            // --- Visual Feedback --- 
            copySessionCodeButton.setText("Copied!");
            copySessionCodeButton.setStyle("-fx-background-color: lightgreen; -fx-text-fill: black;"); // Example green style

            // Create a pause transition
            PauseTransition pause = new PauseTransition(Duration.seconds(1.5)); // 1.5 seconds delay
            pause.setOnFinished(e -> {
                // Revert button appearance after delay
                copySessionCodeButton.setText(originalCopyButtonText);
                copySessionCodeButton.setStyle(originalCopyButtonStyle); // Revert to original style
            });
            pause.play(); // Start the delay
            // --- End Visual Feedback ---

        } else {
            logToStatus("No active session code to copy.");
        }
    }

    // Method to update the student list UI 
    // TODO: This needs to be triggered periodically or by WebSocket events
    public void updateStudentList() {
        if (activeSession == null || !activeSession.isActive()) {
            // Platform.runLater(() -> studentListView.getItems().setAll("Session not active."));
            return;
        }

        String currentSessionCode = activeSession.getSessionCode();
        // Task to fetch student list
        Task<Map<String, String>> getStudentsTask = new Task<>() {
            @Override
            protected Map<String, String> call() throws Exception {
                // TODO: Implement apiService.getStudentsInSession(currentSessionCode);
                // Mocking response
                Thread.sleep(800);
                Map<String, String> mockStudents = new java.util.HashMap<>();
                // mockStudents.put("student123", "Alice");
                // mockStudents.put("student456", "Bob");
                return mockStudents; 
                // --- End Mocking ---
            }
        };

        getStudentsTask.setOnSucceeded(workerStateEvent -> {
            Map<String, String> students = getStudentsTask.getValue();
            Platform.runLater(() -> {
                studentListView.getItems().clear();
                if (students != null && !students.isEmpty()) {
                    students.forEach((id, name) -> studentListView.getItems().add(new StudentInfo(id, name, null, null)));
                } else {
                    studentListView.getItems().add(new StudentInfo("No students have joined yet.", null, null, null));
                }
            });
        });

         getStudentsTask.setOnFailed(workerStateEvent -> {
             Throwable exception = getStudentsTask.getException();
             Platform.runLater(() -> {
                 // Optionally show an error or just clear the list
                 studentListView.getItems().setAll(new StudentInfo("Error fetching student list: " + exception.getMessage(), null, null, null));
                 exception.printStackTrace();
             });
         });

        new Thread(getStudentsTask).start();
    }

    // Removed generateSessionCode - Assumed handled by backend

    // Helper method to show alerts
    private void showAlert(String title, String content) {
        // Ensure alert runs on the JavaFX Application Thread
        Platform.runLater(() -> {
             Alert alert = new Alert(Alert.AlertType.INFORMATION);
             alert.setTitle(title);
             alert.setHeaderText(null);
             alert.setContentText(content);
             alert.initOwner(stage); // Set owner if stage is available
             alert.showAndWait();
        });
    }
    
    // Helper to manage loading indicator and button states
    private void setLoadingState(boolean isLoading, String statusText) {
         loadingIndicator.setVisible(isLoading);
         logoutButton.setDisable(isLoading); // Disable logout while loading
         statusLabel.setText(isLoading ? statusText : (activeSession != null ? statusLabel.getText() : "Ready to start a session."));
         
         // If loading, disable everything; otherwise, let updateButtonStates handle it
         if (isLoading) {
            startSessionButton.setDisable(true);
            endSessionButton.setDisable(true);
            copySessionCodeButton.setDisable(true);
            addWebsiteButton.setDisable(true);
            deleteWebsiteButton.setDisable(true);
            addAppButton.setDisable(true);
            deleteAppButton.setDisable(true);
         } else {
            updateButtonStates(); // Set correct states when not loading
         }
    }

    // Helper method to connect and authenticate WebSocket
    private void authenticateWebSocket() {
        if (webSocketService == null || apiService == null) {
            logToStatus("Error: Services not initialized.");
            return;
        }
        // Use the new getter method
        String token = apiService.getTeacherAuthToken(); 
        if (token == null) {
            logToStatus("Error: Teacher auth token not available for WebSocket.");
             showAlert("WebSocket Error", "Could not authenticate WebSocket: Missing teacher token.");
            return;
        }

        // Always call connectAndAuthenticate - it handles both connection and auth sending
        logToStatus("Attempting WebSocket connection and authentication...");
        webSocketService.connectAndAuthenticate(token); 
        
        // Removed the separate call to the non-existent sendAuthentication
        /*
        if (!webSocketService.isConnected()) {
            logToStatus("Connecting to WebSocket...");
            webSocketService.connectAndAuthenticate(token);
        } else {
            logToStatus("WebSocket already connected. Sending authentication...");
            webSocketService.sendAuthentication(token);
        }
        */
    }

    // Helper to reset UI state when session ends or fails to start
    private void resetSessionUI() {
        sessionCodeLabel.setText("Session Code: N/A");
        connectedStudents.clear(); 
        currentWebsiteList.clear(); // Clear settings lists
        currentAppList.clear();
        websiteListLabel.setText("Website Management (N/A)"); // Reset label
        activeSession = null; 
        currentSessionType = null;
        endSessionButton.setDisable(true);
        copySessionCodeButton.setDisable(true);
        startSessionButton.setDisable(false);
        // Disable settings tab
        if (mainTabPane.getTabs().size() > 1) {
            mainTabPane.getTabs().get(1).setDisable(true);
        }
        isWebSocketAuthenticated = false; 
        currentSessionType = null; // Also reset session type
        // Hide panes directly using setVisible/setManaged
        if (websiteManagementPane != null) {
            websiteManagementPane.setVisible(false);
            websiteManagementPane.setManaged(false);
        }
        if (appManagementPane != null) {
             appManagementPane.setVisible(false);
             appManagementPane.setManaged(false);
        }
        websiteListLabel.setText("Website Management (N/A)"); 
        // ... reset buttons, disable settings tab ...
        updateButtonStates(); 
    }

    // --- WebSocketListener Implementation ---

    @Override
    public void onWebSocketOpen() {
        Platform.runLater(() -> {
            logToStatus("WebSocket Connected. Authenticating...");
        });
    }

    @Override
    public void onWebSocketMessage(WebSocketMessage message) {
        Platform.runLater(() -> {
            // System.out.println("Teacher WS Received: Type=" + message.getType() + ", Payload=" + message.getPayload()); // Debug more
            switch (message.getType()) {
                case "response":
                    handleWebSocketResponse(message);
                    break;
                case "initial_student_list":
                    handleInitialStudentList(message);
                    break;
                case "student_joined":
                     handleStudentJoined(message);
                     break;
                 case "student_left": 
                     handleStudentLeft(message);
                     break;
                case "force_disconnect":
                     handleForceDisconnect();
                     break;
                // Listen for updates pushed by server after our own actions or others
                case "settings_update":
                     handleSettingsUpdate(message); // Handle general settings updates
                     break;
                 case "app_added": // If server broadcasts this back
                     handleAddAppResponse(message, true); // Treat as success
                     break;
                 case "app_removed": // If server broadcasts this back
                     handleDeleteAppResponse(message, true); // Treat as success
                     break;
                 case "student_data": // Add case for student logs/updates
                     handleStudentData(message);
                     break;
                 // Ignore messages meant only for students 
                 case "command":
                 case "initial_settings": // This is for students
                 case "connection_ack": 
                     break; 
                default:
                     System.out.println("Teacher received unhandled WS type: " + message.getType());
            }
        });
    }

    // --- WebSocket Message Handler Helpers ---
    
    private void handleWebSocketResponse(WebSocketMessage message) {
         // Handle authentication success FIRST
        if (message.getPayload() != null && 
            message.getPayload().containsKey("message") &&
            String.valueOf(message.getPayload().get("message")).toLowerCase().contains("authentication successful")) {
            
            logToStatus("WebSocket Authenticated.");
            isWebSocketAuthenticated = true; // Set flag
            updateButtonStates(); // Enable buttons now that we are authenticated
            requestSessionData(); // Request data AFTER auth
            return; // Handled authentication response
        }

        // If not an auth success response, check if it might be settings or apps response
        // Check for settings response (heuristic)
        if (message.getPayload() != null && message.getPayload().containsKey("sessionType")) { 
            logToStatus("Processing potential settings response...");
            handleSettingsUpdate(message); 
            return; // Handled settings response
        }
        
        // Check for apps response (heuristic)
        if (message.getPayload() != null && message.getPayload().containsKey("apps")) { 
            logToStatus("Processing potential apps response...");
            handleAppListUpdate(message);
            return; // Handled apps response
        }
        
        // Handle other specific responses with request IDs if implemented
        if (message.getRequestId() != null) { 
             boolean success = "success".equalsIgnoreCase(message.getStatus());
             if (success) {
                 logToStatus("Action successful (Req ID: " + message.getRequestId() + ").");
                 // UI updates are now primarily handled by broadcast messages (app_added, settings_update)
             } else {
                 String errorMsg = "Action failed";
                 if(message.getPayload() != null && message.getPayload().get("message") instanceof String) {
                    errorMsg += ": " + message.getPayload().get("message");
                 }
                 logToStatus(errorMsg + " (Req ID: " + message.getRequestId() + ").");
                 showAlert("Action Failed", errorMsg);
             }
             return; // Handled response to specific action
         }
         
         // Log if it's some other kind of response we didn't specifically handle
         logToStatus("Received generic WS response: " + message.getPayload());
    }
    
    // Method to explicitly request data from backend via WS
    private void requestSessionData() {
        if (webSocketService != null && webSocketService.isConnected()) {
             logToStatus("Requesting session settings and apps...");
             // Assign Request IDs if WebSocketService supports it
            webSocketService.sendMessage("get_session_settings", null); 
            webSocketService.sendMessage("get_apps", null);
        } else {
            logToStatus("Cannot request session data: WebSocket not connected.");
        }
    }

    private void handleInitialStudentList(WebSocketMessage message) {
        if (message.getPayload() != null && message.getPayload().get("students") instanceof List) {
             // Expecting List<Map<String, String>> for student info
             List<Map<String, Object>> studentsData = (List<Map<String, Object>>) message.getPayload().get("students");
            logToStatus("Received initial student list (" + studentsData.size() + ").");
            connectedStudents.clear();
            studentLogs.clear(); // Clear logs for new session
            studentsData.forEach(studentMap -> {
                String id = (String) studentMap.get("studentId");
                String name = (String) studentMap.get("studentName");
                String roll = (String) studentMap.get("rollNo");
                String cls = (String) studentMap.get("class");
                if (id != null) {
                    connectedStudents.add(new StudentInfo(id, name != null ? name : id, roll, cls));
                    studentLogs.put(id, new ArrayList<>()); // Initialize log list
                }
            });
             if (connectedStudents.isEmpty()) {
                // Don't add placeholder string to StudentInfo list
                // studentListView.setPlaceholder(new Label("Waiting for students...")); // Set placeholder instead
            }
        }
    }

    private void handleStudentJoined(WebSocketMessage message) {
         if (message.getPayload() != null) {
             Map<String, Object> payload = message.getPayload();
             String studentId = (String) payload.get("studentId");
             String studentName = (String) payload.get("studentName");
             String rollNo = (String) payload.get("rollNo");
             String studentClass = (String) payload.get("class");
             
             if (studentId != null) {
                 logToStatus("Student joined: " + studentName + " (" + studentId + ")");
                 StudentInfo newStudent = new StudentInfo(studentId, studentName != null ? studentName : studentId, rollNo, studentClass);
                 // Avoid duplicates if message is received multiple times
                 if (connectedStudents.stream().noneMatch(s -> s.studentId().equals(studentId))) {
                     connectedStudents.add(newStudent);
                     studentLogs.put(studentId, new ArrayList<>()); // Initialize log list
                 }
             }
         }
    }
    
    private void handleStudentLeft(WebSocketMessage message) {
        if (message.getPayload() != null && message.getPayload().containsKey("studentId")) {
             String studentId = (String) message.getPayload().get("studentId");
             StudentInfo leavingStudent = connectedStudents.stream()
                                    .filter(s -> s.studentId().equals(studentId))
                                    .findFirst().orElse(null);
                                    
             String logMessage;
             if (leavingStudent != null) {
                logToStatus("Student left: " + leavingStudent.studentName() + " (" + studentId + ")");
                logMessage = "Left session.";
                connectedStudents.remove(leavingStudent);
             } else {
                 logToStatus("Student left (ID): " + studentId);
                 logMessage = "Left session (details unavailable).";
                 connectedStudents.removeIf(s -> s.studentId().equals(studentId));
             }
             
             // Add log entry for the leaving student
             String timeStamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
             String logEntry = String.format("[%s] [SESSION] %s", timeStamp, logMessage);
             List<String> logs = studentLogs.computeIfAbsent(studentId, k -> new ArrayList<>()); // Get or create log list
             logs.add(logEntry); // Add the log
             // Don't remove the log list from the map immediately, keep history?
             // studentLogs.remove(studentId); 

             if (connectedStudents.isEmpty()) {
                 // studentListView.setPlaceholder(new Label("Waiting for students..."));
             }
         }
    }
    
    private void handleForceDisconnect() {
        logToStatus("Disconnected by server (likely newer connection). Session invalid.");
         showAlert("Session Conflict", "Another teacher connection for this session was established. This session is no longer valid.");
         resetSessionUI();
    }

    // Handles settings updates from initial fetch or server push
    private void handleSettingsUpdate(WebSocketMessage message) {
        if (message.getPayload() == null) return;
        logToStatus("Processing session settings data..."); 
        Map<String, Object> payload = message.getPayload();
        boolean listUpdated = false;
        boolean typeChanged = false;

        // --- Start Critical Section: Update Internal State --- 
        if (payload.containsKey("sessionType")) {
            String newType = (String) payload.get("sessionType");
            if (this.currentSessionType == null || !newType.equals(this.currentSessionType)) { 
                this.currentSessionType = newType;
                typeChanged = true; 
                System.out.println("handleSettingsUpdate: currentSessionType set to: " + this.currentSessionType);
            }
        }
        if (payload.containsKey("blockUsb")) {
             this.currentUsbBlocked = Boolean.TRUE.equals(payload.get("blockUsb"));
        }
        
        // Update website list based on the CURRENT session type
        if (this.currentSessionType != null) { // Ensure type is set before checking lists
            if (("BLOCK_WEBSITES".equals(this.currentSessionType) || "BLOCK_APPS_WEBSITES".equals(this.currentSessionType)) 
                && payload.containsKey("websiteBlacklist")) {
                 List<String> list = (List<String>) payload.get("websiteBlacklist");
                 currentWebsiteList.setAll(list != null ? list : List.of());
                 listUpdated = true;
            } else if ("ALLOW_WEBSITES".equals(this.currentSessionType) && payload.containsKey("websiteWhitelist")) {
                 List<String> list = (List<String>) payload.get("websiteWhitelist");
                 currentWebsiteList.setAll(list != null ? list : List.of());
                 listUpdated = true;
            } else if (typeChanged) { // Clear list if type changed and specific list wasn't present
                 currentWebsiteList.clear();
                 listUpdated = true; 
            }
        } else {
            System.err.println("handleSettingsUpdate: Cannot process website lists, currentSessionType is null.");
        }
       // --- End Critical Section --- 
        
        // Update UI visibility ONLY AFTER processing all settings
        updateSettingsPanesVisibility(); 
    }
    
    // Handles app list updates - ONLY update data, do not trigger UI visibility change
    private void handleAppListUpdate(WebSocketMessage message) {
         if (message.getPayload() != null && message.getPayload().get("apps") instanceof List) {
             List<Map<String, Object>> appsData = (List<Map<String, Object>>) message.getPayload().get("apps");
             logToStatus("Processing app list data..."); 
             currentAppList.clear();
             if (appsData != null) {
                 appsData.stream()
                     .filter(app -> app.containsKey("app_name"))
                     .map(app -> (String) app.get("app_name"))
                     .forEach(currentAppList::add);
             }
             // Visibility is handled by handleSettingsUpdate after sessionType is known
             // We might need to update button states if app list changes affect them
             Platform.runLater(this::updateButtonStates); // Ensure button state is updated
         }
    }
    
    // Handles confirmation of app addition (from broadcast)
    private void handleAddAppResponse(WebSocketMessage message, boolean success) {
        if (success && message.getPayload() != null && message.getPayload().containsKey("app_name")) {
            String appName = (String) message.getPayload().get("app_name");
            if (!currentAppList.contains(appName)) {
                currentAppList.add(appName); // Add to UI list if successful
                logToStatus("App added: " + appName);
            }            
            appInputField.clear(); // Clear input field
        } else {
            // Handle failure if needed (already handled by generic response handler?)
            // showAlert("Add App Failed", "Could not add the application.");
        }
    }
    
     // Handles confirmation of app deletion (from broadcast)
    private void handleDeleteAppResponse(WebSocketMessage message, boolean success) {
        if (success && message.getPayload() != null && message.getPayload().containsKey("app_name")) {
            String appName = (String) message.getPayload().get("app_name");
            if (currentAppList.contains(appName)) {
                currentAppList.remove(appName); // Remove from UI list if successful
                logToStatus("App removed: " + appName);
            }            
        } else {
             // Handle failure if needed
            // showAlert("Delete App Failed", "Could not remove the application.");
        }
    }
    
    private void updateWebsiteListLabel() {
        if ("BLOCK_WEBSITES".equals(this.currentSessionType) || "BLOCK_APPS_WEBSITES".equals(this.currentSessionType)) {
            websiteListLabel.setText("Blocked Websites (Blacklist)");
        } else if ("ALLOW_WEBSITES".equals(this.currentSessionType)) {
            websiteListLabel.setText("Allowed Websites (Whitelist)");
        } else {
             websiteListLabel.setText("Website Management (N/A - App Mode Only)");
        }
    }

    // Method to control visibility using setVisible/setManaged
    private void updateSettingsPanesVisibility() {
        // Defensive null checks
        if (appManagementPane == null || websiteManagementPane == null || settingsContainerVBox == null) {
            System.err.println("Error: Settings panes or container VBox are null. Cannot update visibility.");
            return; 
        }

        boolean sessionActiveAndAuthed = activeSession != null && isWebSocketAuthenticated;
        // Ensure currentSessionType is non-null before checking mode
        boolean isWebsiteMode = this.currentSessionType != null && 
                                ("BLOCK_WEBSITES".equals(this.currentSessionType) 
                                || "ALLOW_WEBSITES".equals(this.currentSessionType)
                                || "BLOCK_APPS_WEBSITES".equals(this.currentSessionType));
                           
        System.out.println("updateSettingsPanesVisibility: sessionActiveAndAuthed=" + sessionActiveAndAuthed +
                           ", currentSessionType='" + this.currentSessionType +
                           "', isWebsiteMode=" + isWebsiteMode);

        // Set visibility using setVisible/setManaged
        boolean showAppPane = sessionActiveAndAuthed;
        appManagementPane.setVisible(showAppPane);
        appManagementPane.setManaged(showAppPane);
       
        boolean showWebsitePane = isWebsiteMode && sessionActiveAndAuthed;
        websiteManagementPane.setVisible(showWebsitePane);
        websiteManagementPane.setManaged(showWebsitePane);
        
        // Update label only if pane is meant to be shown
        if(showWebsitePane) {
             updateWebsiteListLabel(); 
        } else {
             websiteListLabel.setText("Website Management (N/A)"); // Reset label if hidden
        }
        
        System.out.println("updateSettingsPanesVisibility: Setting AppPane Visible=" + showAppPane + ", Managed=" + showAppPane);
        System.out.println("updateSettingsPanesVisibility: Setting WebsitePane Visible=" + showWebsitePane + ", Managed=" + showWebsitePane);

        updateButtonStates(); // Update buttons based on visibility
        
        // Force layout update
        settingsContainerVBox.requestLayout();
        System.out.println("updateSettingsPanesVisibility: Requested layout update for container.");
    }

    // --- Settings Tab Action Handlers ---
    @FXML
    private void handleAddWebsiteAction(ActionEvent event) {
        String websiteInput = websiteInputField.getText().trim(); // Keep original case for a moment
        if (websiteInput.isEmpty()) return;

        // --- Input Normalization (Lowercase, Remove Protocol/Path) --- 
        String normalizedWebsite = websiteInput.toLowerCase();
        normalizedWebsite = normalizedWebsite.replaceFirst("^https?://", "");
        int slashIndex = normalizedWebsite.indexOf('/');
        if (slashIndex != -1) {
            normalizedWebsite = normalizedWebsite.substring(0, slashIndex);
        }
        // --- End Initial Normalization ---

        // --- Domain Validation --- 
        // Check if it contains at least one dot and doesn't end with a dot.
        if (normalizedWebsite.isEmpty() || !normalizedWebsite.contains(".") || normalizedWebsite.endsWith(".")) { 
            showAlert("Invalid Domain", 
                      "Please enter a valid website domain name (e.g., example.com, www.example.org).");
            return;
        }
        // --- End Domain Validation ---
        
        // --- Normalization for Comparison/Storage (Get Base Domain) --- 
        String baseDomain = normalizedWebsite.startsWith("www.") ? normalizedWebsite.substring(4) : normalizedWebsite;
        if (baseDomain.isEmpty()) { // Handle case like entering just "www."
             showAlert("Invalid Domain", "Please enter a valid website domain name.");
             return;
        }
        // --- End Base Domain Normalization ---
        
        // ***** START: Prevent blocking backend domain *****
        // Extract the base domain
        String backendDomain = Main.getBackendApiDomain(); // Use domain from Main class instead of hardcoding
        if (baseDomain.equals(backendDomain)) {
            showAlert("Action Not Allowed", 
                      "Blocking the application's backend domain (" + backendDomain + ") is not permitted.");
            return;
        }
        // ***** END: Prevent blocking backend domain *****

        // Check if website management is allowed in the current mode
        boolean allowedMode = "BLOCK_WEBSITES".equals(currentSessionType) 
                         || "ALLOW_WEBSITES".equals(currentSessionType)
                         || "BLOCK_APPS_WEBSITES".equals(currentSessionType);

        if (!isWebSocketAuthenticated || !allowedMode) { 
             logToStatus("Cannot add website: Not authenticated or wrong session mode.");
             return;
         }
         
        // Check for duplicates using the base domain
        final String finalBaseDomain = baseDomain; // For lambda
        if (currentWebsiteList.stream().anyMatch(existing -> getBaseDomain(existing).equals(finalBaseDomain))) { 
            showAlert("Duplicate", "This website domain (or its www variant) is already in the list.");
            return;
        }
        logToStatus("Adding website: " + finalBaseDomain); // Log base domain
        
        // Create a *new* list with the added item (send base domain version)
        List<String> updatedList = new java.util.ArrayList<>(currentWebsiteList);
        // Add the base domain to the list that's maintained visually and sent to backend
        updatedList.add(finalBaseDomain); 

        // Determine list type for the payload
        String listType;
        if ("ALLOW_WEBSITES".equals(currentSessionType)) {
            listType = "whitelist";
        } else { // BLOCK_WEBSITES or BLOCK_APPS_WEBSITES
            listType = "blacklist";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", listType);
        payload.put("websites", updatedList); // Send list containing base domains
        
        // Send update via WebSocket
        webSocketService.sendMessage("set_website_list", payload);
        
        // Optimistically update UI? Or wait for confirmation?
        // Let's wait for confirmation via settings_update message from server
        websiteInputField.clear(); // Clear input field after attempting to add
    }

    @FXML
    private void handleDeleteWebsiteAction(ActionEvent event) {
        String selectedWebsite = websiteListView.getSelectionModel().getSelectedItem();
        if (selectedWebsite == null) return;

        // Check if website management is allowed in the current mode
        boolean allowedMode = "BLOCK_WEBSITES".equals(currentSessionType) 
                         || "ALLOW_WEBSITES".equals(currentSessionType)
                         || "BLOCK_APPS_WEBSITES".equals(currentSessionType);

        if (!isWebSocketAuthenticated || !allowedMode) { 
             logToStatus("Cannot delete website: Not authenticated or wrong session mode.");
             return;
         }
        logToStatus("Deleting website: " + selectedWebsite);
        // Create a *new* list without the deleted item
        List<String> updatedList = new java.util.ArrayList<>(currentWebsiteList);
        updatedList.remove(selectedWebsite);
        
        String listType;
        if ("ALLOW_WEBSITES".equals(currentSessionType)) {
            listType = "whitelist";
        } else { // BLOCK_WEBSITES or BLOCK_APPS_WEBSITES
            listType = "blacklist";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", listType);
        payload.put("websites", updatedList);

        // Send update via WebSocket
        webSocketService.sendMessage("set_website_list", payload);
        
         // Optimistically update UI? Or wait for confirmation?
         // Let's wait for confirmation via settings_update message from server
    }

    @FXML
    private void handleAddAppAction(ActionEvent event) {
        String appName = appInputField.getText().trim();
        if (appName.isEmpty()) return;

        if (!isWebSocketAuthenticated) { // Check auth state
            logToStatus("Cannot add app: WebSocket not authenticated.");
            showAlert("Authentication Error", "Please wait for WebSocket to authenticate.");
            return;
        }
        
        // Automatically append .exe if not present
        String processedAppName = appName.toLowerCase();
        if (!processedAppName.endsWith(".exe")) {
            processedAppName += ".exe";
            logToStatus("Appended .exe to app name: " + processedAppName);
        }

         if (currentAppList.contains(processedAppName)) { // Check against processed name
            showAlert("Duplicate", "Application already exists in the blocked list.");
            return;
        }

        logToStatus("Requesting to add app: " + processedAppName);
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_name", processedAppName); // Send processed name
        webSocketService.sendMessage("add_app", payload);
    }

    @FXML
    private void handleDeleteAppAction(ActionEvent event) {
        String selectedApp = appListView.getSelectionModel().getSelectedItem();
        if (selectedApp == null) return;
        
        if (!isWebSocketAuthenticated) { // Check auth state
            logToStatus("Cannot delete app: WebSocket not authenticated.");
             showAlert("Authentication Error", "Please wait for WebSocket to authenticate.");
            return;
        }
        logToStatus("Requesting to delete app: " + selectedApp);
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_name", selectedApp);
        webSocketService.sendMessage("delete_app", payload);
         // Wait for response/broadcast before updating list
    }

    // Centralized method to update enable/disable state of buttons
    private void updateButtonStates() {
        boolean sessionActive = activeSession != null;
        boolean itemSelectedWebsite = websiteListView.getSelectionModel().getSelectedItem() != null;
        boolean itemSelectedApp = appListView.getSelectionModel().getSelectedItem() != null;
        
        // Settings buttons enabled only if session is active AND websocket is authenticated
        boolean canModifySettings = sessionActive && isWebSocketAuthenticated;
        
        addWebsiteButton.setDisable(!canModifySettings || !websiteManagementPane.isVisible());
        deleteWebsiteButton.setDisable(!canModifySettings || !websiteManagementPane.isVisible() || !itemSelectedWebsite);
        
        addAppButton.setDisable(!canModifySettings);
        deleteAppButton.setDisable(!canModifySettings || !itemSelectedApp);
        
        // Also update start/end/copy buttons based on activeSession
        startSessionButton.setDisable(sessionActive);
        endSessionButton.setDisable(!sessionActive);
        copySessionCodeButton.setDisable(!sessionActive);
        // Logout is always enabled for now
        // setLoadingState might override these temporarily
    }

    // Helper to log messages to the status label
    private void logToStatus(String message) {
        System.out.println("STATUS: " + message);
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    @Override
    public void onWebSocketClose(int code, String reason) {
        Platform.runLater(() -> {
            logToStatus("WebSocket Closed: " + reason + " (Code: " + code + ")");
        });
    }

    @Override
    public void onWebSocketError(String message, Exception ex) {
        Platform.runLater(() -> {
             logToStatus("WebSocket Error: " + message + (ex != null ? " - " + ex.getMessage() : ""));
             // Removed showAlert from here to avoid potential UI lockups on rapid errors
             // Consider logging more details if needed
             if (ex != null) {
                System.err.println("WebSocket Exception Details:");
                ex.printStackTrace();
             }
        });
    }

    // --- Student Detail Window --- 
    private void openStudentDetailWindow(StudentInfo student) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/cmms/ui/student_detail_view.fxml"));
            Parent root = loader.load();

            StudentDetailController controller = loader.getController();
            List<String> logs = studentLogs.getOrDefault(student.studentId(), List.of("No logs available."));
            controller.setStudentDetails(student);
            controller.setLogs(logs);

            Stage detailStage = new Stage();
            detailStage.setTitle("Student Details - " + student.studentName());
            detailStage.setScene(new Scene(root));
            // detailStage.initModality(Modality.WINDOW_MODAL); // Optional: Block interaction with main window
            // detailStage.initOwner(stage); // Optional: Set owner
            detailStage.show();

        } catch (IOException e) {
            System.err.println("Failed to load student detail view: " + e.getMessage());
            e.printStackTrace();
            showAlert("UI Error", "Could not open student detail window.");
        }
    }

    // Handle student data messages (logs, errors, etc.)
    private void handleStudentData(WebSocketMessage message) {
        if (message.getPayload() == null) return;
        Map<String, Object> payload = message.getPayload();
        String studentId = (String) payload.get("studentId");
        String updateType = (String) payload.get("updateType");
        Object data = payload.get("data"); // Can be Map or other type

        if (studentId == null || updateType == null) {
            System.err.println("Received student_data message with missing fields.");
            return;
        }

        // Format the log message
        String timeStamp = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        String logEntry = String.format("[%s] [%s] %s", timeStamp, updateType.toUpperCase(), data); // Simple format

        // Add log to the specific student's list
        List<String> logs = studentLogs.computeIfAbsent(studentId, k -> new ArrayList<>());
        logs.add(logEntry);
        
        System.out.println("Log added for student " + studentId + ": " + logEntry);

        // TODO: If the detail window for this student is open, update it.
    }

    // ADDED: Method for the calling controller (e.g., Main or Config) to set the type
    public void setDesiredSessionType(String sessionType) {
        System.out.println("TeacherDashboardController: Desired session type set to: " + sessionType);
        this.desiredSessionType = sessionType;
        // Optionally, update a label here if needed before session start
    }

    // Helper method for normalization (used in duplicate check)
    private String normalizeWebsite(String input) {
        if (input == null) return "";
        String normalized = input.trim().toLowerCase();
        normalized = normalized.replaceFirst("^https?://", "");
        // normalized = normalized.replaceFirst("^www\\.", ""); // Don't remove www here for comparison
        int slashIndex = normalized.indexOf('/');
        if (slashIndex != -1) {
            normalized = normalized.substring(0, slashIndex);
        }
        return normalized;
    }

    // Helper method for getting base domain (for duplicate check)
    private String getBaseDomain(String input) {
        if (input == null) return "";
        String normalized = input.trim().toLowerCase();
        // Remove protocol/path first (if needed, though list should be clean)
        normalized = normalized.replaceFirst("^https?://", "");
        int slashIndex = normalized.indexOf('/');
        if (slashIndex != -1) {
            normalized = normalized.substring(0, slashIndex);
        }
        // Remove www.
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        return normalized;
    }
} 