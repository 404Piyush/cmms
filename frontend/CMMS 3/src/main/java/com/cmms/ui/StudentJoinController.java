package com.cmms.ui;

import com.cmms.Main;
import com.cmms.ServiceAwareController;
import com.cmms.dto.ApiResponse;
import com.cmms.dto.SessionSettings;
import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
import com.cmms.logging.SessionLoggerService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller for the Student Join Session screen.
 */
public class StudentJoinController implements ServiceAwareController {

    @FXML private TextField sessionCodeField;
    @FXML private TextField studentNameField;
    @FXML private TextField rollNoField;
    @FXML private TextField classField;
    @FXML private Button joinButton;
    @FXML private Button backButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;

    private ApiService apiService;
    private WebSocketService webSocketService;

    @Override
    public void setApiService(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    @Override
    public void setSessionLoggerService(SessionLoggerService sessionLoggerService) {
        // This controller currently does not use the logger service.
        // Implementation can be added later if needed.
    }

    @FXML
    public void initialize() {
        // Pre-fill student ID (e.g., using hostname or random ID)
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            studentNameField.setText(hostname + "_Student_" + UUID.randomUUID().toString().substring(0, 4));
            rollNoField.setText(UUID.randomUUID().toString().substring(0, 4));
            classField.setText("Class_" + UUID.randomUUID().toString().substring(0, 4));
        } catch (Exception e) {
            studentNameField.setText("StudentPC_" + UUID.randomUUID().toString().substring(0, 8)); // Fallback
            rollNoField.setText("Roll_" + UUID.randomUUID().toString().substring(0, 4));
            classField.setText("Class_" + UUID.randomUUID().toString().substring(0, 4));
        }
        loadingIndicator.setVisible(false);
        statusLabel.setText("");
    }

    @FXML
    void handleJoinAction(ActionEvent event) {
        String sessionCodeInput = sessionCodeField.getText().trim().toUpperCase();
        String studentName = studentNameField.getText().trim();
        String rollNo = rollNoField.getText().trim();
        String studentClass = classField.getText().trim();

        if (sessionCodeInput.isEmpty() || studentName.isEmpty() || rollNo.isEmpty() || studentClass.isEmpty()) {
            statusLabel.setText("Please fill in all fields.");
            Main.showError("Input Error", "Session Code, Name, Roll No., and Class cannot be empty.");
            return;
        }

        setLoadingState(true);

        Map<String, String> studentDetails = new HashMap<>();
        studentDetails.put("studentName", studentName);
        studentDetails.put("rollNo", rollNo);
        studentDetails.put("class", studentClass);

        Task<ApiResponse<Object>> joinTask = new Task<>() {
            @Override
            protected ApiResponse<Object> call() throws Exception {
                return apiService.joinSession(sessionCodeInput, studentDetails);
            }
        };

        joinTask.setOnSucceeded(workerStateEvent -> {
            statusLabel.setText("");
            ApiResponse<Object> response = joinTask.getValue();

            if (response != null && response.getToken() != null && response.getSettings() != null && response.getStudentId() != null) {
                System.out.println("Join successful. Response: " + response);
                System.out.println("Token received: " + response.getToken());
                String authToken = response.getToken();
                SessionSettings settings = response.getSettings();
                String sessionCode = sessionCodeInput;
                String studentId = response.getStudentId();

                Platform.runLater(() -> {
                    try {
                        Main.loadStudentMonitorView(authToken, settings, sessionCode, studentId, 
                                                    studentName, studentClass, rollNo);
                    } catch (Exception e) {
                        System.err.println("Error loading student monitor view: " + e.getMessage());
                        e.printStackTrace();
                        Main.showError("Navigation Error", "Could not load the session monitoring screen.");
                    }
                });
            } else {
                setLoadingState(false);
                statusLabel.setText("Failed to join: Invalid response.");
                String errorMsg = "Received an invalid response from the server.";
                if (response != null) {
                    errorMsg = response.getMessage() != null ? response.getMessage() : errorMsg;
                    if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                        errorMsg = response.getErrors().stream().map(ApiResponse.ApiError::getMsg).collect(Collectors.joining(", "));
                    }
                }
                Main.showError("Join Error", errorMsg);
            }
        });

        joinTask.setOnFailed(workerStateEvent -> {
            Throwable exception = joinTask.getException();
            Platform.runLater(() -> {
                setLoadingState(false);
                statusLabel.setText("Error joining session: " + exception.getMessage());
                Main.showError("Join Session Failed", "Could not join session: \n" + exception.getMessage());
                exception.printStackTrace();
            });
        });

        new Thread(joinTask).start();
    }

    @FXML
    void handleBackAction(ActionEvent event) {
        Main.loadRoleSelectionView();
    }

    private void setLoadingState(boolean isLoading) {
        loadingIndicator.setVisible(isLoading);
        joinButton.setDisable(isLoading);
        backButton.setDisable(isLoading);
        sessionCodeField.setDisable(isLoading);
        studentNameField.setDisable(isLoading);
        rollNoField.setDisable(isLoading);
        classField.setDisable(isLoading);
        statusLabel.setText(isLoading ? "Joining session..." : "");
    }
} 