/**
 * Session store module to manage WebSocket sessions.
 * Handles the in-memory storage of active sessions to avoid circular dependencies.
 */

// In-memory store for active WebSocket sessions and associated connections
// Structure: { sessionCode: { teacher: WebSocket | null, students: Map<userId, WebSocket> } }
const sessions = {};

/**
 * Get access to all WebSocket sessions
 * @returns {Object} The sessions object
 */
const getWebSocketSessions = () => sessions;

/**
 * Get a specific session by code
 * @param {string} sessionCode The session code to retrieve
 * @returns {Object|null} The session object or null if not found
 */
const getSession = (sessionCode) => sessions[sessionCode] || null;

/**
 * Remove a session from the store
 * @param {string} sessionCode The session code to remove
 */
const removeSession = (sessionCode) => {
    if (sessions[sessionCode]) {
        delete sessions[sessionCode];
    }
};

/**
 * Return a copy of all session codes
 * @returns {Array} Array of active session codes
 */
const getAllSessionCodes = () => Object.keys(sessions);

/**
 * Get all sessions
 * @returns {Object} The sessions object
 */
const getAllSessions = () => sessions;

module.exports = {
    getWebSocketSessions,
    getSession,
    removeSession,
    getAllSessionCodes,
    getAllSessions
}; 