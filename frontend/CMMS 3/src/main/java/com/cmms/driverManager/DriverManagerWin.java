package com.cmms.driverManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class DriverManagerWin implements Runnable{
    private static void startMonitoringWin() {
        Map<String, String> previousDevices = getConnectedUSBDevicesWin();
        System.out.println("Starting USB Device Monitor...");

        while (true) {
            Map<String, String> currentDevices = getConnectedUSBDevicesWin();

            for (Map.Entry<String, String> entry : currentDevices.entrySet()) {
                String uniqueKey = entry.getKey();
                String deviceName = entry.getValue();

                if (!previousDevices.containsKey(uniqueKey)) {
                    System.out.println("DriverManager: New device connected: " + deviceName + " (" + uniqueKey + ")");
                }
            }

            previousDevices = currentDevices;

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static Map<String, String> getConnectedUSBDevicesWin() {
        Map<String, String> devices = new HashMap<>();

        try {
            Process process = Runtime.getRuntime().exec("wmic path CIM_LogicalDevice where \"Description like '%USB%'\" get Description, DeviceID");

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;

            reader.readLine();

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    String[] parts = line.split("\\\\");
                    if (parts.length > 1) {
                        String uniqueKey = extractVIDPIDWin(parts[1]);
                        String description = parts[0].trim();
                        devices.put(uniqueKey, description);
                    }
                }
            }

            reader.close();
            process.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return devices;
    }

    private static String extractVIDPIDWin(String deviceID) {
        String[] components = deviceID.split("&");
        if (components.length >= 2) {
            return components[0] + "&" + components[1];
        }
        return deviceID;
    }

    @Override
    public void run() {
        startMonitoringWin();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
