package com.cmms.networkManager;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

public class NetworkManagerWin implements NetworkManager {
    private static final Logger LOGGER = Logger.getLogger(NetworkManagerWin.class.getName());
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?://)");
    private static final String HOSTS_FILE_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts";
    private static final String HOSTS_MARKER_START = "# CMMS Restrictions Start";
    private static final String HOSTS_MARKER_END = "# CMMS Restrictions End";
    private static final String REDIRECT_IP = "127.0.0.1";
    private static boolean isTeacherMachine = false;
    private static String browserPath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe";

    // Set this flag to identify if this is a teacher's machine
    public static void setTeacherMachine(boolean isTeacher) {
        isTeacherMachine = isTeacher;
        LOGGER.info("Machine role set to: " + (isTeacher ? "Teacher" : "Student"));
    }
    
    // Set the browser path for firewall rules
    public static void setBrowserPath(String path) {
        browserPath = path;
        LOGGER.info("Browser path set to: " + path);
    }

    /**
     * Enable website restrictions by:
     * 1. Modifying the hosts file (for basic blocking)
     * 2. Configuring Windows Firewall (for more robust blocking)
     * 
     * Uses allowlist approach - all sites are blocked except those in allowedWebsites
     */
    public static void enableInternetRestrictions(String sessionCode, List<String> allowedWebsites) {
        // Don't apply restrictions on teacher's machine
        if (isTeacherMachine) {
            LOGGER.info("Skipping internet restrictions on teacher's machine");
            return;
        }
        
        try {
            LOGGER.info("Enabling internet restrictions for session: " + sessionCode);
            
            // First try the hosts file approach
            enableHostsFileRestrictions(sessionCode, allowedWebsites);
            
            // Then try the firewall approach for stronger enforcement
            FirewallManager.enableFirewallRestrictions(allowedWebsites, browserPath);
            
            LOGGER.info("Internet restrictions enabled successfully");
        } catch (Exception e) {
            LOGGER.severe("Error enabling internet restrictions: " + e.getMessage());
        }
    }

    /**
     * Enable website restrictions using a blacklist approach.
     * Blocks only the specified websites using hosts file and firewall.
     */
    public static void enableInternetRestrictionsBlacklist(String sessionCode, List<String> blockedWebsites) {
        if (isTeacherMachine) {
            LOGGER.info("Skipping internet restrictions on teacher's machine");
            return;
        }

        try {
            LOGGER.info("Enabling BLACKLIST internet restrictions for session: " + sessionCode);

            // Hosts file blacklist
            enableHostsFileRestrictionsBlacklist(sessionCode, blockedWebsites);

            // Firewall blacklist
            FirewallManager.enableFirewallRestrictionsBlacklist(blockedWebsites, browserPath);

            LOGGER.info("Blacklist internet restrictions enabled successfully");
        } catch (Exception e) {
            LOGGER.severe("Error enabling blacklist internet restrictions: " + e.getMessage());
        }
    }

    /**
     * Enable website restrictions using the hosts file method
     */
    private static void enableHostsFileRestrictions(String sessionCode, List<String> allowedWebsites) {
        try {
            // Remove any existing CMMS entries first
            disableHostsFileRestrictions(sessionCode);
            
            // Pre-process website list - strip protocols, www prefixes, etc.
            Set<String> processedWebsites = processWebsites(allowedWebsites);
            
            // Read current hosts file
            List<String> hostsContent = Files.readAllLines(Paths.get(HOSTS_FILE_PATH), StandardCharsets.UTF_8);
            
            // Add our markers and entries
            List<String> newHostsContent = new ArrayList<>();
            newHostsContent.addAll(hostsContent);
            newHostsContent.add(HOSTS_MARKER_START + " " + sessionCode);
            
            // Add entries for common domains that should be blocked
            // This is not comprehensive, but provides basic blocking
            for (String website : getCommonDomains()) {
                // Skip if this website is in the allowlist
                if (isWebsiteAllowed(website, processedWebsites)) {
                    continue;
                }
                
                newHostsContent.add(REDIRECT_IP + " " + website);
                // Also block www version
                if (!website.startsWith("www.")) {
                    newHostsContent.add(REDIRECT_IP + " www." + website);
                }
            }
            
            newHostsContent.add(HOSTS_MARKER_END + " " + sessionCode);
            
            // Write the updated hosts file
            Files.write(Paths.get(HOSTS_FILE_PATH), newHostsContent, StandardCharsets.UTF_8);
            
            // Flush DNS cache to apply changes immediately
            Runtime.getRuntime().exec("ipconfig /flushdns");
            
            LOGGER.info("Hosts file restrictions enabled");
        } catch (Exception e) {
            LOGGER.severe("Error modifying hosts file: " + e.getMessage());
        }
    }
    
