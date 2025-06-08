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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(WebsiteMonitorService.class);

    public WebsiteMonitorService(WebSocketService webSocketService, String studentId) {
        this.webSocketService = webSocketService;
        this.studentId = studentId;
    }

    public synchronized void startMonitoring(String sessionType, List<String> initialBlacklist, List<String> initialWhitelist) {
        log.info("WebsiteMonitorService: startMonitoring called.");
        if (isRunning) {
            log.warn("WebsiteMonitorService: Already running, ignoring startMonitoring call.");
            return;
        }
        log.info("WebsiteMonitorService: Starting monitoring... Mode: {}, Blacklist: {}, Whitelist: {}", sessionType, initialBlacklist, initialWhitelist);
        updateListsInternal(sessionType, initialBlacklist, initialWhitelist);
        
        log.info("WebsiteMonitorService: Calling applyHostsFileChanges from startMonitoring...");
        if (!applyHostsFileChanges()) {
             log.error("WebsiteMonitorService: Failed to apply initial hosts file changes. Monitoring may not be effective.");
             // Consider how to handle this failure - maybe stop?
        }
        isRunning = true;
        log.info("WebsiteMonitorService: startMonitoring finished, isRunning set to true.");
    }

    public synchronized void stopMonitoring() {
        log.info("WebsiteMonitorService: stopMonitoring called.");
        if (!isRunning) {
            log.warn("WebsiteMonitorService: Not running, ignoring stopMonitoring call.");
            return;
        }
        log.info("WebsiteMonitorService: Stopping monitoring and reverting hosts file...");
        
        log.info("WebsiteMonitorService: Calling revertHostsFileChanges from stopMonitoring...");
        revertHostsFileChanges();
        isRunning = false;
        log.info("WebsiteMonitorService: Monitoring stopped, isRunning set to false.");
    }

    public synchronized void updateMonitoringMode(String sessionType, List<String> newBlacklist, List<String> newWhitelist) {
        log.info("WebsiteMonitorService: updateMonitoringMode called.");
        if (!isRunning) {
            log.warn("WebsiteMonitorService: Not running, ignoring updateMonitoringMode call.");
             return;
        }
        log.info("WebsiteMonitorService: Updating mode and lists... Mode: {}, Blacklist: {}, Whitelist: {}", sessionType, newBlacklist, newWhitelist);
        updateListsInternal(sessionType, newBlacklist, newWhitelist);
        
        log.info("WebsiteMonitorService: Calling applyHostsFileChanges from updateMonitoringMode...");
        if (!applyHostsFileChanges()) {
             log.error("WebsiteMonitorService: Failed to apply updated hosts file changes. Monitoring may not be effective.");
        }
        log.info("WebsiteMonitorService: updateMonitoringMode finished.");
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
        log.info("WebsiteMonitorService: Entered applyHostsFileChanges.");
        log.info("WebsiteMonitorService: Current Mode: " + currentMode);
        log.info("WebsiteMonitorService: Current Blacklist: " + currentBlacklist);
        log.info("WebsiteMonitorService: Current Whitelist: " + currentWhitelist);

        if (!Files.exists(HOSTS_FILE_PATH)) {
            log.error("WebsiteMonitorService: ERROR - Hosts file does not exist at: " + HOSTS_FILE_PATH);
            reportHostsFileError("Hosts file not found.");
            return false;
        }
        
        if (!Files.isWritable(HOSTS_FILE_PATH)) {
            log.error("WebsiteMonitorService: ERROR - Hosts file is not writable (Check Admin Permissions!): " + HOSTS_FILE_PATH);
            reportHostsFileError("Hosts file not writable.");
             // Even if not writable here, proceed to see if write fails later (might be admin run)
             // return false; // Decided to proceed and let the write operation fail if needed
        } else {
            log.info("WebsiteMonitorService: Hosts file appears writable (permission check passed).");
        }

        List<String> hostsEntriesToAdd = new ArrayList<>();

        if ("BLOCK_APPS_WEBSITES".equals(currentMode) || "BLOCK_WEBSITES".equals(currentMode)) {
             log.info("WebsiteMonitorService: Processing BLACKLIST mode.");
            if (currentBlacklist == null || currentBlacklist.isEmpty()) {
                 log.info("WebsiteMonitorService: Blacklist is empty, no hosts entries to add.");
            } else {
                 log.info("WebsiteMonitorService: Adding blacklist entries: " + currentBlacklist);
                 for (String site : currentBlacklist) {
                    if (site == null || site.trim().isEmpty()) continue;
                    String normalizedSite = site.trim().toLowerCase();

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
                        log.info("WebsiteMonitorService: Skipping block for backend host (base): " + baseSite);
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
                        log.info("WebsiteMonitorService: Skipping block for www version of backend host: " + wwwSite);
                    }
                }
            }
        }

        log.info("WebsiteMonitorService: Acquiring lock for hosts file access...");
        synchronized (hostsFileLock) {
            log.info("WebsiteMonitorService: Lock acquired.");
            List<String> originalLines = null;
            try {
                log.info("WebsiteMonitorService: Reading original hosts file...");
                originalLines = Files.readAllLines(HOSTS_FILE_PATH, StandardCharsets.UTF_8);
                log.info("WebsiteMonitorService: Original hosts file read (" + originalLines.size() + " lines).");

                List<String> newLines = new ArrayList<>();
                boolean inCmmsBlock = false;

                 log.info("WebsiteMonitorService: Processing original lines to remove old CMMS block...");
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
                log.info("WebsiteMonitorService: Finished processing original lines.");
                
                // Add new CMMS block if there are entries to add
                if (!hostsEntriesToAdd.isEmpty()) {
                    log.info("WebsiteMonitorService: Adding new CMMS block with entries: " + hostsEntriesToAdd);
                    newLines.add(""); // Add a blank line for separation
                    newLines.add(CMMS_MARKER_START);
                    newLines.addAll(hostsEntriesToAdd);
                    newLines.add(CMMS_MARKER_END);
                } else {
                    log.info("WebsiteMonitorService: No specific hosts entries to add for mode " + currentMode);
                    // Ensure old block is removed even if no new entries are added (handled by loop above)
                }
                
                log.info("WebsiteMonitorService: Preparing to write " + newLines.size() + " lines to hosts file.");
                // Write the modified content back
                // Using WRITE, CREATE, TRUNCATE_EXISTING to overwrite the file
                try (BufferedWriter writer = Files.newBufferedWriter(HOSTS_FILE_PATH, StandardCharsets.UTF_8,
                                                                  StandardOpenOption.WRITE,
                                                                  StandardOpenOption.CREATE,
                                                                  StandardOpenOption.TRUNCATE_EXISTING)) {
                    log.info("WebsiteMonitorService: BufferedWriter opened. Writing lines...");
                    for (String line : newLines) {
                        writer.write(line);
                        writer.newLine();
                    }
                     log.info("WebsiteMonitorService: Finished writing lines to buffer.");
                }
                log.info("WebsiteMonitorService: BufferedWriter closed. Hosts file updated successfully.");
                flushDnsCache();
                log.info("WebsiteMonitorService: Exiting applyHostsFileChanges (Success).");
                return true;

            } catch (IOException e) {
                log.error("WebsiteMonitorService: ERROR updating hosts file (IOException - Permissions?): " + e.getMessage());
                 log.error("WebsiteMonitorService: Stack trace for IOException:");
                 e.printStackTrace();
                // Report error?
                reportHostsFileError(e.getMessage());
                 log.info("WebsiteMonitorService: Exiting applyHostsFileChanges (IOException).");
                return false;
            } catch (Exception e) {
                 log.error("WebsiteMonitorService: UNEXPECTED ERROR updating hosts file: " + e.getMessage());
                 e.printStackTrace();
                 reportHostsFileError("Unexpected error: " + e.getMessage());
                 log.info("WebsiteMonitorService: Exiting applyHostsFileChanges (Unexpected Error).");
                 return false;
            } finally {
                 log.info("WebsiteMonitorService: Releasing lock for hosts file access.");
            }
        }
    }

    private boolean revertHostsFileChanges(boolean flushDns) {
        log.info("WebsiteMonitorService: Reverting hosts file changes...");
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
                    log.info("WebsiteMonitorService: Hosts file reverted successfully.");
                    if(flushDns) {
                         flushDnsCache();
                    }
                } else {
                    log.info("WebsiteMonitorService: No CMMS entries found in hosts file to revert.");
                }
                return true;

            } catch (IOException e) {
                log.error("WebsiteMonitorService: ERROR reverting hosts file (Permissions?): " + e.getMessage());
                reportHostsFileError("Error reverting: " + e.getMessage());
                return false;
            } catch (Exception e) {
                 log.error("WebsiteMonitorService: UNEXPECTED ERROR reverting hosts file: " + e.getMessage());
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
        log.info("WebsiteMonitorService: Flushing DNS cache...");
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("ipconfig /flushdns");
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("WebsiteMonitorService: DNS cache flushed successfully.");
            } else {
                log.error("WebsiteMonitorService: ipconfig /flushdns exited with code: " + exitCode);
                // Read error stream?
                 try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                     String line; while ((line = errorReader.readLine()) != null) { log.error("FlushDNS Error Stream: " + line); }
                 }
            }
        } catch (IOException e) {
            log.error("WebsiteMonitorService: IOException while flushing DNS: " + e.getMessage());
        } catch (InterruptedException e) {
             log.error("WebsiteMonitorService: DNS flush interrupted.");
             Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("WebsiteMonitorService: Unexpected error flushing DNS: " + e.getMessage());
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
             log.info("WebsiteMonitorService: Reported hosts file error to teacher.");
        }
    }

    // Reporting attempts is not feasible with just hosts file modification.
} 