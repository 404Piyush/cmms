package com.cmms.taskManager.win;

import com.sun.management.OperatingSystemMXBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class AsyncManager implements Runnable{
    @Override
    public void run() {
        try {
            while (true) {
                TimeUnit.SECONDS.sleep(2);
                for (String process :
                        getCurrentMemoryProcessesWin(1)) {
//                    if (isBlacklistedProcessHybrid(process)) killProcess(process);

                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void killProcess(String processName) {
        // Extract just the process name without the usage information
        String[] parts = processName.split(" is using");
        String nameToKill = parts[0].trim(); // Get only the process name

        try {
            // Enclose the process name in quotes
            Process process = Runtime.getRuntime().exec("taskkill /IM \"" + nameToKill + "\" /F");

            // Read the output and error streams
            BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Collecting output messages
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = inputReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            // Collecting error messages
            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                errorOutput.append(line).append("\n");
            }

            // Wait for the process to complete and check the exit code
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("Task Manager: Terminated process: " + nameToKill);
            }
        } catch (Exception e) {
            System.err.println("Exception occurred while trying to terminate process: " + nameToKill);
            e.printStackTrace();
        }
    }



    private static List<String> getCurrentMemoryProcessesWin(double memoryThreshold) throws Exception {

        List<String> memoryProcesses = new ArrayList<>();
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalPhysicalMemorySize();
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

}
