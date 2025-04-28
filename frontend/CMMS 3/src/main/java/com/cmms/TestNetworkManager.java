package com.cmms;

import com.cmms.networkManager.NetworkManager;
import com.cmms.networkManager.NetworkManagerWin;
import java.util.Arrays;
import java.util.Scanner;

public class TestNetworkManager {
    public static void main(String[] args) {
        // Get the platform-specific network manager
        NetworkManager networkManager = new NetworkManagerWin();
        
        Scanner scanner = new Scanner(System.in);
        System.out.println("Is this a teacher's machine? (y/n)");
        String isTeacher = scanner.nextLine().trim().toLowerCase();
        
        // Set the machine role based on user input
        NetworkManagerWin.setTeacherMachine(isTeacher.equals("y"));
        
        System.out.println("Select an option:");
        System.out.println("1. Enable website restrictions");
        System.out.println("2. Disable website restrictions");
        System.out.println("3. Exit");
        
        int choice = scanner.nextInt();
        scanner.nextLine(); // consume newline
        
        switch (choice) {
            case 1:
                System.out.println("Enter allowed websites (comma-separated, e.g., google.com,example.com):");
                String websitesInput = scanner.nextLine();
                String[] websites = websitesInput.split(",");
                
                // Trim whitespace from each website
                for (int i = 0; i < websites.length; i++) {
                    websites[i] = websites[i].trim();
                }
                
                System.out.println("Enabling restrictions to allow only: " + Arrays.toString(websites));
                networkManager.enableInternetRestrictions(Arrays.asList(websites));
                System.out.println("Restrictions enabled. Try accessing various websites now.");
                break;
                
            case 2:
                System.out.println("Disabling all restrictions...");
                networkManager.disableInternetRestrictions();
                System.out.println("Restrictions disabled. All websites should be accessible now.");
                break;
                
            case 3:
                System.out.println("Exiting...");
                break;
                
            default:
                System.out.println("Invalid choice");
                break;
        }
        
        scanner.close();
    }
} 