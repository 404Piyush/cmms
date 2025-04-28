package com.cmms.networkManager;

import com.cmms.Main;
import com.cmms.service.WebSocketService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

/**
 * Service for managing website access via hosts file modification on Windows.
 * Requires administrator privileges.
 */
public class WebsiteMonitorService {

    private final WebSocketService webSocketService;
    private final String studentId;
    private volatile String currentMode = "";
    private volatile List<String> currentBlacklist = Collections.emptyList();
    private volatile List<String> currentWhitelist = Collections.emptyList();
    private volatile boolean isRunning = false;
    private final Object hostsFileLock = new Object(); // Lock for file access

    // TODO: Derive backend host from API URL if possible/needed
    private static final Path HOSTS_FILE_PATH = Paths.get("C:", "Windows", "System32", "drivers", "etc", "hosts");
    private static final String REDIRECT_IP_V4 = "127.0.0.1";
    private static final String REDIRECT_IP_V6 = "::1"; // Added IPv6 loopback
    private static final String CMMS_MARKER_START = "# CMMS Start - Do not edit below this line";
    private static final String CMMS_MARKER_END = "# CMMS End - Do not edit above this line";

    public WebsiteMonitorService(WebSocketService webSocketService, String studentId) {
        this.webSocketService = webSocketService;
        this.studentId = studentId;
    }

    public synchronized void startMonitoring(String sessionType, List<String> initialBlacklist, List<String> initialWhitelist) {
        if (isRunning) {
            System.out.println("WebsiteMonitorService: Already running.");
            return;
        }
        System.out.println("WebsiteMonitorService: Starting monitoring...");
        updateListsInternal(sessionType, initialBlacklist, initialWhitelist);
        
        if (!applyHostsFileChanges()) {
             System.err.println("WebsiteMonitorService: Failed to apply initial hosts file changes. Monitoring may not be effective.");
             // Consider how to handle this failure - maybe stop?
        }
        isRunning = true;
    }

    public synchronized void stopMonitoring() {
        if (!isRunning) {
            return;
        }
        System.out.println("WebsiteMonitorService: Stopping monitoring and reverting hosts file...");
        revertHostsFileChanges();
        isRunning = false;
        System.out.println("WebsiteMonitorService: Monitoring stopped.");
    }

    public synchronized void updateMonitoringMode(String sessionType, List<String> newBlacklist, List<String> newWhitelist) {
        if (!isRunning) return;
        System.out.println("WebsiteMonitorService: Updating mode and lists...");
        updateListsInternal(sessionType, newBlacklist, newWhitelist);
        
        if (!applyHostsFileChanges()) {
             System.err.println("WebsiteMonitorService: Failed to apply updated hosts file changes. Monitoring may not be effective.");
        } 
    }
    
