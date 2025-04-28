package com.cmms.taskManager;

import com.cmms.CommonFunctions;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class MemoryManagement {
    public static List<String> getCurrentMemoryProcesses(double memoryThreshold){
        CommonFunctions cF = new CommonFunctions();
        if (cF.getOS().contains( "win" )){
            try {
                return getCurrentMemoryProcessesWin( 1 );
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else if (cF.getOS().contains( "mac" )){

        }
        return null;
    }
    public static void killAsync(String kill){
        try {
            // Get processes with command and memory usage percentage
            ProcessBuilder processBuilder = new ProcessBuilder("ps", "-caxm", "-o", "comm,%mem");
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            reader.readLine();

            double memoryThreshold = 0.5;
            while ((line = reader.readLine()) != null) {
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace != -1) {
                    String appName = line.substring(0, lastSpace).trim(); // Process name
                    String memoryUsageStr = line.substring(lastSpace + 1).trim(); // Memory usage
                    try {
                        double memUsage = Double.parseDouble(memoryUsageStr);


                        if (memUsage > memoryThreshold && !isSystemProcess(appName)) {
                            if (appName.equals( kill )){
                                killTaskMac( appName );
                            }
                        }
                    } catch (NumberFormatException e) {
                        System.err.println("Error parsing memory usage: " + memoryUsageStr);
                    }
                }
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static boolean isSystemProcess(String appName) {
        String[] systemProcesses = {
                "launchd", "kernel_task", "mds", "mds_stores", "syslogd", "UserEventAgent", "distnoted",
                "bluetoothd", "WindowServer", "cfprefsd", "tccd", "loginwindow", "coreaudiod", "mdworker",
                "securityd", "trustd", "systemstats", "notifyd", "powerd", "configd", "lsd", "launchservicesd"
        };

        for (String systemProcess : systemProcesses) {
            if (appName.equals(systemProcess)) {
                return true;
            }
        }

        // Automatically filter processes that start with com.apple (likely system processes)
        return appName.startsWith("com.apple.");
    }

    private static void killTaskMac(String processName){
        try {
            // Step 1: Get the PID of the process using the process name
            String[] getPidCommand = {"/bin/sh", "-c", "pgrep -i '" + processName + "'"};
            Process getPidProcess = Runtime.getRuntime().exec(getPidCommand);

            BufferedReader reader = new BufferedReader(new InputStreamReader(getPidProcess.getInputStream()));
            String pid;

            while ((pid = reader.readLine()) != null) {
                System.out.println("Found PID: " + pid);

                // Step 2: Terminate the process using the PID
                String[] killCommand = {"/bin/sh", "-c", "kill -9 " + pid};
                Process killProcess = Runtime.getRuntime().exec(killCommand);
                killProcess.waitFor();
                System.out.println("Terminated process with PID: " + pid);
            }

        } catch (Exception e) {
            System.err.println("Couldn't terminate the process");
        }


    }

    private static List<String> getCurrentMemoryProcessesWin(double memoryThreshold) throws Exception {

        List<String> memoryProcesses = new ArrayList<>();
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        long totalMemory = -1;
        try {
            // Use fully qualified name for the check and cast
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                totalMemory = ((com.sun.management.OperatingSystemMXBean) osBean).getTotalMemorySize();
            } else {
                System.err.println("MemoryManagement: Could not cast to com.sun.management.OperatingSystemMXBean to get total memory size.");
            }
        } catch (Exception e) {
            System.err.println("MemoryManagement: Error getting total memory size: " + e.getMessage());
        }
        Process process = Runtime.getRuntime().exec("tasklist /fo csv /nh");
        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;

        while ((line = input.readLine()) != null) {
            String[] processDetails = line.split("\",\"");
            if (processDetails.length > 4) {
                String memoryUsageString = processDetails[4].replace(" K", "").replace(",", "").replace("\"", "");
                String processName = processDetails[0].replace("\"", "");
                try {
                    long memoryUsage = Long.parseLong(memoryUsageString) * 1024; // Convert to bytes
                    double memoryPercentage = (double) memoryUsage / totalMemory * 100;
                    if (memoryPercentage > memoryThreshold) {
                        memoryProcesses.add(processName + " is using " + String.format("%.2f", memoryPercentage) + "% of memory.");
                    }
                } catch (NumberFormatException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        input.close();
        return memoryProcesses;
    }
//    private static List<String> getCurrentMemoryProcessesMac(double memoryThreshold){
//
//    }
    public static void promptForTermination(List<String> highMemoryProcesses) {
        Scanner scanner = new Scanner(System.in);

            System.out.println("Enter the name of the process you want to terminate:");
            String processToKill = scanner.nextLine();
            killProcess(processToKill);
        scanner.close();
    }
    // Function to terminate a process


    public static void killProcess(String processName) {
        try {
            Runtime.getRuntime().exec("taskkill /IM " + processName + " /F");
            System.out.println("Terminated process: " + processName);
        } catch (Exception e) {
            System.out.println("Failed to terminate process: " + processName);
            e.printStackTrace();
        }
    }
    public static void addProcessInFile(String addContent) throws IOException {
        File file = new File( "process.txt" );
        if (file.createNewFile()){
            System.out.println("Created file "+file.getName());
        }else System.err.println("Couldn't create file.");
        try {
            FileWriter fW = new FileWriter(  file,true );
            fW.write( addContent+"\n" );
            fW.close();
            System.out.println("Successfully wrote.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
