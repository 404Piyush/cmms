package com.cmms.taskManager.mac;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class AsyncManager implements Runnable {

    @Override
    public void run() {
        try {
            while (true) {
                TimeUnit.SECONDS.sleep(2);
                for (String bl : blProcesses()){
                    checkAndKill(bl);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    private static List<String> blProcesses(){
        List<String> list = new ArrayList<>();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url("https://pastebin.com/raw/fTQhj8nm")
                .build();
        Response res = null;
        try{
            res = client.newCall(request).execute();
            for (String blacklist : res.body().string().split("\n")) {
                list.add(blacklist.trim());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return list;
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
    private static void checkAndKill(String kill){
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


//                        if (memUsage > memoryThreshold && !isSystemProcessHybrid(appName)) {
//                            if (appName.equals( kill )){
//                                killTaskMac( appName );
//                            }
//                        }
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


}
