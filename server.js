/**
 * Main server setup file for the Classroom Management and Monitoring System.
 * Configures Express app, security middleware, WebSocket server, database connection,
 * primary API routes (session creation), and starts the HTTP server.
 * Handles WebSocket connections, authentication, and message routing.
 */
require("dotenv").config();
const express = require("express");
const cors = require("cors");
const http = require('http');
const { WebSocketServer } = require('ws');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const jwt = require('jsonwebtoken'); 
const crypto = require('crypto'); 
const MongoDBHelper = require("./utils/MongoDBHelper");
const connectDB = require("./config/db");
const sessionRoutes = require("./routes/sessionRoutes"); // For session create/end REST endpoints
const ConnectionManager = require('./utils/ConnectionManager'); // Assumed utility for monitoring

const app = express();
const PORT = process.env.PORT || 5001;

// --- Core Middleware ---
app.use(helmet());
app.use(cors()); // Consider configuring allowed origins for production
app.use(express.json());

// --- Rate Limiting ---
const limiter = rateLimit({
	windowMs: 15 * 60 * 1000, 
	max: 150, // Increased slightly
	standardHeaders: true, 
	legacyHeaders: false, 
});
app.use(limiter); 

// --- Database Connection ---
connectDB();

// --- DEBUGGING MIDDLEWARE --- 
app.use((req, res, next) => {
    console.log(`>>> Incoming Request: ${req.method} ${req.originalUrl}`);
    // console.log("Headers:", req.headers); // Optional: Log headers if needed
    next(); // Pass control to the next middleware/router
});
// --- END DEBUGGING MIDDLEWARE ---

// --- REST API Routes ---
// Only session create/end and student join are handled via REST
app.use("/api/session", sessionRoutes);

// --- HTTP Server & WebSocket Server Setup ---
const server = http.createServer(app);
const wss = new WebSocketServer({ server });

// In-memory store for active WebSocket sessions and associated connections
// Structure: { sessionCode: { teacher: WebSocket | null, students: Map<userId, WebSocket> } }
const sessions = {}; 

// Getter function to avoid circular dependency issues
const getWebSocketSessions = () => sessions;
module.exports.getWebSocketSessions = getWebSocketSessions; 

// Helper function to broadcast settings updates to all students in a session
const broadcastSettingsUpdate = (sessionCode, updatePayload) => {
    const targetSession = getWebSocketSessions()[sessionCode]; // Use getter
    if (!targetSession || !targetSession.students) return;
    
    const messageToSend = JSON.stringify({ type: 'settings_update', payload: updatePayload });
    
    targetSession.students.forEach((studentWs) => {
        if (studentWs.readyState === ws.OPEN) {
            studentWs.send(messageToSend);
        }
    });
    console.log(`Broadcast settings update to session ${sessionCode}:`, updatePayload);
};

