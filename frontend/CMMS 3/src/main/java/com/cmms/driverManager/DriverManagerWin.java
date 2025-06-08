package com.cmms.driverManager;

import com.cmms.logging.SessionLoggerService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// Note: This class now requires the application to be run with Administrator privileges
// to execute Disable-PnpDevice and Enable-PnpDevice PowerShell commands.
public class DriverManagerWin implements IDriverManager { // Implement common interface

    private final SessionLoggerService logger;
    private final Set<String> disabledDeviceInstanceIds = new HashSet<>(); // Keep track of devices we disabled

    // Constructor requires the logger service
    public DriverManagerWin(SessionLoggerService logger) {
        this.logger = logger;
        if (this.logger == null) {
            System.err.println("DriverManagerWin initialized without a logger!");
            // Potentially throw an exception if logger is mandatory
        }
        log("DriverManagerWin initialized. Requires Admin privileges for USB blocking.");
    }

    @Override
    public String blockUsbDevices(boolean block) {
        StringBuilder statusMessage = new StringBuilder();
        int successCount = 0;
        int failCount = 0;
        
        if (block) {
            log("Attempting to block USB Mass Storage devices...");
            List<String> devicesToDisable = getUsbStorageDeviceInstanceIds();
            if (devicesToDisable.isEmpty()) {
                String msg = "No USB Mass Storage devices found to disable.";
                log(msg);
                return msg; // Return status directly
            }
            log("Found USB Mass Storage devices to potentially disable: " + String.join(", ", devicesToDisable));
            statusMessage.append("Found ").append(devicesToDisable.size()).append(" USB storage device(s). ");

            for (String instanceId : devicesToDisable) {
                if (disabledDeviceInstanceIds.contains(instanceId)) {
                    log("Device already disabled by this session: " + instanceId);
                    // Consider incrementing successCount or adding to status? For now, just log.
                    continue; // Avoid trying to disable again if already tracked
                }
                boolean success = executePowerShellDeviceCommand("Disable-PnpDevice", instanceId);
                if (success) {
                    log("Successfully DISABLED USB Storage device: " + instanceId);
                    disabledDeviceInstanceIds.add(instanceId); // Track it
                    successCount++;
                } else {
                    log("FAILED to disable USB Storage device: " + instanceId + ". Check Admin privileges and PowerShell execution policy.");
                    failCount++;
                }
            }
            statusMessage.append("Disabled: ").append(successCount).append(", Failed: ").append(failCount).append(".");
            
        } else {
            log("Attempting to re-enable USB devices previously disabled by this session...");
            if (disabledDeviceInstanceIds.isEmpty()) {
                String msg = "No devices were previously disabled by this session.";
                log(msg);
                return null; // Indicate no action needed/taken
            }
            statusMessage.append("Attempting to re-enable ").append(disabledDeviceInstanceIds.size()).append(" device(s). ");
            Set<String> idsToReEnable = new HashSet<>(disabledDeviceInstanceIds);
            for (String instanceId : idsToReEnable) {
                boolean success = executePowerShellDeviceCommand("Enable-PnpDevice", instanceId);
                if (success) {
                    log("Successfully ENABLED device: " + instanceId);
                    disabledDeviceInstanceIds.remove(instanceId); // Untrack it
                    successCount++;
                } else {
                    log("FAILED to re-enable device: " + instanceId + ". Manual check might be needed.");
                     failCount++;
                }
            }
            statusMessage.append("Re-enabled: ").append(successCount).append(", Failed: ").append(failCount).append(".");
             
             if (!disabledDeviceInstanceIds.isEmpty()) {
                 String remainingMsg = "Some devices may not have been re-enabled automatically: " + String.join(", ", disabledDeviceInstanceIds);
                 log(remainingMsg);
                 statusMessage.append(" ").append(remainingMsg);
                 disabledDeviceInstanceIds.clear();
             }
        }
        return statusMessage.toString();
    }

