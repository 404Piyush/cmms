package com.cmms.admin;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.cmms.utils.MongoDBHelper;
import org.bson.Document;

public class ConnectionListener implements Runnable {
    private final String sessionCode;

    public ConnectionListener(String sessionCode) {
        this.sessionCode = sessionCode;
    }

    @Override
    public void run() {
        MongoCollection<Document> notifications = MongoDBHelper.getCollection("notifications");

        while (true) {
            // Fetch unread notifications
            FindIterable<Document> unreadNotifications = notifications.find(
                    new Document("session_code", sessionCode).append("read", false)
            );

            for (Document notification : unreadNotifications) {
                System.out.println("\nNOTIFICATION:");
                System.out.println("Student Name: " + notification.getString("student_name"));
                System.out.println("Class: " + notification.getString("class"));
                System.out.println("Roll No: " + notification.getString("roll_no"));
                System.out.println("Message: " + notification.getString("message"));

                // Mark as read
                notifications.updateOne(
                        new Document("_id", notification.getObjectId("_id")),
                        new Document("$set", new Document("read", true))
                );
            }

            try {
                Thread.sleep(5000); // Check every 5 seconds
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}