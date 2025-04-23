package com.cmms.taskManager;

import com.cmms.utils.MongoDBHelper;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TaskManagement {
    public static void monitorApps(String sessionCode, String studentPcId) {
        while (true) {
            try {
                List<String> blacklistedApps = getBlacklistedApps();
                for (String app : blacklistedApps) {
                    if (isAppRunning(app)) {
                        terminateApp(app);
                        notifyAdmin(sessionCode, studentPcId, "Terminated blacklisted app: " + app);
                    }
                }
                TimeUnit.SECONDS.sleep(3);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }



    private static List<String> getBlacklistedApps() {
        MongoCollection<Document> blacklistedApps = MongoDBHelper.getCollection("blacklisted_apps");
        List<String> apps = new ArrayList<>();
        for (Document doc : blacklistedApps.find()) {
            apps.add(doc.getString("app_name") +".exe");
        }
        return apps;
    }

    private static boolean isAppRunning(String appName) {
        try {
            Process process;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                process = Runtime.getRuntime().exec("tasklist /FI \"IMAGENAME eq " + appName + "\"");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(appName)) return true;
                }
            } else {
                process = Runtime.getRuntime().exec("pgrep -x " + appName);
                return process.waitFor() == 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    private static void terminateApp(String appName) {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("taskkill /F /IM " + appName);
            } else {
                Runtime.getRuntime().exec("pkill -9 " + appName);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private static void notifyAdmin(String sessionCode, String studentPcId, String message) {
        MongoCollection<Document> notifications = MongoDBHelper.getCollection("notifications");
        notifications.insertOne(new Document()
                .append("session_code", sessionCode)
                .append("pc_id", studentPcId)
                .append("message", message)
                .append("read", false));
    }
}