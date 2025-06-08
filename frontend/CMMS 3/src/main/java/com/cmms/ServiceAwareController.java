package com.cmms;

import com.cmms.service.ApiService;
import com.cmms.service.WebSocketService;
import com.cmms.logging.SessionLoggerService;

/**
 * Interface for controllers that need access to the application services.
 */
public interface ServiceAwareController {
    void setApiService(ApiService apiService);
    void setWebSocketService(WebSocketService webSocketService);
    void setSessionLoggerService(SessionLoggerService sessionLoggerService);
} 