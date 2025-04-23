package com.cmms.taskManager;

import com.cmms.service.WebSocketService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for monitoring and blocking blacklisted applications on Windows.
 */
public class AppMonitorService {

    private final WebSocketService webSocketService;
    private final String studentId;
    private volatile Set<String> appBlacklist = Collections.synchronizedSet(new HashSet<>());
    private ScheduledExecutorService executorService;
    private volatile boolean isRunning = false;

    public AppMonitorService(WebSocketService webSocketService, String studentId) {
        this.webSocketService = webSocketService;
        this.studentId = studentId;
    }

    public synchronized void startMonitoring(List<String> initialBlacklist) {
        if (isRunning) {
            System.out.println("AppMonitorService: Already running.");
            return;
        }
        System.out.println("AppMonitorService: Starting monitoring...");
        updateBlacklistInternal(initialBlacklist);
        System.out.println("AppMonitorService: Initial blacklist: " + this.appBlacklist);

        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true); // Allow JVM to exit even if this thread is running
            return t;
        });
        // Check running processes periodically (e.g., every 3 seconds)
        executorService.scheduleAtFixedRate(this::checkAndKillProcesses, 0, 3, TimeUnit.SECONDS);
        isRunning = true;
    }

    public synchronized void stopMonitoring() {
        if (!isRunning) {
            return;
        }
        System.out.println("AppMonitorService: Stopping monitoring...");
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                // Wait a bit for termination
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("AppMonitorService: Executor did not terminate gracefully.");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        isRunning = false;
        System.out.println("AppMonitorService: Monitoring stopped.");
    }

    public synchronized void updateAppBlacklist(List<String> newBlacklist) {
        if (!isRunning) return;
        updateBlacklistInternal(newBlacklist);
        System.out.println("AppMonitorService: Blacklist updated: " + this.appBlacklist);
        // Immediately re-check processes after update
        executorService.execute(this::checkAndKillProcesses); 
    }
    
    public synchronized void addToBlacklist(String appName) {
        if (!isRunning || appName == null || appName.trim().isEmpty()) return;
        if (this.appBlacklist.add(appName.trim().toLowerCase())) {
             System.out.println("AppMonitorService: Added to blacklist: " + appName.trim().toLowerCase());
             // Immediately re-check processes after adding
             executorService.execute(this::checkAndKillProcesses);
        }
    }
    
    public synchronized void removeFromBlacklist(String appName) {
        if (!isRunning || appName == null || appName.trim().isEmpty()) return;
         if (this.appBlacklist.remove(appName.trim().toLowerCase())) {
             System.out.println("AppMonitorService: Removed from blacklist: " + appName.trim().toLowerCase());
         }
    }
    
    private void updateBlacklistInternal(List<String> list) {
         this.appBlacklist = list != null ? 
                            Collections.synchronizedSet(list.stream()
                                .map(String::trim)
                                .map(String::toLowerCase)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toSet())) : 
                            Collections.synchronizedSet(new HashSet<>());
    }

    private void checkAndKillProcesses() {
        if (!isRunning || appBlacklist.isEmpty()) {
            return;
        }
        Set<String> currentBlacklist = new HashSet<>(this.appBlacklist);
        if (currentBlacklist.isEmpty()) return; // Double check after copy
        
        // Add verbose logging flag if needed for debugging
        boolean verboseLog = false; 
        if(verboseLog) System.out.println("AppMonitorService: Checking processes against blacklist: " + currentBlacklist);
        
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("tasklist /nh /fo csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null && isRunning) {
                if(verboseLog) System.out.println("AppMonitorService: Raw tasklist line: " + line);
                String[] parts = line.split("\",\""); 
                if (parts.length > 0) {
                    String imageName = parts[0].replace("\"", "").trim().toLowerCase();
                    if (imageName.isEmpty()) continue; // Skip empty image names
                    
                    if(verboseLog) System.out.println("AppMonitorService: Parsed imageName: '" + imageName + "'");
                    
                    // Check blacklist (case-insensitive due to lowercasing above)
                    if (currentBlacklist.contains(imageName)) {
                        System.out.println("AppMonitorService: MATCH FOUND - Attempting to kill blacklisted process: " + imageName);
                        killProcess(imageName); // Attempt kill
                        reportBlockedApp(imageName); // Report block attempt
                    }
                }
            }
            reader.close();
            // Removed waitFor() as it can block unnecessarily if output reading finishes
            // int exitCode = process.waitFor(); 
            // if (exitCode != 0) { ... }

        } catch (IOException e) {
            System.err.println("AppMonitorService: IOException while checking processes: " + e.getMessage());
        } /* Removed InterruptedException catch block - waitFor removed */
        catch (Exception e) { 
            System.err.println("AppMonitorService: Unexpected error checking processes: " + e.getMessage());
            e.printStackTrace();
        } finally {
             if (process != null) {
                 // Ensure streams are closed
                 try { process.getInputStream().close(); } catch (IOException e) { /* ignore */ }
                 try { process.getErrorStream().close(); } catch (IOException e) { /* ignore */ }
                 try { process.getOutputStream().close(); } catch (IOException e) { /* ignore */ }
                 process.destroy(); // Ensure process is destroyed
             }
        }
    }

    private void killProcess(String imageName) {
        if (!isRunning || imageName == null || imageName.isEmpty()) return;
        System.out.println("AppMonitorService: Executing taskkill for: " + imageName);
        Process killProcess = null;
        BufferedReader errorReader = null; // To read error stream
        try {
            killProcess = Runtime.getRuntime().exec("taskkill /F /IM " + imageName + " /T"); // Added /T to kill child processes
            
            // Capture error stream for more details on failure
            errorReader = new BufferedReader(new InputStreamReader(killProcess.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append(System.lineSeparator());
            }
            
            int exitCode = killProcess.waitFor();
            if (exitCode == 0) {
                System.out.println("AppMonitorService: Successfully killed " + imageName);
            } else {
                System.err.println("AppMonitorService: taskkill command for \"" + imageName + "\" exited with code: " + exitCode);
                if (errorOutput.length() > 0) {
                    System.err.println("AppMonitorService: taskkill error output:\n" + errorOutput.toString());
                }
            }
        } catch (IOException e) {
            System.err.println("AppMonitorService: IOException while killing process \"" + imageName + "\": " + e.getMessage());
        } catch (InterruptedException e) { 
             System.err.println("AppMonitorService: Kill process interrupted for \"" + imageName + "\"");
             Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("AppMonitorService: Unexpected error killing process \"" + imageName + "\": " + e.getMessage());
        } finally {
            try { if (errorReader != null) errorReader.close(); } catch (IOException e) { /* ignore */ }
            if (killProcess != null) {
                 try { killProcess.getInputStream().close(); } catch (IOException e) { /* ignore */ }
                 try { killProcess.getErrorStream().close(); } catch (IOException e) { /* ignore */ }
                 try { killProcess.getOutputStream().close(); } catch (IOException e) { /* ignore */ }
                killProcess.destroy();
            }
        }
    }
    
    private void reportBlockedApp(String appName) {
        if (!isRunning) return;
        if (webSocketService != null && webSocketService.isAuthenticated()) {
             Map<String, Object> payload = new HashMap<>();
             payload.put("type", "blocked_app"); // Specific type for student update
             Map<String, Object> data = new HashMap<>();
             data.put("app_name", appName);
             payload.put("data", data);
             webSocketService.sendMessage("student_update", payload);
             System.out.println("AppMonitorService: Reported blocked app: " + appName);
        }
    }
} 