    private void updateListsInternal(String type, List<String> blacklist, List<String> whitelist) {
        this.currentMode = type != null ? type : "";
        this.currentBlacklist = blacklist != null ? 
                                blacklist.stream().map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).collect(Collectors.toList()) : 
                                Collections.emptyList();
        this.currentWhitelist = whitelist != null ? 
                                whitelist.stream().map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty()).collect(Collectors.toList()) : 
                                Collections.emptyList();
    }

    private boolean applyHostsFileChanges() {
        // Determine if mode requires hosts file editing
        boolean requiresHostsEdit = "BLOCK_WEBSITES".equals(currentMode) 
                                 || "ALLOW_WEBSITES".equals(currentMode) 
                                 || "BLOCK_APPS_WEBSITES".equals(currentMode); // ADDED Combined mode

        if (!requiresHostsEdit) {
            System.out.println("WebsiteMonitorService: Mode (" + currentMode + ") does not require hosts file changes. Ensuring cleanup...");
            return revertHostsFileChanges(false); 
        }

        System.out.println("WebsiteMonitorService: Applying hosts file changes... Mode: " + currentMode);
        List<String> hostsEntriesToAdd = new ArrayList<>();

        // Handle modes that use the blacklist
        if ("BLOCK_WEBSITES".equals(currentMode) || "BLOCK_APPS_WEBSITES".equals(currentMode)) { 
            System.out.println("  -> Mode: " + currentMode + ". Blocking: " + currentBlacklist);
            for (String site : currentBlacklist) {
                String normalizedSite = site.trim().toLowerCase();
                if (normalizedSite.isEmpty()) continue;
                
                // Remove www. prefix if it exists for the base domain check
                String baseSite = normalizedSite.startsWith("www.") ? normalizedSite.substring(4) : normalizedSite;
                
                // Get backend domains dynamically
                String apiDomain = Main.getBackendApiDomain();
                String wsDomain = Main.getBackendWebSocketDomain();

                // Always block the base domain (e.g., youtube.com)
                // Check against both API and WebSocket domains
                boolean isBackendBase = (apiDomain != null && baseSite.equalsIgnoreCase(apiDomain)) || 
                                        (wsDomain != null && baseSite.equalsIgnoreCase(wsDomain));
                                        
                if (!isBackendBase) {
                    hostsEntriesToAdd.add(REDIRECT_IP_V4 + " " + baseSite + " # CMMS Blocked");
                    hostsEntriesToAdd.add(REDIRECT_IP_V6 + " " + baseSite + " # CMMS Blocked");
                } else {
                     System.out.println("  -> Skipping block for backend host (base): " + baseSite);
                }
                
                // Always block the www. version (e.g., www.youtube.com)
                String wwwSite = "www." + baseSite;
                 // Check www version against backend host too
                 boolean isBackendWww = (apiDomain != null && wwwSite.equalsIgnoreCase(apiDomain)) ||
                                       (wsDomain != null && wwwSite.equalsIgnoreCase(wsDomain));

                 if (!isBackendWww) { 
                    hostsEntriesToAdd.add(REDIRECT_IP_V4 + " " + wwwSite + " # CMMS Blocked");
                    hostsEntriesToAdd.add(REDIRECT_IP_V6 + " " + wwwSite + " # CMMS Blocked");
                 } else {
                     System.out.println("  -> Skipping block for www version of backend host: " + wwwSite);
                 }
            }
        } else if ("ALLOW_WEBSITES".equals(currentMode)) {
            System.out.println("  -> Mode: ALLOW_WEBSITES. Ensuring whitelist allowed (no blocks added by CMMS): " + currentWhitelist);
            System.out.println("  -> NOTE: This mode does not actively block unspecified websites using the hosts file.");
            // No entries to add in this mode by default
        }

        synchronized (hostsFileLock) {
            try {
                List<String> originalLines = Files.readAllLines(HOSTS_FILE_PATH, StandardCharsets.UTF_8);
                List<String> newLines = new ArrayList<>();
                boolean inCmmsBlock = false;

                // Copy lines, excluding the old CMMS block
                for (String line : originalLines) {
                    if (line.trim().equals(CMMS_MARKER_START)) {
                        inCmmsBlock = true;
                        continue; // Skip start marker
                    }
                    if (line.trim().equals(CMMS_MARKER_END)) {
                        inCmmsBlock = false;
                        continue; // Skip end marker
                    }
                    if (!inCmmsBlock) {
                        newLines.add(line);
                    }
                }
                
                // Add new CMMS block if there are entries to add
                if (!hostsEntriesToAdd.isEmpty()) {
                    newLines.add(""); // Add a blank line for separation
                    newLines.add(CMMS_MARKER_START);
                    newLines.addAll(hostsEntriesToAdd);
                    newLines.add(CMMS_MARKER_END);
                } else {
                    System.out.println("  -> No specific hosts entries to add for mode " + currentMode);
                    // Ensure old block is removed even if no new entries are added (handled by loop above)
                }
                
                // Write the modified content back
                // Using WRITE, CREATE, TRUNCATE_EXISTING to overwrite the file
                try (BufferedWriter writer = Files.newBufferedWriter(HOSTS_FILE_PATH, StandardCharsets.UTF_8, 
                                                                  StandardOpenOption.WRITE, 
                                                                  StandardOpenOption.CREATE, 
                                                                  StandardOpenOption.TRUNCATE_EXISTING)) {
                    for (String line : newLines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                System.out.println("WebsiteMonitorService: Hosts file updated successfully.");
                flushDnsCache();
                return true;

            } catch (IOException e) {
                System.err.println("WebsiteMonitorService: ERROR updating hosts file (Permissions?): " + e.getMessage());
                // Report error? 
                reportHostsFileError(e.getMessage());
                return false;
            } catch (Exception e) {
                 System.err.println("WebsiteMonitorService: UNEXPECTED ERROR updating hosts file: " + e.getMessage());
                 e.printStackTrace();
                 reportHostsFileError("Unexpected error: " + e.getMessage());
                 return false;
            }
        }
    }

    private boolean revertHostsFileChanges(boolean flushDns) {
        System.out.println("WebsiteMonitorService: Reverting hosts file changes...");
         synchronized (hostsFileLock) {
            try {
                List<String> originalLines = Files.readAllLines(HOSTS_FILE_PATH, StandardCharsets.UTF_8);
                List<String> newLines = new ArrayList<>();
                boolean inCmmsBlock = false;
                boolean changed = false;

                // Copy lines, excluding the CMMS block
                for (String line : originalLines) {
                    if (line.trim().equals(CMMS_MARKER_START)) {
                        inCmmsBlock = true;
                        changed = true; // Mark that we found and are removing the block
                        continue; 
                    }
                    if (line.trim().equals(CMMS_MARKER_END)) {
                        inCmmsBlock = false;
                        continue; 
                    }
                    if (!inCmmsBlock) {
                        newLines.add(line);
                    }
                }

                // Only write back if changes were made
                if (changed) {
                    try (BufferedWriter writer = Files.newBufferedWriter(HOSTS_FILE_PATH, StandardCharsets.UTF_8,
                                                                      StandardOpenOption.WRITE, 
                                                                      StandardOpenOption.CREATE, 
                                                                      StandardOpenOption.TRUNCATE_EXISTING)) {
                        for (String line : newLines) {
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                    System.out.println("WebsiteMonitorService: Hosts file reverted successfully.");
                    if(flushDns) {
                         flushDnsCache();
                    }
                } else {
                    System.out.println("WebsiteMonitorService: No CMMS entries found in hosts file to revert.");
                }
                return true;

            } catch (IOException e) {
                System.err.println("WebsiteMonitorService: ERROR reverting hosts file (Permissions?): " + e.getMessage());
                reportHostsFileError("Error reverting: " + e.getMessage());
                return false;
            } catch (Exception e) {
                 System.err.println("WebsiteMonitorService: UNEXPECTED ERROR reverting hosts file: " + e.getMessage());
                 e.printStackTrace();
                 reportHostsFileError("Unexpected error reverting: " + e.getMessage());
                 return false;
            }
        }
    }

    private boolean revertHostsFileChanges() {
        return revertHostsFileChanges(true);
    }

    private void flushDnsCache() {
        System.out.println("WebsiteMonitorService: Flushing DNS cache...");
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("ipconfig /flushdns");
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("WebsiteMonitorService: DNS cache flushed successfully.");
            } else {
                System.err.println("WebsiteMonitorService: ipconfig /flushdns exited with code: " + exitCode);
                // Read error stream?
                 try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                     String line; while ((line = errorReader.readLine()) != null) { System.err.println("FlushDNS Error Stream: " + line); }
                 }
            }
        } catch (IOException e) {
            System.err.println("WebsiteMonitorService: IOException while flushing DNS: " + e.getMessage());
        } catch (InterruptedException e) {
             System.err.println("WebsiteMonitorService: DNS flush interrupted.");
             Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("WebsiteMonitorService: Unexpected error flushing DNS: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
    
    private void reportHostsFileError(String errorMessage) {
         if (webSocketService != null && webSocketService.isAuthenticated()) {
             Map<String, Object> payload = new HashMap<>();
             payload.put("type", "hosts_file_error"); 
             Map<String, Object> data = new HashMap<>();
             data.put("error", errorMessage);
             payload.put("data", data);
             webSocketService.sendMessage("student_update", payload);
             System.out.println("WebsiteMonitorService: Reported hosts file error to teacher.");
        }
    }

    // Reporting attempts is not feasible with just hosts file modification.
} 