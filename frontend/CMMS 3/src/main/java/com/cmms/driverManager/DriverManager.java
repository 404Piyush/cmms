package com.cmms.driverManager;

import com.cmms.utils.MongoDBHelper;
import org.bson.Document;
import com.mongodb.client.MongoCollection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DriverManager {

    // Store InstanceIDs of detected devices, mapping InstanceID to a simple description
    private static Map<String, String> previousUsbDevices = new HashMap<>();
    // Store InstanceIDs of devices we have actively disabled
    private static Set<String> disabledDeviceInstanceIds = new HashSet<>();
    private static final Object lock = new Object(); // For thread safety if needed later
    private static volatile boolean monitoringActive = false; // Flag to control the loop
    private static Thread monitoringThread = null; // Reference to the monitoring thread

    // Keywords to identify devices that should NOT be blocked
    private static final Set<String> EXEMPT_KEYWORDS = Set.of("keyboard", "mouse", "hid");
    // More specific classes/hardware IDs could be added if needed

    public static void startMonitoring(String sessionCode, String studentPcId, String studentName, String className, String rollNo) {
        // Prevent starting multiple monitor threads
        if (monitoringActive || monitoringThread != null && monitoringThread.isAlive()) {
             System.out.println("DRIVER_MANAGER: Monitoring is already active.");
             return;
        }
        
        System.out.println("DRIVER_MANAGER: Starting USB monitoring for session " + sessionCode);
        monitoringActive = true; // Set the flag before starting the thread
        disabledDeviceInstanceIds.clear(); // Clear previously disabled devices for the new session
        
        // Store the thread reference
        monitoringThread = new Thread(() -> {
            boolean firstRun = true;
            try {
                // Initialize with currently connected devices
                synchronized (lock) {
                    previousUsbDevices = getConnectedUsbDevicesWithDetails();
                    System.out.println("DRIVER_MANAGER: Initial USB devices: " + previousUsbDevices.values());
                    // TODO: Decide if existing non-exempt devices should be disabled on start?
                    // for (Map.Entry<String, String> entry : previousUsbDevices.entrySet()) {
                    //     if (!isDeviceExempt(entry.getValue())) {
                    //         // disableDevice(entry.getKey()); ... add to disabledDeviceInstanceIds
                    //     }
                    // }
                    firstRun = false;
                }

                while (monitoringActive) { // Check the flag here
                    Thread.sleep(5000); // Check every 5 seconds
                    if (!monitoringActive) break; // Check again after sleep
                    
                    Map<String, String> currentUsbDevices;
                    synchronized (lock) {
                        // Avoid check if monitoring was stopped during sleep
                        if (!monitoringActive) break;
                        currentUsbDevices = getConnectedUsbDevicesWithDetails();
                    }

                    Set<String> newlyConnectedIds = new HashSet<>(currentUsbDevices.keySet());
                    newlyConnectedIds.removeAll(previousUsbDevices.keySet());

                    if (!newlyConnectedIds.isEmpty()) {
                        System.out.println("DRIVER_MANAGER: Detected new USB InstanceIDs: " + newlyConnectedIds);
                        for (String instanceId : newlyConnectedIds) {
                             // Avoid acting if monitoring was stopped during loop
                            if (!monitoringActive) break; 
                            
                            String description = currentUsbDevices.getOrDefault(instanceId, "Unknown Device");
                            System.out.println("DRIVER_MANAGER: New device detected: " + description + " (" + instanceId + ")");

                            if (isDeviceExempt(description)) {
                                System.out.println("DRIVER_MANAGER: Device '" + description + "' is exempt (keyboard/mouse/hid). Allowing.");
                                notifyAdmin(sessionCode, studentPcId, studentName, className, rollNo, "Allowed USB device connected: " + description);
                            } else {
                                System.out.println("DRIVER_MANAGER: Device '" + description + "' is NOT exempt. Attempting to disable...");
                                if (disableDevice(instanceId)) {
                                    System.out.println("DRIVER_MANAGER: Successfully disabled device: " + description);
                                    synchronized(lock) {
                                        // Check flag again before modifying shared state
                                        if (monitoringActive) disabledDeviceInstanceIds.add(instanceId);
                                    }
                                    notifyAdmin(sessionCode, studentPcId, studentName, className, rollNo, "Blocked USB device connected: " + description);
                                } else {
                                    System.err.println("DRIVER_MANAGER: Failed to disable device: " + description + ". Maybe requires higher privileges?");
                                    notifyAdmin(sessionCode, studentPcId, studentName, className, rollNo, "Failed to block USB device: " + description);
                                }
                            }
                        }
                    }
                    
                    // Check flag before updating state
                    if (!monitoringActive) break;

                    // Update the set of known devices for the next check
                    synchronized (lock) {
                        previousUsbDevices = currentUsbDevices;
                    }
                }
            } catch (InterruptedException e) {
                System.out.println("DRIVER_MANAGER: USB monitoring thread interrupted.");
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } catch (Exception e) {
                System.err.println("DRIVER_MANAGER: Error during USB monitoring loop: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("DRIVER_MANAGER: USB monitoring loop finished for session " + sessionCode + ".");
                // Re-enabling is handled by stopMonitoring()
            }
        }, "USB-Monitor-Thread-" + sessionCode); // Give thread a name
        
        monitoringThread.setDaemon(true); // Allow JVM exit even if this hangs
        monitoringThread.start();
    }

    // New method to stop monitoring
    public static void stopMonitoring() {
        if (!monitoringActive) {
            System.out.println("DRIVER_MANAGER: stopMonitoring called but not active.");
            return;
        }
        System.out.println("DRIVER_MANAGER: Stopping USB monitoring...");
        monitoringActive = false; // Signal the loop to stop
        
        if (monitoringThread != null && monitoringThread.isAlive()) {
            monitoringThread.interrupt(); // Interrupt sleep/wait
            try {
                monitoringThread.join(2000); // Wait for thread to finish
            } catch (InterruptedException e) {
                 System.err.println("DRIVER_MANAGER: Interrupted while waiting for monitoring thread to stop.");
                 Thread.currentThread().interrupt();
            }
            if (monitoringThread.isAlive()) {
                 System.err.println("DRIVER_MANAGER: Monitoring thread did not stop gracefully.");
            }
        }
        monitoringThread = null; // Clear thread reference

        System.out.println("DRIVER_MANAGER: Re-enabling devices...");
        enableAllPreviouslyDisabledDevices();
        System.out.println("DRIVER_MANAGER: Monitoring stopped completely.");
    }

    // Gets USB devices using PowerShell (requires admin privileges for disabling later)
    private static Map<String, String> getConnectedUsbDevicesWithDetails() {
        Map<String, String> usbDevices = new HashMap<>();
        // Use Get-PnpDevice for more details. Filter for present USB devices.
        // Format output to easily parse InstanceId and Description.
        String command = "powershell -ExecutionPolicy Bypass -Command \"Get-PnpDevice -Class USB -Status OK | Select-Object InstanceId, FriendlyName | Format-Table -HideTableHeaders\"";

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command.substring(command.indexOf('"') + 1, command.lastIndexOf('"')));
            pb.redirectErrorStream(true);
            process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    // Expecting output like: <InstanceId> <FriendlyName>
                    // Find the first space after a potential VID/PID path
                    int firstSpace = line.indexOf(' ');
                     if (firstSpace > 0) {
                        String instanceId = line.substring(0, firstSpace).trim();
                        String description = line.substring(firstSpace).trim();
                        if (!instanceId.isEmpty()) {
                             // Use description if available, otherwise InstanceId
                             usbDevices.put(instanceId, description.isEmpty() ? instanceId : description);
                        }
                     }
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                System.err.println("DRIVER_MANAGER: Timeout getting USB device list.");
                process.destroyForcibly();
            }
            // No need to check exit code here, an empty map is sufficient indication of failure/timeout

        } catch (IOException | InterruptedException e) {
            System.err.println("DRIVER_MANAGER: Exception getting USB devices: " + e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return usbDevices;
    }

    private static boolean isDeviceExempt(String description) {
        if (description == null) return false;
        String lowerCaseDesc = description.toLowerCase();
        for (String keyword : EXEMPT_KEYWORDS) {
            if (lowerCaseDesc.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    // Disables a device using its InstanceID via PowerShell (Requires Admin)
    private static boolean disableDevice(String instanceId) {
        // Escape single quotes in instanceId just in case, although unlikely for InstanceIDs
        String escapedInstanceId = instanceId.replace("'", "''");
        // Construct the inner PowerShell command script
        String psCommand = String.format(
            "Disable-PnpDevice -InstanceId '%s' -Confirm:$false -ErrorAction Stop; if ($?) { exit 0 } else { exit 1 }",
            escapedInstanceId
        );
        System.out.println("DRIVER_MANAGER: Preparing Disable Command: " + psCommand);
        return executePowerShellCommand(psCommand, "Disable Device: " + instanceId);
    }

    // Re-enables a device using its InstanceID via PowerShell (Requires Admin)
    private static boolean enableDevice(String instanceId) {
        String escapedInstanceId = instanceId.replace("'", "''");
         // Construct the inner PowerShell command script
        String psCommand = String.format(
            "Enable-PnpDevice -InstanceId '%s' -Confirm:$false -ErrorAction Stop; if ($?) { exit 0 } else { exit 1 }",
            escapedInstanceId
        );
        System.out.println("DRIVER_MANAGER: Preparing Enable Command: " + psCommand);
        return executePowerShellCommand(psCommand, "Enable Device: " + instanceId);
    }

    // Centralized method to re-enable all devices we disabled
    public static void enableAllPreviouslyDisabledDevices() {
        synchronized (lock) {
            if (disabledDeviceInstanceIds.isEmpty()) {
                System.out.println("DRIVER_MANAGER: No devices were disabled by this session.");
                return;
            }
            System.out.println("DRIVER_MANAGER: Re-enabling " + disabledDeviceInstanceIds.size() + " previously disabled devices...");
            Set<String> successfullyEnabled = new HashSet<>();
            for (String instanceId : disabledDeviceInstanceIds) {
                if (enableDevice(instanceId)) {
                    System.out.println("DRIVER_MANAGER: Successfully re-enabled: " + instanceId);
                    successfullyEnabled.add(instanceId);
            } else {
                    System.err.println("DRIVER_MANAGER: Failed to re-enable device: " + instanceId + ". Manual intervention may be required.");
                    // Consider adding this failure to a persistent log or admin notification
                }
            }
            // Remove successfully enabled devices from the set
            disabledDeviceInstanceIds.removeAll(successfullyEnabled);
            if (!disabledDeviceInstanceIds.isEmpty()) {
                 System.err.println("DRIVER_MANAGER: WARNING - Could not re-enable all devices: " + disabledDeviceInstanceIds);
            }
        }
    }

    // Executes a PowerShell command and returns true on success (exit code 0)
    private static boolean executePowerShellCommand(String powershellScript, String description) {
        Process process = null;
        try {
             // Use ProcessBuilder correctly: pass the script to the -Command argument
             ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-Command", powershellScript);
            pb.redirectErrorStream(true); // Combine output and error streams
            process = pb.start();

            StringBuilder output = new StringBuilder();
            // Use try-with-resources for the reader
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(15, TimeUnit.SECONDS); // Slightly longer timeout for device operations
            int exitCode = -1;

            if (finished) {
                exitCode = process.exitValue();
            } else {
                 System.err.println("DRIVER_MANAGER: Timeout executing command: " + description);
                 process.destroyForcibly();
                 // Make sure we return false on timeout
                 return false;
            }

            String logOutput = output.toString().trim();

            if (exitCode == 0) {
                System.out.println("DRIVER_MANAGER: Command successful: " + description);
                if (!logOutput.isEmpty()) System.out.println("DRIVER_MANAGER[PS Output]: " + logOutput);
                return true;
            } else {
                 System.err.println("DRIVER_MANAGER: Command failed [" + exitCode + "]: " + description);
                 if (!logOutput.isEmpty()) System.err.println("DRIVER_MANAGER[PS Error Output]: " + logOutput);
                 if (logOutput.contains("requires elevation")) {
                     System.err.println("DRIVER_MANAGER: Hint - Device management commands require Administrator privileges.");
                 }
                 return false;
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("DRIVER_MANAGER: Exception executing command (" + description + "): " + e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        } finally {
            // Ensure process is always destroyed
            if (process != null) process.destroy();
        }
    }

    // Keep the original notification method
    private static void notifyAdmin(
            String sessionCode,
            String studentPcId,
            String studentName,
            String className,
            String rollNo,
            String message
    ) {
         MongoCollection<Document> notifications = MongoDBHelper.getCollection("notifications");
          if (notifications != null) {
                try {
                     notifications.insertOne(
                new Document()
                        .append("session_code", sessionCode)
                        .append("pc_id", studentPcId)
                        .append("student_name", studentName)
                        .append("class", className)
                        .append("roll_no", rollNo)
                        .append("message", message)
                                .append("type", "usb_alert") // Add a type for easier filtering later
                                .append("timestamp", new java.util.Date()) // Add timestamp
                        .append("read", false)
        );
             } catch (Exception e) {
                 System.err.println("DRIVER_MANAGER: Failed to send notification to MongoDB: " + e.getMessage());
             }
         } else {
             System.err.println("DRIVER_MANAGER: Failed to get MongoDB notifications collection.");
         }
    }

    // Add a main method for testing purposes (optional)
    // public static void main(String[] args) {
    //     System.out.println("Starting USB Monitor Test...");
    //     // Example: Replace with actual values or test parameters
    //     startMonitoring("TEST_SESSION", "TEST_PC", "Test Student", "Test Class", "T001");
    //     // Keep alive for testing, or use a proper shutdown mechanism
    //     try {
    //         Thread.sleep(Long.MAX_VALUE);
    //     } catch (InterruptedException e) {
    //         Thread.currentThread().interrupt();
    //     }
    // }
}