package com.cmms;

import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
import com.cmms.dto.SessionSettings;
import com.cmms.ui.RoleSelectionController;
import com.cmms.ui.StudentMonitorController;
import com.cmms.ui.TeacherDashboardController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main application class for the CMMS Client (using JavaFX).
 * Initializes services and loads the initial role selection UI.
 */
public class Main extends Application {

    // Configuration - Replace with your actual backend URLs
    private static final String API_BASE_URL = "http://localhost:5001/api";
    private static final String WEBSOCKET_URL = "ws://localhost:5001";

    private static Stage primaryStage;
    private static ApiService apiService;
    private static WebSocketService webSocketService;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // Initialize services
        apiService = new ApiService(API_BASE_URL);
        webSocketService = new WebSocketService(WEBSOCKET_URL);

        // Load initial role selection view
        loadRoleSelectionView();

        primaryStage.setTitle("CMMS Client");
        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Closing application...");
            if (webSocketService.isConnected()) {
                webSocketService.disconnect();
            }
            Platform.exit();
            System.exit(0); // Ensure JVM exits
        });
        primaryStage.show();
    }

    public static void loadRoleSelectionView() {
        loadScene("/com/cmms/ui/role_selection.fxml", "Select Role");
    }

    public static void loadTeacherConfigView() {
        loadScene("/com/cmms/ui/teacher_config.fxml", "Configure Session");
    }
    
    /**
     * @deprecated Use {@link #loadTeacherDashboardView(String)} instead to provide session type.
     */
    @Deprecated 
    public static void loadTeacherDashboardView() {
        System.err.println("Warning: Using deprecated loadTeacherDashboardView(). Session type will not be set.");
        loadScene("/com/cmms/ui/teacher_dashboard.fxml", "Teacher Dashboard");
    }

    // ADDED: New method to load dashboard and pass session type
    public static void loadTeacherDashboardView(String sessionType) {
        try {
            String fxmlPath = "/com/cmms/ui/teacher_dashboard.fxml";
            URL fxmlUrl = Main.class.getResource(fxmlPath);
            if (fxmlUrl == null) {
                handleFxmlLoadError(fxmlPath, null);
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            // Get the controller *after* loading
            Object controller = loader.getController();
            
            // Inject services first (if applicable)
            if (controller instanceof ServiceAwareController) {
                ((ServiceAwareController) controller).setApiService(apiService);
                ((ServiceAwareController) controller).setWebSocketService(webSocketService);
            }

            // Pass the session type to the specific controller method
            if (controller instanceof TeacherDashboardController) {
                ((TeacherDashboardController) controller).setDesiredSessionType(sessionType); // Call the new setter
            } else {
                 System.err.println("Error: Loaded controller is not an instance of TeacherDashboardController");
                 showError("Internal Error", "Could not initialize dashboard screen properly.");
                 return;
            }

            // Set the scene
            setSceneRoot(root, "Teacher Dashboard");

        } catch (IOException e) {
            handleFxmlLoadError("/com/cmms/ui/teacher_dashboard.fxml", e);
        }
    }

    public static void loadStudentJoinView() {
        loadScene("/com/cmms/ui/student_join.fxml", "Join Session");
    }

    // Overloaded method to load monitor view and pass data
    public static void loadStudentMonitorView(String authToken, SessionSettings settings, String sessionCode, String studentId, String studentName) {
        try {
            String fxmlPath = "/com/cmms/ui/student_monitor.fxml";
            URL fxmlUrl = Main.class.getResource(fxmlPath);
            if (fxmlUrl == null) {
                handleFxmlLoadError(fxmlPath, null);
                return;
            }
            
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            // Get the controller *after* loading
            Object controller = loader.getController();
            
            // Inject services first (if applicable)
            if (controller instanceof ServiceAwareController) {
                ((ServiceAwareController) controller).setApiService(apiService);
                ((ServiceAwareController) controller).setWebSocketService(webSocketService);
            }

            // Pass the session data to the specific controller method
            if (controller instanceof StudentMonitorController) {
                ((StudentMonitorController) controller).setSessionData(authToken, settings, sessionCode, studentId, studentName);
            } else {
                 System.err.println("Error: Loaded controller is not an instance of StudentMonitorController");
                 showError("Internal Error", "Could not initialize monitoring screen properly.");
                 return;
            }

            // Set the scene
            setSceneRoot(root, "Session Active");

        } catch (IOException e) {
            handleFxmlLoadError("/com/cmms/ui/student_monitor.fxml", e);
        }
    }

    // Refactored common scene setting logic
    private static void setSceneRoot(Parent root, String title) {
        Scene scene = primaryStage.getScene();
        if (scene == null) {
            scene = new Scene(root);
            primaryStage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
        primaryStage.setTitle("CMMS Client - " + title);
        primaryStage.sizeToScene();
        primaryStage.centerOnScreen();
    }
    
    // Refactored FXML loading error handling
    private static void handleFxmlLoadError(String fxmlPath, IOException e) {
         System.err.println("Failed to load FXML scene: " + fxmlPath);
         if (e != null) e.printStackTrace();
         showError("UI Load Error", "Failed to load scene: " + fxmlPath + (e != null ? "\n" + e.getMessage() : ""));
    }

    // Deprecated the old loadScene method to encourage specific loaders
    @Deprecated
    private static void loadScene(String fxmlPath, String title) {
        try {
            URL fxmlUrl = Main.class.getResource(fxmlPath);
            if (fxmlUrl == null) {
                handleFxmlLoadError(fxmlPath, null);
            return;
        }
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();
            
            Object controller = loader.getController();
            if (controller instanceof ServiceAwareController) {
                ((ServiceAwareController) controller).setApiService(apiService);
                ((ServiceAwareController) controller).setWebSocketService(webSocketService);
            }

            setSceneRoot(root, title);

        } catch (IOException e) {
             handleFxmlLoadError(fxmlPath, e);
        }
    }
    
    // Simple error dialog utility
    public static void showError(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    // Simple info dialog utility
    public static void showInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        // Check for Admin privileges (crucial for enforcement)
        // This is a basic check, might need refinement for robustness
        boolean isAdmin = System.getenv("PROCESSOR_ARCHITECTURE").equals("AMD64") ||
                          System.getenv("PROCESSOR_ARCHITEW6432") != null;
        if (!isAdmin) {
             try {
                // A simple way to check - attempt to read a restricted system dir
                // This is NOT foolproof and might fail for other reasons.
                // A better approach involves checking group membership or using JNA/Platform specific APIs.
                Process p = Runtime.getRuntime().exec("cmd /c dir C:\\Windows\\System32\\config > NUL");
                isAdmin = (p.waitFor() == 0);
             } catch (Exception ignored) { 
                 isAdmin = false; // Assume not admin if check fails
             }
        }

        System.out.println("Admin Check Result: " + isAdmin);
        if (!isAdmin) {
            System.err.println("ERROR: Application requires administrator privileges to function correctly.");
            // Show a non-JavaFX alert immediately because JavaFX might not be initialized
            javax.swing.JOptionPane.showMessageDialog(null, 
                "This application requires administrator privileges to manage system settings (apps, websites, USB).\nPlease run the application as an administrator.", 
                "Administrator Privileges Required", 
                javax.swing.JOptionPane.ERROR_MESSAGE);
            System.exit(1); // Exit if not admin
        }
        
        // Launch JavaFX application
        System.out.println("Admin check passed, launching JavaFX application...");
        launch(args);
    }
}
