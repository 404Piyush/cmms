package com.cmms.driverManager;

import com.cmms.utils.MongoDBHelper;
import org.bson.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class DriverManager {
    private static Set<String> previousUSBDevices = new HashSet<>();

    public static void monitorUSB(
            String sessionCode,
            String studentPcId,
            String studentName,
            String className,
            String rollNo
    ) {
        try {
            previousUSBDevices = getConnectedUSBDevices();
            while (true) {
                Set<String> currentUSBDevices = getConnectedUSBDevices();

                // Detect newly connected devices
                currentUSBDevices.removeAll(previousUSBDevices);
                if (!currentUSBDevices.isEmpty()) {
                    notifyAdmin(
                            sessionCode,
                            studentPcId,
                            studentName,
                            className,
                            rollNo,
                            "New USB device connected: " + currentUSBDevices
                    );
                }

                previousUSBDevices = currentUSBDevices;
                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static Set<String> getConnectedUSBDevices() {
        Set<String> usbDevices = new HashSet<>();
        try {
            Process process;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                process = Runtime.getRuntime().exec("wmic path Win32_USBControllerDevice get Dependent");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("DeviceID")) {
                        String deviceId = line.split("=")[1].replace("\"", "").trim();
                        usbDevices.add(deviceId);
                    }
                }
            } else {
                process = Runtime.getRuntime().exec("lsusb");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    usbDevices.add(line.trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return usbDevices;
    }

    private static void notifyAdmin(
            String sessionCode,
            String studentPcId,
            String studentName,
            String className,
            String rollNo,
            String message
    ) {
        MongoDBHelper.getCollection("notifications").insertOne(
                new Document()
                        .append("session_code", sessionCode)
                        .append("pc_id", studentPcId)
                        .append("student_name", studentName)
                        .append("class", className)
                        .append("roll_no", rollNo)
                        .append("message", message)
                        .append("read", false)
        );
    }
}