    private List<String> getUsbStorageDeviceInstanceIds() {
        // PowerShell command to get InstanceIDs of present USB devices using the USBSTOR service
        String command = "powershell -Command \"Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match '^USB' -and $_.Service -eq 'USBSTOR' } | ForEach-Object { $_.InstanceId }\"";
        List<String> instanceIds = new ArrayList<>();
        log("Executing PowerShell to find USB Storage devices: " + command);
        
        // *** DEBUG: Add a small delay before checking devices ***
        try {
             log("DEBUG: Pausing for 1 second before querying PnP devices...");
             Thread.sleep(1000); // 1 second delay
         } catch (InterruptedException ie) {
             Thread.currentThread().interrupt();
             log("DEBUG: Sleep interrupted.");
         }

        StringBuilder fullStdOutput = new StringBuilder();
        StringBuilder fullStdError = new StringBuilder();
        int exitCode = -1; // Default error code

        try {
            Process process = Runtime.getRuntime().exec(command);
            
            // *** DEBUG: Read streams fully before waiting ***
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fullStdOutput.append(line).append(System.lineSeparator());
                    if (!line.trim().isEmpty()) {
                        instanceIds.add(line.trim());
                    }
                }
            }
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    fullStdError.append(errorLine).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS); // Wait max 10 seconds
            exitCode = finished ? process.exitValue() : -99; // Use -99 for timeout

            // *** DEBUG: Log full output and exit code ***
            log(String.format("DEBUG: Get Devices PowerShell completed. ExitCode: %d, FinishedInTime: %b", exitCode, finished));
            log("DEBUG: Get Devices Standard Output:\n---\n" + fullStdOutput.toString().trim() + "\n---");
            if (fullStdError.length() > 0) {
                log("DEBUG: Get Devices Standard Error:\n---\n" + fullStdError.toString().trim() + "\n---");
            } else {
                 log("DEBUG: Get Devices Standard Error: [None]");
            }

            if (!finished) {
                log("Powershell command to get USB storage devices timed out.");
                process.destroyForcibly();
            } else if (exitCode != 0) {
                log("Powershell command to get USB storage devices exited with non-zero code: " + exitCode);
            }

        } catch (IOException | InterruptedException e) {
            log("Exception executing PowerShell to get USB storage devices: " + e.getMessage());
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
        log("DEBUG: Found Instance IDs: " + instanceIds); // Log the parsed IDs
        return instanceIds;
    }

    private boolean executePowerShellDeviceCommand(String cmdlet, String instanceId) {
        // Escape single quotes in instanceId just in case, though unlikely for standard IDs
        String escapedInstanceId = instanceId.replace("'", "''");
        String command = String.format("powershell -Command \"%s -InstanceId '%s' -Confirm:$false\"", cmdlet, escapedInstanceId);
        boolean success = false;
        int exitCode = -1;
        StringBuilder fullStdOutput = new StringBuilder();
        StringBuilder fullStdError = new StringBuilder();

        try {
            log("Executing: " + command); // Log the command being run
            Process process = Runtime.getRuntime().exec(command);

            // *** DEBUG: Capture full streams ***
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                     fullStdOutput.append(line).append(System.lineSeparator());
                }
            }
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                     fullStdError.append(errorLine).append(System.lineSeparator());
                }
            }

            boolean finished = process.waitFor(10, TimeUnit.SECONDS); // Wait max 10 seconds
            exitCode = finished ? process.exitValue() : -99; // -99 for timeout
            
            // *** DEBUG: Log full output and exit code ***
            log(String.format("DEBUG: %s PowerShell completed. ExitCode: %d, FinishedInTime: %b", cmdlet, exitCode, finished));
            log(String.format("DEBUG: %s Standard Output:\n---\n%s\n---", cmdlet, fullStdOutput.toString().trim()));
            if (fullStdError.length() > 0) {
                 log(String.format("DEBUG: %s Standard Error:\n---\n%s\n---", cmdlet, fullStdError.toString().trim()));
            } else {
                 log(String.format("DEBUG: %s Standard Error: [None]", cmdlet));
            }
            // *** END DEBUG LOGGING ***

            if (!finished) {
                 log("Powershell command timed out: " + command);
                 process.destroyForcibly();
            } else if (exitCode == 0) {
                log("Powershell command executed successfully (Exit Code 0): " + cmdlet + " for " + instanceId);
                success = true;
            } else {
                log("Powershell command failed (Exit Code " + exitCode + "): " + cmdlet + " for " + instanceId);
                // Error logging already done via DEBUG logs above
            }

        } catch (IOException | InterruptedException e) {
            log("Exception executing PowerShell command " + cmdlet + " for " + instanceId + ": " + e.getMessage());
            Thread.currentThread().interrupt(); // Preserve interrupt status
        }
        return success;
    }

    // Helper to log messages using the SessionLoggerService if available
    private void log(String message) {
        System.out.println("DriverManagerWin: " + message); // Keep console log
        if (logger != null) {
            logger.logGenericEvent("USBManager: " + message); // Log to session file
        }
    }

    // The old monitoring code via Runnable is removed as blocking is now explicit.
    // If background monitoring is still needed, it should be redesigned.
}
