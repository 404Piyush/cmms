package com.cmms;

import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;

/**
 * Interface for controllers that need access to the application services.
 */
public interface ServiceAwareController {
    void setApiService(ApiService apiService);
    void setWebSocketService(WebSocketService webSocketService);
} 