    /**
     * Enable website restrictions using the hosts file method (BLACKLIST mode).
     * Adds entries only for the specifically blocked websites.
     */
    private static void enableHostsFileRestrictionsBlacklist(String sessionCode, List<String> blockedWebsites) {
        try {
            // Remove any existing CMMS entries first
            disableHostsFileRestrictions(sessionCode);
            
            // Pre-process website list
            Set<String> processedBlockedWebsites = processWebsites(blockedWebsites);
            
            // Read current hosts file
            List<String> hostsContent = Files.readAllLines(Paths.get(HOSTS_FILE_PATH), StandardCharsets.UTF_8);
            
            // Add our markers and entries for blocked sites
            List<String> newHostsContent = new ArrayList<>();
            newHostsContent.addAll(hostsContent);
            newHostsContent.add(HOSTS_MARKER_START + " " + sessionCode + " (Blacklist)"); // Indicate mode
            
            for (String website : processedBlockedWebsites) {
                // Add redirect entry for the processed domain (e.g., example.com)
                // and its www version (e.g., www.example.com)
                // processWebsites already handles adding both variants to the set
                newHostsContent.add(REDIRECT_IP + " " + website);
            }
            
            newHostsContent.add(HOSTS_MARKER_END + " " + sessionCode + " (Blacklist)");
            
            // Write the updated hosts file
            Files.write(Paths.get(HOSTS_FILE_PATH), newHostsContent, StandardCharsets.UTF_8);
            
            // Flush DNS cache
            Runtime.getRuntime().exec("ipconfig /flushdns");
            
            LOGGER.info("Hosts file blacklist restrictions enabled");
        } catch (Exception e) {
            LOGGER.severe("Error modifying hosts file for blacklist: " + e.getMessage());
        }
    }
    
    /**
     * Implementation of the interface method
     */
    @Override
    public void enableInternetRestrictions(List<String> allowedWebsites) {
        enableInternetRestrictions("default", allowedWebsites);
    }

    /**
     * Implementation of the interface method for blacklist
     */
    @Override
    public void enableInternetRestrictionsBlacklist(List<String> blockedWebsites) {
        enableInternetRestrictionsBlacklist("default", blockedWebsites);
    }

    /**
     * Disable website restrictions by:
     * 1. Removing the CMMS entries from the hosts file
     * 2. Removing firewall rules and resetting policy
     */
    public static void disableInternetRestrictions(String sessionCode) {
        // Don't apply restrictions on teacher's machine
        if (isTeacherMachine) {
            return;
        }
        
        try {
            LOGGER.info("Disabling internet restrictions for session: " + sessionCode);
            
            // Disable hosts file restrictions
            disableHostsFileRestrictions(sessionCode);
            
            // Disable firewall restrictions
            FirewallManager.disableFirewallRestrictions();
            
            LOGGER.info("Internet restrictions disabled successfully");
        } catch (Exception e) {
            LOGGER.severe("Error disabling internet restrictions: " + e.getMessage());
        }
    }
    
