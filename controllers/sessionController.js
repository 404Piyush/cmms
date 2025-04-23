/**
 * Controller for handling session-related REST API requests.
 * Provides functionality for creating and ending sessions.
 */
const crypto = require('crypto');
const fs = require("fs");
const path = require("path");
const jwt = require('jsonwebtoken');
const MongoDBHelper = require('../utils/MongoDBHelper');
const { getWebSocketSessions } = require('../server'); // Import the getter function

/**
 * Generates a random session code.
 */
const generateSessionCode = () => {
    return crypto.randomBytes(3).toString('hex').toUpperCase();
};

/**
 * Generates a JWT for the teacher who created the session.
 */
const generateTeacherToken = (sessionCode, adminPc, expiresIn = '2h') => {
    const payload = {
        userId: adminPc, 
        role: 'teacher', 
        sessionCode: sessionCode
    };
    return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn });
};

/**
 * POST /api/session/create
 * Creates a new classroom session.
 * Expects { adminPc } in the request body.
 */
exports.createSession = async (req, res) => {
    console.log("--- createSession Endpoint Hit ---");
    console.log("Request Method:", req.method);
    console.log("Request Body:", req.body);
    
    try {
        const { adminPc } = req.body;
        if (!adminPc) {
            return res.status(400).json({ message: "Admin PC identifier (adminPc) is required." });
        }

        // Destructure new parameters with defaults
        const { sessionType = 'BLOCK_APPS', blockUsb = false } = req.body;

        // Validate sessionType
        const validSessionTypes = ['BLOCK_APPS', 'BLOCK_APPS_WEBSITES', 'ALLOW_WEBSITES'];
        if (!validSessionTypes.includes(sessionType)) {
            return res.status(400).json({ message: `Invalid sessionType. Must be one of: ${validSessionTypes.join(', ')}` });
        }

        const sessionCode = generateSessionCode();
        const sessionCollection = MongoDBHelper.getCollection("sessions");

        const newSession = {
            session_code: sessionCode,
            admin_pc: adminPc,
            createdAt: new Date(),
            isSessionOn: true,
            sessionType: sessionType, // Store session type
            blockUsb: Boolean(blockUsb), // Store USB blocking preference
            websiteBlacklist: [], // Initialize website lists
            websiteWhitelist: [], // Initialize website lists
            studentCount: 0,
            blacklisted_apps: 0, // This count might need adjustment based on how apps are stored later
        };

        const result = await sessionCollection.insertOne(newSession);
        if (!result.insertedId) {
            throw new Error("Failed to insert session into database.");
        }

        const token = generateTeacherToken(sessionCode, adminPc);

        console.log(`Session created: ${sessionCode} by ${adminPc}`);
        res.status(201).json({
            message: "Session created successfully",
            sessionCode: sessionCode,
            adminPc: adminPc,
            token: token // Send token for the teacher to authenticate WebSocket
        });
    } catch (error) {
        console.error("Error creating session:", error);
        if (error.message.includes("duplicate key")) { 
             return res.status(409).json({ message: "Failed to create session. Potential session code collision. Please try again." });
        }
        res.status(500).json({ message: "Internal server error" });
    }
};

/**
 * POST /api/session/:code/end
 * Ends a classroom session.
 * Requires teacher authentication.
 */
exports.endSession = async (req, res) => {
    console.log("--- endSession Endpoint Hit ---");
    try {
        const { code } = req.params;
        const adminPc = req.user.userId; 

        const sessionCollection = MongoDBHelper.getCollection("sessions");
        const session = await sessionCollection.findOne({ session_code: code });

        if (!session) {
            return res.status(404).json({ message: "Session not found." });
        }

        if (session.admin_pc !== adminPc) {
            return res.status(403).json({ message: "Forbidden: You did not create this session." });
        }
        
        if (!session.isSessionOn) {
            // Return existing info if already ended?
            const endedDuration = session.endedAt && session.createdAt ? (session.endedAt.getTime() - session.createdAt.getTime()) / (1000 * 60) : 0; // Duration in minutes
            return res.status(200).json({ 
                message: "Session already ended.",
                summary: {
                    durationMinutes: Math.round(endedDuration),
                    studentCountAtEnd: 0 // Assume 0 if already ended and no WS map
                }
            });
        }

        // --- Notify Students and Gather Summary Data --- 
        let studentCount = 0;
        const allSessions = getWebSocketSessions(); // Call the getter function
        const sessionConnections = allSessions[code]; // Get WS connections for this session

        if (sessionConnections && sessionConnections.students) {
            studentCount = sessionConnections.students.size;
            console.log(`Notifying ${studentCount} student(s) in session ${code} about ending.`);
            const endMessage = JSON.stringify({ 
                type: 'session_ending', 
                payload: { message: 'Session ended by teacher.' } 
            });
            
            sessionConnections.students.forEach((studentWs, studentId) => {
                if (studentWs.readyState === 1) { // Check if WebSocket.OPEN (using numeric value directly)
                    try {
                        studentWs.send(endMessage);
                         // Consider a short delay before terminate? Not strictly necessary.
                        studentWs.terminate(); // Disconnect student
                        console.log(`Sent session_ending to student ${studentId} and terminated WS.`);
                    } catch (wsError) {
                        console.error(`Error sending end message or terminating WS for student ${studentId}:`, wsError);
                    }
                }
            });
        } else {
             console.log(`No active student WebSocket connections found for session ${code} to notify.`);
        }
        
        // Also disconnect teacher if connected
        if (sessionConnections && sessionConnections.teacher && sessionConnections.teacher.readyState === 1) {
            console.log(`Terminating teacher WebSocket connection for session ${code}.`);
            sessionConnections.teacher.terminate();
        }
        
        // Clean up the in-memory session entry
        delete allSessions[code]; // Delete from the map obtained via getter
        console.log(`Removed in-memory WebSocket session entry for ${code}.`);
        // --- End Notification and WS Cleanup --- 

        const endedAt = new Date();
        const durationMs = endedAt.getTime() - session.createdAt.getTime();
        const durationMinutes = Math.round(durationMs / (1000 * 60));

        const updateResult = await sessionCollection.updateOne(
            { session_code: code, admin_pc: adminPc },
            { $set: { isSessionOn: false, endedAt: endedAt } }
        );

        if (updateResult.modifiedCount === 0) {
            // This shouldn't happen if the check above passed, but handle defensively
            console.error(`Failed to update session ${code} in DB despite passing initial checks.`);
            return res.status(500).json({ message: "Failed to update session status in database." });
        }

        console.log(`Session ended: ${code} by ${adminPc}. Duration: ${durationMinutes} mins. Students at end (WS): ${studentCount}`);
        
        // Return success with summary
        res.status(200).json({ 
            message: "Session ended successfully.",
            summary: { // Add summary object
                durationMinutes: durationMinutes,
                studentCountAtEnd: studentCount
                // TODO: Add other metrics here if tracked (e.g., total joins/leaves)
            }
        });
    } catch (error) {
        console.error("Error ending session:", error);
        res.status(500).json({ message: "Internal server error" });
    }
};

function logEvent(message) {
  const logPath = path.join(__dirname, "../logs.txt");
  const logMsg = `[${new Date().toISOString()}] ${message}\n`;
  fs.appendFile(logPath, logMsg, (err) => {
    if (err) console.error("Log error:", err);
  });
}
