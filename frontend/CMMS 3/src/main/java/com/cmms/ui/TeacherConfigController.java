package com.cmms.ui;

import com.cmms.Main;
import com.cmms.ServiceAwareController;
import com.cmms.dto.ApiResponse;
import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.Node;

import java.util.UUID;

/**
 * Controller for the Teacher Session Configuration screen.
 */
public class TeacherConfigController implements ServiceAwareController {

    @FXML private ToggleGroup sessionTypeToggleGroup;
    @FXML private RadioButton blockAppsRadio;
    @FXML private RadioButton blockAppsWebsitesRadio;
    @FXML private RadioButton allowWebsitesRadio;
    @FXML private CheckBox blockUsbCheckbox;
    @FXML private Button startSessionButton;
    @FXML private Button backButton;
    @FXML private ProgressIndicator loadingIndicator;
    @FXML private Label statusLabel;

    private ApiService apiService;
    // private WebSocketService webSocketService; // Not needed here directly

    @Override
    public void setApiService(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public void setWebSocketService(WebSocketService webSocketService) {
        // this.webSocketService = webSocketService;
    }

    @FXML
    public void initialize() {
        // Set user data for radio buttons (used by getSelectedSessionType)
        blockAppsRadio.setUserData("BLOCK_APPS");
        blockAppsWebsitesRadio.setUserData("BLOCK_APPS_WEBSITES");
        allowWebsitesRadio.setUserData("ALLOW_WEBSITES"); // TODO: Backend needs to support this combination if selected
        // allowWebsitesRadio.setDisable(true); // REMOVED: Enable the radio button

        // Set default selection (optional)
        sessionTypeToggleGroup.selectToggle(blockAppsRadio);

        statusLabel.setText("");
        loadingIndicator.setVisible(false);
    }

    @FXML
    void handleStartSessionAction(ActionEvent event) {
        String selectedType = getSelectedSessionType();
        // boolean blockUsb = blockUsbCheckbox.isSelected(); // Keep this if needed for dashboard?

        if (selectedType == null) {
            statusLabel.setText("Please select a session type.");
            Main.showError("Input Required", "Please select a session type.");
            return;
        }
        
        System.out.println("ConfigController: Retrieved Session Type = " + selectedType);
        
        // --- REMOVED API Call Task --- 
        // setLoadingState(true, "Creating session...");
        // Task<ApiResponse<Object>> createTask = ... removed ...
        // new Thread(createTask).start();
        
        // --- Directly load dashboard, passing the selected type --- 
        Main.loadTeacherDashboardView(selectedType); 
    }

    @FXML
    void handleBackAction(ActionEvent event) {
        Main.loadRoleSelectionView(); // Go back to role selection
    }

    private String getSelectedSessionType() {
        Toggle selectedToggle = sessionTypeToggleGroup.getSelectedToggle();
        if (selectedToggle != null) {
            return (String) selectedToggle.getUserData();
        }
        return null; 
    }

    // --- REMOVED setLoadingState method as it's no longer needed here ---
    /*
    private void setLoadingState(boolean isLoading, String statusText) {
        loadingIndicator.setVisible(isLoading);
        statusLabel.setText(isLoading ? statusText : "");
        startSessionButton.setDisable(isLoading);
        backButton.setDisable(isLoading);
        sessionTypeToggleGroup.getToggles().forEach(toggle -> ((Node)toggle).setDisable(isLoading));
        blockUsbCheckbox.setDisable(isLoading);
    }
    */
} 