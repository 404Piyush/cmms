package com.cmms.driverManager;

import com.cmms.service.WebSocketService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for monitoring and blocking USB storage devices via Windows Registry.
 * Requires administrator privileges.
 */
public class UsbMonitorService {

    private final WebSocketService webSocketService;
    private final String studentId;
    private volatile boolean shouldBlockUsb = false;
    private ScheduledExecutorService executorService;
    private volatile boolean isRunning = false;
    private final Object regLock = new Object(); // Lock for registry access

    // Registry key for disabling USB Storage
    private static final String USB_STORAGE_REG_KEY = "HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\USBSTOR";
    private static final String REG_VALUE_NAME = "Start";
    private static final String REG_TYPE = "REG_DWORD";
    private static final String REG_VALUE_DISABLE = "4"; // Value to disable
    private static final String REG_VALUE_ENABLE = "3";  // Value to enable (default)

    public UsbMonitorService(WebSocketService webSocketService, String studentId) {
        this.webSocketService = webSocketService;
        this.studentId = studentId;
    }

    public synchronized void startMonitoring(boolean blockUsbEnabled) {
        if (isRunning) {
            System.out.println("UsbMonitorService: Already running.");
            if (this.shouldBlockUsb != blockUsbEnabled) {
                 updateBlockingState(blockUsbEnabled);
            }
            return;
        }
        System.out.println("UsbMonitorService: Starting monitoring...");
        this.shouldBlockUsb = blockUsbEnabled;

        if (!applyCurrentBlockingState()) {
            System.err.println("UsbMonitorService: Failed to apply initial USB blocking state.");
            // Consider reporting error
        }

        // Start detection loop (Placeholder - needs better implementation)
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true); 
            return t;
        });
        executorService.scheduleAtFixedRate(this::detectUsbDevices, 0, 15, TimeUnit.SECONDS); // Check less frequently
        
        isRunning = true;
    }

    public synchronized void stopMonitoring() {
        if (!isRunning) {
            return;
        }
        System.out.println("UsbMonitorService: Stopping monitoring and reverting USB state...");
        if (executorService != null) {
            executorService.shutdownNow();
             try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("UsbMonitorService: Detection executor did not terminate gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (!revertBlockingState()) { // Ensure USB is re-enabled on stop
             System.err.println("UsbMonitorService: Failed to revert USB blocking state.");
        }
        isRunning = false;
        System.out.println("UsbMonitorService: Monitoring stopped.");
    }

    public synchronized void updateBlockingState(boolean blockUsbEnabled) {
        if (!isRunning) return; // Don't apply if not monitoring
        if (this.shouldBlockUsb == blockUsbEnabled) return; // No change

        System.out.println("UsbMonitorService: Updating blocking state to: " + blockUsbEnabled);
        this.shouldBlockUsb = blockUsbEnabled;
        if (!applyCurrentBlockingState()) {
            System.err.println("UsbMonitorService: Failed to apply updated USB blocking state.");
        }
    }

    private boolean applyCurrentBlockingState() {
        String valueToSet = shouldBlockUsb ? REG_VALUE_DISABLE : REG_VALUE_ENABLE;
        System.out.println("UsbMonitorService: Setting USBSTOR Start value to " + valueToSet + " (ShouldBlock: " + shouldBlockUsb + ")");
        return executeRegCommand(valueToSet);
    }

    private boolean revertBlockingState() {
        System.out.println("UsbMonitorService: Reverting USBSTOR Start value to ENABLE (" + REG_VALUE_ENABLE + ")");
        return executeRegCommand(REG_VALUE_ENABLE); // Always revert to enabled state
    }

    private boolean executeRegCommand(String value) {
         synchronized(regLock) {
            Process process = null;
            try {
                 // Command: reg add HKLM\...\USBSTOR /v Start /t REG_DWORD /d <value> /f
                String command = String.format("reg add \"%s\" /v %s /t %s /d %s /f",
                                             USB_STORAGE_REG_KEY, REG_VALUE_NAME, REG_TYPE, value);
                
                System.out.println("UsbMonitorService: Executing: " + command);
                process = Runtime.getRuntime().exec(command);
                
                // Capture output streams to prevent process blocking
                StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), "ERROR");            
                StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), "OUTPUT");
                errorGobbler.start();
                outputGobbler.start();
                
                int exitCode = process.waitFor();
                errorGobbler.join(); // Wait for threads to finish reading
                outputGobbler.join();
                
                if (exitCode == 0) {
                    System.out.println("UsbMonitorService: Registry command executed successfully (Exit Code: " + exitCode + ").");
                    // Note: Success just means the command ran. Effect might not be immediate.
                    return true;
                } else {
                    System.err.println("UsbMonitorService: Registry command failed (Exit Code: " + exitCode + "). Check permissions.");
                    reportRegistryError("reg add command failed with exit code " + exitCode);
                    return false;
                }
            } catch (IOException e) {
                System.err.println("UsbMonitorService: IOException executing registry command: " + e.getMessage());
                 reportRegistryError("IOException: " + e.getMessage());
                return false;
            } catch (InterruptedException e) {
                System.err.println("UsbMonitorService: Registry command interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
                 reportRegistryError("InterruptedException: " + e.getMessage());
                return false;
            } catch (Exception e) {
                 System.err.println("UsbMonitorService: Unexpected error executing registry command: " + e.getMessage());
                 e.printStackTrace();
                  reportRegistryError("Unexpected error: " + e.getMessage());
                 return false;
            } finally {
                 if (process != null) {
                     process.destroy();
                 }
            }
        }
    }

    private void detectUsbDevices() {
        if (!isRunning) return;
        // --- WINDOWS SPECIFIC IMPLEMENTATION NEEDED --- 
        // Placeholder: This periodic check is highly inefficient and unreliable for real-time detection.
        // Real implementation requires WMI event subscriptions or JNA P/Invoke for WM_DEVICECHANGE.
        // System.out.println("UsbMonitorService: Checking for USB device changes (Placeholder)...");
        
        // If a new USB device connection attempt were detected:
        /*
        String deviceId = "DetectedUsbDeviceId"; 
        System.out.println("UsbMonitorService: Detected USB device: " + deviceId);
        reportUsbAttempt(deviceId);
        
        if (shouldBlockUsb) {
             System.out.println("UsbMonitorService: Blocking is enabled. Device should be unusable (effect may vary).");
             // Optionally try to eject/disable programmatically if possible (very complex)
        }
        */
    }

    private void reportUsbAttempt(String deviceDetails) {
        if (!isRunning) return;
        if (webSocketService != null && webSocketService.isAuthenticated()) {
             Map<String, Object> payload = new HashMap<>();
             payload.put("type", "usb_attempt");
             Map<String, Object> data = new HashMap<>();
             data.put("device_details", deviceDetails);
             payload.put("data", data);
             webSocketService.sendMessage("student_update", payload);
             System.out.println("UsbMonitorService: Reported USB attempt: " + deviceDetails);
        }
    }
    
     private void reportRegistryError(String errorMessage) {
         if (webSocketService != null && webSocketService.isAuthenticated()) {
             Map<String, Object> payload = new HashMap<>();
             payload.put("type", "usb_registry_error"); 
             Map<String, Object> data = new HashMap<>();
             data.put("error", errorMessage);
             payload.put("data", data);
             webSocketService.sendMessage("student_update", payload);
             System.out.println("UsbMonitorService: Reported registry error to teacher.");
        }
    }
    
    // Helper class to consume process output streams to prevent blocking
    private static class StreamGobbler extends Thread {
        private final java.io.InputStream is;
        private final String type;

        StreamGobbler(java.io.InputStream is, String type) {
            this.is = is;
            this.type = type;
        }

        @Override
        public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println("UsbMonitorService[" + type + "]: " + line);
                }
            } catch (IOException ioe) {
                // Don't print stack trace if it's just due to stream closing
                if (!ioe.getMessage().toLowerCase().contains("stream closed")) {
                     System.err.println("Error reading stream " + type + ": " + ioe.getMessage());
                }
            }
        }
    }
} 