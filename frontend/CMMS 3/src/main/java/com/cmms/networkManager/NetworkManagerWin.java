package com.cmms.networkManager;

import com.cmms.utils.MongoDBHelper;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Pattern;

public class NetworkManagerWin {
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^(https?://)");
    private static final String DNS_SERVER = "8.8.8.8"; // Google DNS

    public static void enableInternetRestrictions(String sessionCode, String studentPcId) {
        try {
            List<String> allowedWebsites = getAllowedWebsites();
            blockAllTrafficExceptAllowed(sessionCode, allowedWebsites);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void disableInternetRestrictions(String sessionCode) {
        try {
            // Remove all rules associated with this session
            Runtime.getRuntime().exec(
                    "powershell -Command Remove-NetFirewallRule -Name 'AllowDNS_" + sessionCode + "'"
            );
            Runtime.getRuntime().exec(
                    "powershell -Command Remove-NetFirewallRule -Name 'BlockAllOutbound_" + sessionCode + "'"
            );
            Runtime.getRuntime().exec(
                    "powershell -Command Remove-NetFirewallRule -Name 'AllowSpecific_*_" + sessionCode + "'"
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> getAllowedWebsites() {
        List<String> allowedWebsites = new ArrayList<>();
        MongoCollection<Document> allowedWebsitesCollection = MongoDBHelper.getCollection("allowed_websites");
        for (Document doc : allowedWebsitesCollection.find()) {
            String website = sanitizeWebsite(doc.getString("website"));
            if (!website.isEmpty()) {
                allowedWebsites.add(website);
            }
        }
        return allowedWebsites;
    }

    private static String sanitizeWebsite(String website) {
        return PROTOCOL_PATTERN.matcher(website)
                .replaceAll("")
                .split("/")[0]
                .trim()
                .toLowerCase();
    }

    private static void blockAllTrafficExceptAllowed(String sessionCode, List<String> allowedWebsites) {
        try {
            // 1. First allow DNS traffic

            // 2. Resolve IPs while DNS is allowed
            Set<String> allIPs = new HashSet<>();
            for (String website : allowedWebsites) {
                allIPs.addAll(resolveIPsWithRetry(website, 3));
            }
            createDNSRule(sessionCode);

            // 3. Create allow rules before blocking other traffic
            for (String ip : allIPs) {
                createFirewallRule(sessionCode, ip);
            }

            // 4. Now block all other traffic
            createBlockAllRule(sessionCode);

            // 5. Clean up DNS rule (optional)
            Runtime.getRuntime().exec(
                    "powershell -Command Remove-NetFirewallRule -Name 'AllowDNS_" + sessionCode + "'"
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createDNSRule(String sessionCode) throws Exception {
        Process dnsProcess = Runtime.getRuntime().exec(
                "powershell -Command New-NetFirewallRule -Name 'AllowDNS_" + sessionCode +
                        "' -DisplayName 'AllowDNS_" + sessionCode +
                        "' -Direction Outbound -Protocol UDP -RemotePort 53 -Action Allow"
        );
        logProcessOutput(dnsProcess, "DNS Rule Creation");
    }

    private static void createBlockAllRule(String sessionCode) throws Exception {
        Process blockProcess = Runtime.getRuntime().exec(
                "powershell -Command New-NetFirewallRule -Name 'BlockAllOutbound_" + sessionCode +
                        "' -DisplayName 'BlockAllOutbound_" + sessionCode +
                        "' -Direction Outbound -Action Block"
        );
        logProcessOutput(blockProcess, "Block All Traffic");
    }

    private static void createFirewallRule(String sessionCode, String ip) {
        try {
            String ruleName = "AllowSpecific_" + ip.replace(":", "-") + "_" + sessionCode;
            Process allowProcess = Runtime.getRuntime().exec(
                    "powershell -Command New-NetFirewallRule -Name '" + ruleName +
                            "' -DisplayName '" + ruleName +
                            "' -Direction Outbound -RemoteAddress " + ip + " -Action Allow"
            );
            logProcessOutput(allowProcess, "Allow Rule: " + ruleName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Set<String> resolveIPsWithRetry(String website, int maxRetries) {
        Set<String> ips = new HashSet<>();
        int retryCount = 0;
        while (retryCount < maxRetries && ips.isEmpty()) {
            System.out.println("[DNS] Attempt " + (retryCount + 1) + " for: " + website);
            ips = resolveIPs(website);
            retryCount++;
        }
        return ips;
    }

    private static Set<String> resolveIPs(String website) {
        Set<String> ips = new HashSet<>();
        try {
            Process process = Runtime.getRuntime().exec("nslookup " + website + " " + DNS_SERVER);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean inAnswerSection = false;

            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Non-authoritative answer:")) {
                    inAnswerSection = true;
                    continue;
                }

                if (inAnswerSection && line.startsWith("Address:")) {
                    String ip = line.replaceAll("^Address:\\s+", "").trim();
                    if (!ip.equals(DNS_SERVER) && isValidIP(ip)) {
                        ips.add(ip);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ips;
    }
    private static boolean isValidIP(String ip) {
        // Allow both IPv4 and IPv6
        return ip.matches("^([0-9a-fA-F.:]+)$");
    }

    private static void logProcessOutput(Process process, String processName) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            System.out.println("----- " + processName + " Output -----");
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            System.out.println("----------------------------------");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}