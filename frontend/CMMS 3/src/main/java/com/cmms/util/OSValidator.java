            package com.cmms.util;

/**
 * Utility class to check the current operating system.
 */
public class OSValidator {

    private static String OS = System.getProperty("os.name").toLowerCase();

    /**
     * Checks if the current operating system is Windows.
     *
     * @return true if the OS is Windows, false otherwise.
     */
    public static boolean isWindows() {
        return (OS.indexOf("win") >= 0);
    }

    /**
     * Checks if the current operating system is Mac OS.
     *
     * @return true if the OS is Mac, false otherwise.
     */
    public static boolean isMac() {
        return (OS.indexOf("mac") >= 0);
    }

    /**
     * Checks if the current operating system is Unix or Linux.
     *
     * @return true if the OS is Unix/Linux, false otherwise.
     */
    public static boolean isUnix() {
        return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0 );
    }

    /**
     * Checks if the current operating system is Solaris.
     *
     * @return true if the OS is Solaris, false otherwise.
     */
    public static boolean isSolaris() {
        return (OS.indexOf("sunos") >= 0);
    }
    
    // Private constructor to prevent instantiation
    private OSValidator() {}
} 