wss.on('connection', (ws, req) => {
    console.log('WebSocket: Client attempting connection...');

    ws.isAuthenticated = false;
    ws.userId = null;
    ws.role = null;
    ws.sessionCode = null;

    ws.send(JSON.stringify({ type: 'connection_ack', payload: { message: 'Connected. Please authenticate or send join_request.'} })); 

    ws.on('message', async (message) => {
        let messageData;
        try {
            messageData = JSON.parse(message);
            // Avoid logging sensitive payload data here in production
            console.log(`WebSocket: Received message type: ${messageData.type}`, messageData.requestId ? `(ReqID: ${messageData.requestId})` : ''); 
        } catch (error) {
            console.error('WebSocket: Failed to parse message or invalid JSON:', message.toString().substring(0, 100)); // Log only snippet
            ws.send(JSON.stringify({ type: 'error', payload: { message: 'Invalid JSON format.' } }));
            return;
        }

        const { type, payload, requestId } = messageData;

        const sendResponse = (status, data) => {
            if (requestId) {
                ws.send(JSON.stringify({ type: 'response', requestId, status, payload: data }));
            }
        };
        
        const broadcastToSession = (targetSessionCode, messageToSend, senderWs = null) => {
            const targetSession = getWebSocketSessions()[targetSessionCode];
            if (!targetSession) return;
            const stringifiedMessage = JSON.stringify(messageToSend);
            if (targetSession.teacher && targetSession.teacher !== senderWs && targetSession.teacher.readyState === ws.OPEN) {
                targetSession.teacher.send(stringifiedMessage);
            }
            targetSession.students.forEach((studentWs) => {
                if (studentWs !== senderWs && studentWs.readyState === ws.OPEN) {
                    studentWs.send(stringifiedMessage);
                }
            });
        };
        
        const sendToUser = (targetSessionCode, targetUserId, messageToSend) => {
             const targetSession = getWebSocketSessions()[targetSessionCode];
             if (!targetSession) return false;
             const stringifiedMessage = JSON.stringify(messageToSend);
             if(targetSession.teacher && targetSession.teacher.userId === targetUserId && targetSession.teacher.readyState === ws.OPEN) {
                 targetSession.teacher.send(stringifiedMessage);
                 return true; 
             }
             const studentWs = targetSession.students.get(targetUserId);
             if(studentWs && studentWs.readyState === ws.OPEN) {
                 studentWs.send(stringifiedMessage);
                 return true; 
             }
             return false; 
        };
        
        // --- Message Handlers ---
        try {
            // --- Unauthenticated Messages --- 
            if (type === 'authenticate') {
                if (!payload || !payload.token) return sendResponse('error', { message: 'Auth failed: Token missing.' });
                
                const jwtSecret = process.env.JWT_SECRET;
                if (!jwtSecret) throw new Error("Server JWT_SECRET not configured.");
                const decoded = jwt.verify(payload.token, jwtSecret);

                ws.isAuthenticated = true;
                ws.userId = decoded.userId; 
                ws.role = decoded.role;
                ws.sessionCode = decoded.sessionCode;
                // Get details directly from token
                const studentNameFromToken = decoded.studentName || ws.userId; // Fallback
                const rollNoFromToken = decoded.rollNo || 'N/A';
                const classFromToken = decoded.class || 'N/A';

                // Get session details from DB
                const sessionsCollection = MongoDBHelper.getCollection("sessions");
                // const studentDetailsCollection = MongoDBHelper.getCollection("student_details"); // No longer needed here
                const sessionData = await sessionsCollection.findOne({ session_code: ws.sessionCode });

                if (!sessionData || !sessionData.isSessionOn) {
                    // Could happen if session ended between REST call and WS auth
                    ws.send(JSON.stringify({type: 'error', payload: {message: 'Session not found or inactive.'}}));
                    ws.terminate();
                    return;
                }

                if (!getWebSocketSessions()[ws.sessionCode]) {
                    getWebSocketSessions()[ws.sessionCode] = { teacher: null, students: new Map() };
                }
                const currentSession = getWebSocketSessions()[ws.sessionCode];

                if (ws.role === 'teacher') {
                    if (currentSession.teacher && currentSession.teacher !== ws) {
                         console.log(`WebSocket: Replacing teacher WS for session ${ws.sessionCode}`);
                         currentSession.teacher.send(JSON.stringify({type: 'force_disconnect', payload: {message: 'Newer teacher connection established.'}}));
                         currentSession.teacher.terminate();
                    }
                    currentSession.teacher = ws;
                    console.log(`WebSocket: Teacher ${ws.userId} authenticated for session ${ws.sessionCode}`);
                    ws.send(JSON.stringify({ type: 'response', status: 'success', payload: { message: 'Authentication successful.' } })); 
                    
                    // Send initial list of connected students WITH NAMES (using info stored on WS object)
                    const studentInfoList = [];
                    currentSession.students.forEach((studentWs, studentId) => {
                        // Assume student WS object now stores details if needed, or fetch?
                        // Let's modify the student auth part to store details on ws object
                        studentInfoList.push({ 
                            studentId: studentId, 
                            studentName: studentWs.studentName || studentId, 
                            rollNo: studentWs.rollNo || 'N/A', 
                            class: studentWs.studentClass || 'N/A' 
                        });
                    });
                    ws.send(JSON.stringify({ type: 'initial_student_list', payload: { students: studentInfoList } }));

                } else if (ws.role === 'student') {
                    // Store details from token onto the ws object for later retrieval
                    ws.studentName = studentNameFromToken;
                    ws.rollNo = rollNoFromToken;
                    ws.studentClass = classFromToken;
                    
                    // Check if student is already connected with this ID
                    if (currentSession.students.has(ws.userId)) {
                        console.log(`WebSocket: Replacing student WS for ${ws.userId} in session ${ws.sessionCode}`);
                        const oldWs = currentSession.students.get(ws.userId);
                        oldWs.send(JSON.stringify({type: 'force_disconnect', payload: {message: 'Newer student connection established.'}}));
                        oldWs.terminate();
                    }
                    currentSession.students.set(ws.userId, ws); // Store ws object which now contains details
                    console.log(`WebSocket: Student ${ws.studentName} (${ws.userId}) authenticated for session ${ws.sessionCode}`);
                    ws.send(JSON.stringify({ type: 'response', status: 'success', payload: { message: 'Authentication successful.' } })); 

                    // Send initial settings to the newly authenticated student
                    ws.send(JSON.stringify({ 
                        type: 'initial_settings', 
                        payload: { 
                            sessionType: sessionData.sessionType, 
                            blockUsb: sessionData.blockUsb,
                            // Fetch app blacklist from the retrieved session data
                            websiteBlacklist: sessionData.websiteBlacklist || [],
                            websiteWhitelist: sessionData.websiteWhitelist || [],
                            appBlacklist: sessionData.appBlacklist || [] // Use actual blacklist
                        } 
                    }));

                    // Notify teacher with student details (using details stored on ws object)
                    const teacherWs = currentSession.teacher;
                    if (teacherWs && teacherWs.readyState === ws.OPEN) {
                         teacherWs.send(JSON.stringify({ 
                            type: 'student_joined', 
                            payload: { 
                                studentId: ws.userId, 
                                studentName: ws.studentName, 
                                rollNo: ws.rollNo, 
                                class: ws.studentClass 
                            } 
                        }));
                    }
                } else {
                     throw new Error(`Invalid role found in token: ${ws.role}`);
                }
                return; // Processed auth message
            }
            
            // Other message types require authentication
            if (!ws.isAuthenticated) {
                console.log('WebSocket: Ignoring message from unauthenticated client:', type);
                ws.send(JSON.stringify({ type: 'error', payload: { message: 'Not authenticated.' } }));
                return;
            }

            // --- Authenticated Message Handlers ---
            const currentSessionCode = ws.sessionCode;
            const currentUserId = ws.userId;
            const currentRole = ws.role;
            const currentSession = getWebSocketSessions()[currentSessionCode];
            
            if (!currentSession) throw new Error(`Session ${currentSessionCode} missing for authenticated user ${currentUserId}`);

            switch (type) {
                // --- Teacher Actions ---
                case 'get_session_details':
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    const sessionsCollection_gsd = MongoDBHelper.getCollection("sessions");
                    const sessionDetails = await sessionsCollection_gsd.findOne({ session_code: currentSessionCode, admin_pc: currentUserId });
                    if (!sessionDetails) return sendResponse('error', {message: 'Session details not found.'});
                    const studentList_gsd = Array.from(currentSession.students.keys());
                    sendResponse('success', { session: sessionDetails, connectedStudents: studentList_gsd });
                    break;

                case 'get_apps': 
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    const blacklistedAppsCollection_ga = MongoDBHelper.getCollection("blacklisted_apps");
                    const blacklistedApps_ga = await blacklistedAppsCollection_ga.find({ session_code: currentSessionCode, is_active: true }).toArray();
                    ws.send(JSON.stringify({ type: 'response', status: 'success', payload: { apps: blacklistedApps_ga } })); 
                    break;

                case 'get_session_settings': // New handler for teacher
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    const sessionsCollection_gss = MongoDBHelper.getCollection("sessions");
                    const currentSettings = await sessionsCollection_gss.findOne(
                        { session_code: currentSessionCode, admin_pc: currentUserId },
                        { projection: { sessionType: 1, blockUsb: 1, websiteBlacklist: 1, websiteWhitelist: 1 } } // Fetch only needed fields
                    );
                    if (!currentSettings) {
                        ws.send(JSON.stringify({ type: 'response', status: 'error', payload: { message: 'Session settings not found.' }})); 
                    } else {
                        ws.send(JSON.stringify({ type: 'response', status: 'success', payload: currentSettings })); 
                    }
                    break;

                case 'set_website_list': // New handler for teacher
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    if (!payload || !payload.type || !Array.isArray(payload.websites)) return sendResponse('error', { message: 'Invalid payload. Requires type ("blacklist" or "whitelist") and websites array.' });
                    
                    const { type: listType, websites } = payload;
                    const sessionsCollection_swl = MongoDBHelper.getCollection("sessions");
                    let updateField;
                    if (listType === 'blacklist') updateField = 'websiteBlacklist';
                    else if (listType === 'whitelist') updateField = 'websiteWhitelist';
                    else return sendResponse('error', { message: 'Invalid list type specified.' });

                    // Basic sanitization/validation of websites could be added here
                    const sanitizedWebsites = websites.map(w => String(w).trim().toLowerCase()).filter(w => w.length > 0);

                    const updateResult_swl = await sessionsCollection_swl.updateOne(
                        { session_code: currentSessionCode, admin_pc: currentUserId, isSessionOn: true },
                        { $set: { [updateField]: sanitizedWebsites } }
                    );

                    if (!updateResult_swl.matchedCount) return sendResponse('error', { message: 'Session not active or invalid admin.' });
                    if (!updateResult_swl.modifiedCount) return sendResponse('success', { message: 'Website list already up-to-date.' }); // Not an error

                    sendResponse('success', { message: `${listType} updated successfully.` });
                    
                    // Broadcast the updated list to ALL clients in the session (including teacher)
                    broadcastToSession(currentSessionCode, 
                        { type: 'settings_update', payload: { [updateField]: sanitizedWebsites } }, 
                        null // Send to everyone, including original sender (teacher)
                    ); 
                    break;

                case 'set_usb_blocking': // New handler for teacher
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    if (payload === null || typeof payload.enabled !== 'boolean') return sendResponse('error', { message: 'Invalid payload. Requires { enabled: boolean }.' });
                    
                    const { enabled } = payload;
                    const sessionsCollection_sub = MongoDBHelper.getCollection("sessions");

                    const updateResult_sub = await sessionsCollection_sub.updateOne(
                        { session_code: currentSessionCode, admin_pc: currentUserId, isSessionOn: true },
                        { $set: { blockUsb: enabled } }
                    );

                    if (!updateResult_sub.matchedCount) return sendResponse('error', { message: 'Session not active or invalid admin.' });
                    if (!updateResult_sub.modifiedCount && sessionExists_sub.blockUsb === enabled) return sendResponse('success', { message: 'USB blocking status already set.' }); // Not an error
                    
                    sendResponse('success', { message: `USB blocking ${enabled ? 'enabled' : 'disabled'}.` });
                    // Broadcast the change to all students
                    broadcastSettingsUpdate(currentSessionCode, { blockUsb: enabled });
                    break;

                case 'add_app': 
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    if (!payload || !payload.app_name) return sendResponse('error', { message: 'Missing app_name.' });
                    
                    const sessionsCollection_aa = MongoDBHelper.getCollection("sessions");
                    const blacklistedAppsCollection_aa = MongoDBHelper.getCollection("blacklisted_apps");
                    const sessionExists_aa = await sessionsCollection_aa.findOne({ session_code: currentSessionCode, admin_pc: currentUserId, isSessionOn: true });
                    if (!sessionExists_aa) return sendResponse('error', {message: 'Session not active or invalid admin.'});

                    const app_name_aa = payload.app_name;
                    const existingApp_aa = await blacklistedAppsCollection_aa.findOne({ session_code: currentSessionCode, app_name: { $regex: new RegExp(`^${app_name_aa}$`, 'i') } });

                    if (existingApp_aa && existingApp_aa.is_active) return sendResponse('error', { message: 'App already blacklisted.' });
                         
                    let savedApp_aa;
                    if(existingApp_aa && !existingApp_aa.is_active) { 
                        await blacklistedAppsCollection_aa.updateOne({ _id: existingApp_aa._id }, { $set: { is_active: true, added_at: new Date(), added_by: currentUserId }, $unset: { removed_at: "" } });
                        savedApp_aa = { ...existingApp_aa, is_active: true, added_at: new Date(), added_by: currentUserId };
                    } else { 
                        const blacklistedApp_aa = { session_code: currentSessionCode, app_name: app_name_aa.toLowerCase(), added_by: currentUserId, added_at: new Date(), is_active: true };
                        const result_aa = await blacklistedAppsCollection_aa.insertOne(blacklistedApp_aa);
                        if (!result_aa.insertedId) throw new Error("Failed to insert app.");
                        savedApp_aa = { ...blacklistedApp_aa, _id: result_aa.insertedId };
                    }
                    await sessionsCollection_aa.updateOne({ session_code: currentSessionCode }, { $inc: { blacklisted_apps: 1 } });
                         
                    // Send direct success response to teacher
                    ws.send(JSON.stringify({ type: 'response', status: 'success', payload: { app: savedApp_aa } })); 
                    
                    // Broadcast update to all clients
                    broadcastToSession(currentSessionCode, { type: 'app_added', payload: { app_name: savedApp_aa.app_name } }, null);
                    break;

                case 'delete_app': 
                    if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                    if (!payload || !payload.app_name) return sendResponse('error', { message: 'Missing app_name.' });
                    
                    const sessionsCollection_da = MongoDBHelper.getCollection("sessions");
                    const blacklistedAppsCollection_da = MongoDBHelper.getCollection("blacklisted_apps");
                    const sessionExists_da = await sessionsCollection_da.findOne({ session_code: currentSessionCode, admin_pc: currentUserId, isSessionOn: true });
                    if (!sessionExists_da) return sendResponse('error', {message: 'Session not active or invalid admin.'});

                    const app_name_da = payload.app_name;
                    const app_da = await blacklistedAppsCollection_da.findOne({ session_code: currentSessionCode, app_name: { $regex: new RegExp(`^${app_name_da}$`, 'i') }, is_active: true });
                    if (!app_da) {
                        // Send error directly
                        ws.send(JSON.stringify({ type: 'response', status: 'error', payload: { message: 'App not found in active blacklist.' }})); 
                    } else {
                        const result_da = await blacklistedAppsCollection_da.updateOne({ _id: app_da._id }, { $set: { is_active: false, removed_at: new Date() } });
                        if (!result_da.modifiedCount) throw new Error("Failed to update app status.");
                        await sessionsCollection_da.updateOne({ session_code: currentSessionCode }, { $inc: { blacklisted_apps: -1 } });
                             
                        // Send direct success response to teacher
                        ws.send(JSON.stringify({ type: 'response', status: 'success', payload: { app_name: app_da.app_name } })); 
                        
                        // Broadcast update to all clients
                        broadcastToSession(currentSessionCode, { type: 'app_removed', payload: { app_name: app_da.app_name } }, null);
                    }
                    break;
                    
                 case 'teacher_command': 
                     if (currentRole !== 'teacher') return sendResponse('error', { message: 'Permission denied.' });
                     if (!payload) return sendResponse('error', { message: 'Missing payload.' });

                     const targetStudentId_tc = payload.targetStudentId; 
                     const commandToSend_tc = { type: payload.commandType || 'command', payload: payload.commandData || {} };

                     if (targetStudentId_tc) {
                         // Special handling for kick/disconnect
                         if (payload.commandType === 'force_disconnect') {
                             console.log(`Teacher ${currentUserId} is kicking student ${targetStudentId_tc}`);
                             const kicked = sendToUser(currentSessionCode, targetStudentId_tc, commandToSend_tc);
                             if (kicked) {
                                 sendResponse('success', { message: `Disconnect command sent to ${targetStudentId_tc}` });
                                 // Optional: Terminate server-side connection immediately after sending?
                                 // const studentWs = currentSession.students.get(targetStudentId_tc);
                                 // if (studentWs) studentWs.terminate(); 
                             } else {
                                 sendResponse('error', { message: `Student ${targetStudentId_tc} not found/disconnected.` });
                             }
                         } else {
                             // Generic command
                             const sent_tc = sendToUser(currentSessionCode, targetStudentId_tc, commandToSend_tc);
                             if (sent_tc) sendResponse('success', { message: `Command sent to ${targetStudentId_tc}` });
                             else sendResponse('error', { message: `Student ${targetStudentId_tc} not found/disconnected.` });
                         }
                     } else {
                         // Broadcast command (excluding force_disconnect which should be targeted)
                         if (payload.commandType === 'force_disconnect') {
                              return sendResponse('error', { message: 'Cannot broadcast force_disconnect.' });
                         }
                         let count_tc = 0;
                         currentSession.students.forEach((studentWs) => {
                             if (studentWs.readyState === ws.OPEN) { studentWs.send(JSON.stringify(commandToSend_tc)); count_tc++; }
                         });
                         sendResponse('success', { message: `Command broadcast to ${count_tc} students.` });
                     }
                     break;

                // --- Student Actions ---
                 case 'student_update': // Modified handler for student reports
                     if (currentRole !== 'student') return sendResponse('error', { message: 'Invalid action for role.' });
                     if (!payload) return sendResponse('error', { message: 'Missing payload.' });
                     
                     // Validate payload type (e.g., 'blocked_app', 'usb_attempt', 'log')
                     const updateType = payload.type; // e.g., 'blocked_app'
                     const updateData = payload.data; // e.g., { app_name: 'evil.exe' } or { device_id: '...' }
                     
                     if (!updateType || !updateData) {
                         return sendResponse('error', { message: 'Invalid student update payload. Requires type and data.' });
                     }

                     console.log(`Student Update from ${currentUserId}: Type=${updateType}, Data=`, updateData);
                     
                     // Relay the update to the teacher
                     const teacherWs_su = currentSession.teacher;
                     if (teacherWs_su && teacherWs_su.readyState === ws.OPEN) {
                         teacherWs_su.send(JSON.stringify({
                             type: 'student_data', // Keep generic type for teacher UI? Or use specific like 'student_blocked_app'?
                             payload: { 
                                 studentId: currentUserId, 
                                 updateType: updateType, // Pass original type
                                 data: updateData        // Pass original data
                             }
                         }));
                     }
                     // Send ACK back to student? Maybe not necessary unless action required.
                     // sendResponse('success', { message: 'Update received.' }); 
                     break;

                default:
                    console.log(`WebSocket: Received unhandled message type from authenticated client: ${type}`);
                    sendResponse('error', { message: `Unhandled message type: ${type}` });
            } // End switch
        } catch (error) {
            console.error('WebSocket: Error handling message:', error);
            ws.send(JSON.stringify({ type: 'error', payload: { message: 'Internal server error.' } }));
        }
    });

    ws.on('close', () => {
        console.log(`WebSocket: Client disconnected (Authenticated: ${ws.isAuthenticated}, Role: ${ws.role}, UserID: ${ws.userId}, Session: ${ws.sessionCode})`);
        // If the disconnected client was an authenticated user in a session
        if (ws.isAuthenticated && ws.sessionCode && ws.userId) {
            const currentSessionCode = ws.sessionCode;
            const currentUserId = ws.userId;
            const currentRole = ws.role;
            const currentSession = getWebSocketSessions()[currentSessionCode];

            if (currentSession) {
                if (currentRole === 'teacher' && currentSession.teacher === ws) {
                    console.log(`WebSocket: Teacher ${currentUserId} connection closed for session ${currentSessionCode}`);
                    currentSession.teacher = null;
                    // Optionally notify students? Maybe not necessary.
                } else if (currentRole === 'student') {
                    if (currentSession.students.get(currentUserId) === ws) {
                        console.log(`WebSocket: Student ${currentUserId} connection closed for session ${currentSessionCode}`);
                        currentSession.students.delete(currentUserId);
                        
                        // Notify the teacher that the student left
                        const teacherWs = currentSession.teacher;
                        if (teacherWs && teacherWs.readyState === ws.OPEN) {
                            teacherWs.send(JSON.stringify({ 
                                type: 'student_left', 
                                payload: { studentId: currentUserId } 
                            }));
                            console.log(`WebSocket: Notified teacher about student ${currentUserId} leaving session ${currentSessionCode}`);
                        }
                    }
                }
                
                // Clean up session if empty?
                // if (currentSession.teacher === null && currentSession.students.size === 0) {
                //     console.log(`WebSocket: Cleaning up empty session ${currentSessionCode}`);
                //     delete sessions[currentSessionCode];
                // }
            }
        }
    });

    ws.on('error', (error) => {
        console.error('WebSocket: Error:', error);
        // Handle specific errors if needed
    });
});

server.on('error', (error) => {
    console.error(`Server error: ${error.message}`);
    if (error.code === 'EADDRINUSE') {
        console.error(`*** Port ${PORT} is already in use. Please ensure no other instance is running. ***`);
    }
});

server.listen(PORT, '0.0.0.0', () => { // Explicitly listen on all IPv4 interfaces
    console.log(`Server is running on port ${PORT} (Listening on 0.0.0.0)`);
    console.log(`Attempting to connect to MongoDB...`); // Moved connection log trigger here
});