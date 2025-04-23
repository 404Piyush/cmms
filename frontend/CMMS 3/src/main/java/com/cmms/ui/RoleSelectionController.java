package com.cmms.ui;

import com.cmms.Main;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;

/**
 * Controller for the initial role selection screen.
 */
public class RoleSelectionController {

    @FXML
    private Button teacherButton;

    @FXML
    private Button studentButton;

    @FXML
    void handleTeacherButtonAction(ActionEvent event) {
        System.out.println("Teacher role selected.");
        Main.loadTeacherConfigView(); // Navigate to teacher configuration
    }

    @FXML
    void handleStudentButtonAction(ActionEvent event) {
        System.out.println("Student role selected.");
        Main.loadStudentJoinView(); // Navigate to student join screen
    }

    @FXML
    public void initialize() {
        // Initialization logic if needed when the FXML is loaded
        System.out.println("RoleSelectionController initialized.");
    }
} 