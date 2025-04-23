package com.cmms.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;

import java.util.List;

public class StudentDetailController {

    @FXML private Label studentNameLabel;
    @FXML private Label rollNoLabel;
    @FXML private Label classLabel;
    @FXML private Label studentIdLabel;
    @FXML private ListView<String> logListView;

    private ObservableList<String> logs = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Bind the observable list to the ListView
        logListView.setItems(logs);
    }

    /**
     * Sets the student information to display.
     * Called by the TeacherDashboardController after loading the FXML.
     * @param student The StudentInfo object containing details.
     */
    public void setStudentDetails(StudentInfo student) {
        if (student != null) {
            studentNameLabel.setText(student.studentName() != null ? student.studentName() : "N/A");
            rollNoLabel.setText(student.rollNo() != null ? student.rollNo() : "N/A");
            classLabel.setText(student.studentClass() != null ? student.studentClass() : "N/A");
            studentIdLabel.setText(student.studentId());
        } else {
            studentNameLabel.setText("Error");
            rollNoLabel.setText("Error");
            classLabel.setText("Error");
            studentIdLabel.setText("Error");
        }
    }

    /**
     * Sets the logs for the student.
     * Called by the TeacherDashboardController.
     * @param studentLogs The list of log entries for the student.
     */
    public void setLogs(List<String> studentLogs) {
        logs.clear();
        if (studentLogs != null && !studentLogs.isEmpty()) {
            logs.addAll(studentLogs);
        } else {
            logs.add("No log entries available.");
        }
        // Scroll to the bottom
        logListView.scrollTo(logs.size() - 1);
    }
    
    // TODO: Implement method to append new log entries if window is open
    // public void appendLog(String logEntry) {
    //     if (logs.get(0).equals("No log entries available.")) {
    //         logs.clear(); // Clear placeholder if it exists
    //     }
    //     logs.add(logEntry);
    //     logListView.scrollTo(logs.size() - 1);
    // }
} 