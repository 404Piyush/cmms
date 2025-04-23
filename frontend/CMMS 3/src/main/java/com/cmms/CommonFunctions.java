package com.cmms;

public class CommonFunctions {
    private static String OS = System.getProperty("os.name").toLowerCase();

    public static String getOS() {
        return OS;
    }

}
