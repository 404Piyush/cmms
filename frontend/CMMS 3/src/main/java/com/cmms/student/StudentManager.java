package com.cmms.student;

import com.cmms.networkManager.NetworkManagerWin;
import com.cmms.networkManager.WebsiteBlocker;
import com.cmms.taskManager.TaskManagement;
import com.cmms.driverManager.DriverManager;
import com.cmms.utils.MongoDBHelper;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class StudentManager {
    public static void startMonitoring(
            String sessionCode,
            String studentPcId,
            String studentName,
            String className,
            String rollNo
    ) {
        // Notify admin about student connection
        notifyAdmin(sessionCode, studentPcId, studentName, className, rollNo);

        // Start monitoring blacklisted apps
//        new Thread(() -> TaskManagement.monitorApps(sessionCode, studentPcId)).start();

        // Start monitoring USB devices
        new Thread(() -> DriverManager.monitorUSB(
                sessionCode,
                studentPcId,
                studentName,
                className,
                rollNo
        )).start();
 new Thread(()->     NetworkManagerWin.enableInternetRestrictions(sessionCode, studentPcId)).start();
        // Block websites
//        WebsiteBlocker.blockWebsites();
    }

    private static void notifyAdmin(
            String sessionCode,
            String studentPcId,
            String studentName,
            String className,
            String rollNo
    ) {
        MongoCollection<Document> notifications = MongoDBHelper.getCollection("notifications");
        notifications.insertOne(new Document()
                .append("session_code", sessionCode)
                .append("pc_id", studentPcId)
                .append("student_name", studentName)
                .append("class", className)
                .append("roll_no", rollNo)
                .append("message", "Student connected")
                .append("read", false));
    }
}