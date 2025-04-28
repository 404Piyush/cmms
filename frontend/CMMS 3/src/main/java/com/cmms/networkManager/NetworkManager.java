package com.cmms.networkManager;

import java.util.List;

public interface NetworkManager {
    void enableInternetRestrictions(List<String> allowedWebsites);
    void disableInternetRestrictions();
    void enableInternetRestrictionsBlacklist(List<String> blockedWebsites);
} 