    /**
     * Disable hosts file restrictions by removing CMMS entries
     */
    private static void disableHostsFileRestrictions(String sessionCode) {
        try {
            // Read current hosts file
            List<String> hostsContent = Files.readAllLines(Paths.get(HOSTS_FILE_PATH), StandardCharsets.UTF_8);
            
            // Find our section and remove it
            List<String> newHostsContent = new ArrayList<>();
            boolean skipping = false;
            
            for (String line : hostsContent) {
                // Check for start marker
                if (line.startsWith(HOSTS_MARKER_START)) {
                    skipping = true;
                    continue;
                }
                
                // Check for end marker
                if (line.startsWith(HOSTS_MARKER_END)) {
                    skipping = false;
                    continue;
                }
                
                // Add line if not in our section
                if (!skipping) {
                    newHostsContent.add(line);
                }
            }
            
            // Write the updated hosts file
            Files.write(Paths.get(HOSTS_FILE_PATH), newHostsContent, StandardCharsets.UTF_8);
            
            // Flush DNS cache to apply changes immediately
            Runtime.getRuntime().exec("ipconfig /flushdns");
            
            LOGGER.info("Hosts file restrictions disabled");
        } catch (Exception e) {
            LOGGER.severe("Error modifying hosts file: " + e.getMessage());
        }
    }
    
    /**
     * Implementation of the interface method
     */
    @Override
    public void disableInternetRestrictions() {
        disableInternetRestrictions("default");
    }
    
    /**
     * Process a list of websites to normalize formats
     */
    private static Set<String> processWebsites(List<String> websites) {
        Set<String> processed = new HashSet<>();
        
        for (String website : websites) {
            // Remove protocol (http:// or https://)
            String cleanUrl = PROTOCOL_PATTERN.matcher(website).replaceAll("");
            
            // Remove trailing slashes
            while (cleanUrl.endsWith("/")) {
                cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
            }
            
            // Add base domain and www version
            processed.add(cleanUrl);
            if (cleanUrl.startsWith("www.")) {
                processed.add(cleanUrl.substring(4));
            } else {
                processed.add("www." + cleanUrl);
            }
            
            // Add common subdomains for major sites
            if (cleanUrl.contains("google.com")) {
                processed.add("accounts.google.com");
                processed.add("mail.google.com");
                processed.add("drive.google.com");
                processed.add("docs.google.com");
            }
        }
        
        return processed;
    }
    
    /**
     * Check if a website is in the allowed list
     */
    private static boolean isWebsiteAllowed(String website, Set<String> allowedWebsites) {
        // Direct match
        if (allowedWebsites.contains(website)) {
            return true;
        }
        
        // Check if it's a subdomain of an allowed domain
        for (String allowed : allowedWebsites) {
            if (website.endsWith("." + allowed)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get a list of common domains to block
     * This is not comprehensive, but provides basic coverage
     */
    private static List<String> getCommonDomains() {
        List<String> domains = new ArrayList<>();
        
        // Social media
        domains.add("facebook.com");
        domains.add("twitter.com");
        domains.add("instagram.com");
        domains.add("tiktok.com");
        domains.add("snapchat.com");
        domains.add("pinterest.com");
        domains.add("reddit.com");
        
        // Video streaming
        domains.add("youtube.com");
        domains.add("netflix.com");
        domains.add("hulu.com");
        domains.add("twitch.tv");
        domains.add("disney.com");
        domains.add("disneyplus.com");
        
        // Gaming
        domains.add("steam.com");
        domains.add("epicgames.com");
        domains.add("roblox.com");
        domains.add("minecraft.net");
        
        // Shopping
        domains.add("amazon.com");
        domains.add("ebay.com");
        domains.add("walmart.com");
        domains.add("target.com");
        
        // Common sites
        domains.add("google.com");
        domains.add("bing.com");
        domains.add("yahoo.com");
        domains.add("baidu.com");
        domains.add("wikipedia.org");
        
        return domains;
    }
}