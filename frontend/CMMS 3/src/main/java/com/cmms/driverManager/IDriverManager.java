package com.cmms.driverManager;

/**
 * Interface for managing system drivers or device states, particularly for USB blocking.
 */
public interface IDriverManager {

    /**
     * Enables or disables blocking of specific device types (e.g., USB Mass Storage).
     * Implementation details are OS-specific.
     *
     * @param block true to enable blocking, false to disable blocking.
     * @return A status message summarizing the action taken (e.g., number of devices blocked/unblocked, errors).
     *         Returns null if no action was relevant (e.g., trying to unblock when nothing was blocked).
     */
    String blockUsbDevices(boolean block);

} 