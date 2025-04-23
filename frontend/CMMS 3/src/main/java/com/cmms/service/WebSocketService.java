package com.cmms.service;

import com.cmms.dto.WebSocketMessage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing WebSocket communication with the backend.
 */
public class WebSocketService {

    private final String wsUrl;
    private SimpleWebSocketClient client;
    private final Gson gson;
    private final List<WebSocketListener> listeners = new ArrayList<>();
    private String authToken = null;
    private boolean isAuthenticated = false;

    public WebSocketService(String wsUrl) {
        this.wsUrl = wsUrl;
        this.gson = new Gson();
    }

    // --- Public Methods ---

    public void addListener(WebSocketListener listener) {
        listeners.add(listener);
    }

    public void removeListener(WebSocketListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnected() {
        return client != null && client.isOpen();
    }

    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Connects to the WebSocket server and attempts authentication.
     * @param token The JWT token obtained from the API service.
     */
    public void connectAndAuthenticate(String token) {
        if (isConnected()) {
            System.out.println("WebSocket already connected.");
            if (token != null && !token.equals(this.authToken)) {
                 // Re-authenticate if token changed
                 this.authToken = token;
                 authenticate();
            } else if (isAuthenticated) {
                 notifyConnect(); // Notify listeners even if already connected and authenticated
            }
            return;
        }

        this.authToken = token;
        this.isAuthenticated = false;

        try {
            URI serverUri = new URI(wsUrl);
            client = new SimpleWebSocketClient(serverUri);
            System.out.println("Attempting WebSocket connection to: " + wsUrl);
            client.connect(); // Connects asynchronously
        } catch (URISyntaxException e) {
            System.err.println("Invalid WebSocket URL: " + wsUrl);
            notifyError("Invalid WebSocket URL", e);
        }
    }

    public void disconnect() {
        if (client != null) {
            isAuthenticated = false;
            authToken = null;
            client.close();
            // onClose event will notify listeners
        }
    }

    /**
     * Sends a message over the WebSocket.
     * @param type The message type.
     * @param payload The message payload map.
     */
    public void sendMessage(String type, Map<String, Object> payload) {
        if (!isConnected()) {
            System.err.println("Cannot send message: WebSocket not connected.");
            return;
        }
        if (!isAuthenticated()) {
            System.err.println("Cannot send message: WebSocket not authenticated.");
            // Optionally queue message until authenticated?
            return;
        }

        WebSocketMessage message = new WebSocketMessage(type, payload);
        String jsonMessage = gson.toJson(message);
        System.out.println("Sending WebSocket message: " + jsonMessage);
        client.send(jsonMessage);
    }
    
     /**
     * Sends a request message over the WebSocket and expects a response.
     * (Simple version - doesn't track request/response matching here)
     * @param type The message type.
     * @param payload The message payload map.
     * @param requestId A unique ID for this request.
     */
    public void sendRequest(String type, Map<String, Object> payload, String requestId) {
         if (!isConnected() || !isAuthenticated()) {
            System.err.println("Cannot send request: WebSocket not connected or authenticated.");
            return;
        }
        WebSocketMessage message = new WebSocketMessage(type, payload, requestId);
        String jsonMessage = gson.toJson(message);
        System.out.println("Sending WebSocket request: " + jsonMessage);
        client.send(jsonMessage);
    }

    // --- Private Helper Methods ---

    private void authenticate() {
        if (authToken == null) {
            System.err.println("Cannot authenticate: Auth token is missing.");
            return;
        }
        if (!isConnected()) {
             System.err.println("Cannot authenticate: WebSocket not connected.");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("token", authToken);
        WebSocketMessage authMessage = new WebSocketMessage("authenticate", payload);
        String jsonMessage = gson.toJson(authMessage);
        System.out.println("Sending authentication message...");
        client.send(jsonMessage);
    }

    private void notifyConnect() {
        for (WebSocketListener listener : listeners) {
            try {
                listener.onWebSocketOpen();
            } catch (Exception e) {
                System.err.println("Error in WebSocket listener (onOpen): " + e.getMessage());
            }
        }
    }

    private void notifyMessage(WebSocketMessage message) {
        // Check for authentication success response
        if ("response".equals(message.getType()) && message.getPayload() != null && message.getPayload().get("message") != null) {
             String responseMessage = String.valueOf(message.getPayload().get("message"));
             if (responseMessage.toLowerCase().contains("authentication successful")) {
                 System.out.println("WebSocket authentication successful!");
                 isAuthenticated = true;
                 // Optionally notify authenticated? 
                 // Currently handled by generic onMessage
             }
        }
        
        // Notify generic message
        for (WebSocketListener listener : listeners) {
            try {
                listener.onWebSocketMessage(message);
            } catch (Exception e) {
                System.err.println("Error in WebSocket listener (onMessage): " + e.getMessage());
            }
        }
    }

    private void notifyClose(int code, String reason) {
        isAuthenticated = false; // Ensure authenticated is false on close
        for (WebSocketListener listener : listeners) {
            try {
                listener.onWebSocketClose(code, reason);
            } catch (Exception e) {
                System.err.println("Error in WebSocket listener (onClose): " + e.getMessage());
            }
        }
    }

    private void notifyError(String message, Exception ex) {
        System.err.println("WebSocket Error: " + message + (ex != null ? " - " + ex.getMessage() : ""));
        for (WebSocketListener listener : listeners) {
            try {
                listener.onWebSocketError(message, ex);
            } catch (Exception e) {
                System.err.println("Error in WebSocket listener (onError): " + e.getMessage());
            }
        }
    }

    // --- Inner WebSocketClient Implementation ---

    private class SimpleWebSocketClient extends WebSocketClient {

        public SimpleWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("WebSocket connection opened (Status: " + handshakedata.getHttpStatus() + ")");
            // Now that connection is open, attempt authentication
            authenticate(); 
            notifyConnect(); // Notify connection open BEFORE auth response
        }

        @Override
        public void onMessage(String message) {
            System.out.println("Received WebSocket message: " + message);
            try {
                WebSocketMessage webSocketMessage = gson.fromJson(message, WebSocketMessage.class);
                if (webSocketMessage.getType() == null) {
                    System.err.println("Received WebSocket message with null type.");
                    return; // Ignore invalid message format
                }
                notifyMessage(webSocketMessage);
            } catch (JsonSyntaxException e) {
                System.err.println("Failed to parse WebSocket message: " + e.getMessage());
                 notifyError("Failed to parse message: " + message.substring(0, Math.min(message.length(), 100)), e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("WebSocket connection closed. Code: " + code + ", Reason: " + reason + ", Remote: " + remote);
            notifyClose(code, reason);
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("WebSocket error occurred: " + ex.getMessage());
            notifyError("WebSocket client error", ex);
        }
    }

    // --- Listener Interface ---

    public interface WebSocketListener {
        void onWebSocketOpen();
        void onWebSocketMessage(WebSocketMessage message);
        void onWebSocketClose(int code, String reason);
        void onWebSocketError(String message, Exception ex);
    }
} 