package com.cmms.networkManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages Windows Firewall rules for website restrictions
 * Provides a more robust blocking mechanism than hosts file alone
 */
public class FirewallManager {
    private static final Logger LOGGER = Logger.getLogger(FirewallManager.class.getName());
    private static final String FIREWALL_RULE_GROUP = "CMMS_WebRestrictions";
    private static final String CRITICAL_SERVICES_RULE_GROUP = "CMMS_CriticalServices";
    
    /**
     * Ensures critical system services have outbound network access.
     */
    private static void ensureCriticalServicesAllowed() {
        try {
            if (!isAdministrator()) {
                LOGGER.warning("Cannot ensure critical services allowed without administrator privileges.");
                return;
            }

            // Rule for System Process (PID 4) - handles many low-level network tasks
            String systemRuleName = CRITICAL_SERVICES_RULE_GROUP + "_System";
            // Allowing 'System' pseudo-process might require different approaches or might be implicitly allowed.
            // Let's focus on svchost first as it's more directly controllable via firewall rules by path.
            // String systemCommand = String.format("powershell -Command \"New-NetFirewallRule -DisplayName '%s' -Direction Outbound -Action Allow -Program 'System' -Group '%s'\"", systemRuleName, CRITICAL_SERVICES_RULE_GROUP);
            // executeCommand(systemCommand); // Commented out for now

            // Rule for svchost.exe (hosts many essential Windows services)
            String svchostRuleName = CRITICAL_SERVICES_RULE_GROUP + "_Svchost";
            String svchostPath = System.getenv("SystemRoot") + "\\System32\\svchost.exe"; // Get dynamic path
            String svchostCommand = String.format(
                "powershell -Command \"New-NetFirewallRule -DisplayName '%s' -Direction Outbound -Action Allow -Program '%s' -Group '%s' -ErrorAction SilentlyContinue\"", // Added SilentlyContinue if rule exists
                svchostRuleName, svchostPath, CRITICAL_SERVICES_RULE_GROUP
            );
            executeCommand(svchostCommand);

            // Potentially add rules for other critical processes like Windows Update Agent if needed
            // e.g., wuauclt.exe, UsoClient.exe

            LOGGER.info("Ensured critical services firewall rules are present.");

        } catch (Exception e) {
            LOGGER.severe("Error ensuring critical services firewall rules: " + e.getMessage());
        }
    }
    
    /**
     * Enables internet restrictions by:
     * 1. Setting default outbound policy to block
     * 2. Creating specific allow rules for the browser to access allowed websites
     * 
     * @param allowedWebsites list of websites that should be accessible
     * @param browserPath path to the browser executable (e.g., chrome.exe)
     * @return true if successful, false otherwise
     */
    public static boolean enableFirewallRestrictions(List<String> allowedWebsites, String browserPath) {
        try {
            if (!isAdministrator()) {
                LOGGER.warning("Firewall configuration requires administrator privileges");
                return false;
            }
            
            // Ensure critical services are allowed *before* potentially blocking everything else
            ensureCriticalServicesAllowed();
            
            // Clear any existing CMMS rules first
            disableFirewallRestrictions();
            
            // Create new rules for each allowed website
            for (String website : allowedWebsites) {
                // Resolve domain to IP addresses
                List<String> ipAddresses = resolveWebsiteIPs(website);
                
                if (ipAddresses.isEmpty()) {
                    LOGGER.warning("Could not resolve IP addresses for: " + website);
                    continue;
                }
                
                // Create a firewall rule for each IP address
                for (String ip : ipAddresses) {
                    String ruleName = String.format("%s_Allow_%s", FIREWALL_RULE_GROUP, website);
                    String command = String.format(
                        "powershell -Command \"New-NetFirewallRule -DisplayName '%s' " +
                        "-Direction Outbound -Action Allow -Program '%s' " +
                        "-RemoteAddress '%s' -Group '%s'\"",
                        ruleName, browserPath, ip, FIREWALL_RULE_GROUP
                    );
                    
                    executeCommand(command);
                }
            }
            
            // Allow DNS resolution (port 53) for the browser to resolve domains
            String dnsRuleName = FIREWALL_RULE_GROUP + "_DNS";
            String dnsCommand = String.format(
                "powershell -Command \"New-NetFirewallRule -DisplayName '%s' " +
                "-Direction Outbound -Action Allow -Program '%s' " +
                "-Protocol UDP -RemotePort 53 -Group '%s'\"",
                dnsRuleName, browserPath, FIREWALL_RULE_GROUP
            );
            executeCommand(dnsCommand);
            
            // Set default outbound policy to block
            String blockCommand = "powershell -Command \"Set-NetFirewallProfile -Profile Domain,Public,Private -DefaultOutboundAction Block\"";
            executeCommand(blockCommand);
            
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error enabling firewall restrictions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Disables internet restrictions by removing all CMMS firewall rules
     * and resetting outbound policy to allow
     * 
     * @return true if successful, false otherwise
     */
    public static boolean disableFirewallRestrictions() {
        try {
            if (!isAdministrator()) {
                LOGGER.warning("Firewall configuration requires administrator privileges");
                return false;
            }
            
            // Remove only the web restriction rules, leave critical services rules intact
            String removeCommand = String.format(
                "powershell -Command \"Remove-NetFirewallRule -Group '%s' -ErrorAction SilentlyContinue\"",
                FIREWALL_RULE_GROUP // Use the specific group for web restrictions
            );
            executeCommand(removeCommand);
            
            // Reset default outbound policy to allow
            String allowCommand = "powershell -Command \"Set-NetFirewallProfile -Profile Domain,Public,Private -DefaultOutboundAction Allow\"";
            executeCommand(allowCommand);
            
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error disabling firewall restrictions: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Resolves a website domain to IP addresses
     * 
     * @param website the website domain to resolve
     * @return list of IP addresses for the website
     */
    private static List<String> resolveWebsiteIPs(String website) {
        List<String> ipAddresses = new ArrayList<>();
        
        try {
            // Use nslookup to resolve the domain
            String command = "nslookup " + website;
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("Address:") && !line.contains("Address: #")) {
                    String ip = line.split("Address:")[1].trim();
                    ipAddresses.add(ip);
                }
            }
            
            process.waitFor();
            reader.close();
        } catch (IOException | InterruptedException e) {
            LOGGER.warning("Error resolving IP for " + website + ": " + e.getMessage());
        }
        
        return ipAddresses;
    }
    
    /**
     * Executes a system command and returns the output
     * 
     * @param command the command to execute
     * @return the output of the command
     */
    private static String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            process.waitFor();
            reader.close();
        } catch (IOException | InterruptedException e) {
            LOGGER.warning("Error executing command: " + e.getMessage());
        }
        
        return output.toString();
    }
    
    /**
     * Checks if the application is running with administrator privileges
     * 
     * @return true if running as administrator, false otherwise
     */
    private static boolean isAdministrator() {
        try {
            String command = "powershell -Command \"([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)\"";
            Process process = Runtime.getRuntime().exec(command);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            
            process.waitFor();
            reader.close();
            
            return "True".equalsIgnoreCase(result);
        } catch (Exception e) {
            LOGGER.warning("Error checking administrator privileges: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Enables internet restrictions using a blacklist approach:
     * 1. Ensures default outbound policy is Allow
     * 2. Creates specific block rules for the browser accessing blacklisted websites
     * 
     * @param blockedWebsites list of websites that should be blocked
     * @param browserPath path to the browser executable (e.g., chrome.exe)
     * @return true if successful, false otherwise
     */
    public static boolean enableFirewallRestrictionsBlacklist(List<String> blockedWebsites, String browserPath) {
        try {
            if (!isAdministrator()) {
                LOGGER.warning("Firewall configuration requires administrator privileges");
                return false;
            }
            
            // Ensure critical services are allowed
            ensureCriticalServicesAllowed();
            
            // Clear any existing CMMS rules first (covers both allow/block modes)
            disableFirewallRestrictions(); // Reuse the existing cleanup for web rules
            
            // Ensure default outbound policy is Allow (it might have been set to Block previously)
            String allowCommand = "powershell -Command \\\"Set-NetFirewallProfile -Profile Domain,Public,Private -DefaultOutboundAction Allow\\\"";
            executeCommand(allowCommand);

            // Create block rules for each blacklisted website
            for (String website : blockedWebsites) {
                List<String> ipAddresses = resolveWebsiteIPs(website);
                
                if (ipAddresses.isEmpty()) {
                    LOGGER.warning("Could not resolve IP addresses for blacklisted site: " + website);
                    continue;
                }
                
                for (String ip : ipAddresses) {
                    // Use a slightly different name pattern for block rules if needed, or reuse
                    String ruleName = String.format("%s_Block_%s_%s", FIREWALL_RULE_GROUP, website, ip.replace(":", "_")); // Ensure unique names per IP
                    // Correct escaping for String.format: Use single quotes for PowerShell arguments where possible,
                    // and escape the outer double quotes for the powershell -Command argument.
                    String command = String.format(
                        "powershell -Command \\\"New-NetFirewallRule -DisplayName '%s' " + // Use single quotes for PowerShell string args
                        "-Direction Outbound -Action Block -Program '%s' " +               // Use single quotes
                        "-RemoteAddress '%s' -Group '%s'\\\"",                              // Use single quotes and escape final quote
                        ruleName, browserPath, ip, FIREWALL_RULE_GROUP
                    );
                    
                    executeCommand(command);
                }
            }
            
            // Note: We don't need a specific DNS rule here as the default policy is Allow.
            
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error enabling firewall blacklist restrictions: " + e.getMessage());
            return false;
        }
    }
} 