/**
 * Controller for handling student-related actions, primarily joining sessions.
 */
const MongoDBHelper = require("../utils/MongoDBHelper");
const jwt = require('jsonwebtoken'); // Needed for JWT
const crypto = require('crypto'); // <-- Added missing import

/**
 * Generates a JWT for a student joining a session.
 */
const generateStudentToken = (sessionCode, studentId, expiresIn = '2h') => {
    const payload = {
        userId: studentId,
        role: 'student',
        sessionCode: sessionCode
    };
    return jwt.sign(payload, process.env.JWT_SECRET, { expiresIn });
};

/**
 * POST /api/student/join
 * Allows a student to join an active session.
 * Expects { sessionCode, studentId } in the request body.
 */
exports.joinSession = async (req, res) => {
    try {
        const { sessionCode, studentId } = req.body;

        if (!sessionCode || !studentId) {
            return res.status(400).json({ message: "Session code and student ID are required." });
        }

        const sessionsCollection = MongoDBHelper.getCollection("sessions");
        const session = await sessionsCollection.findOne({ session_code: sessionCode, isSessionOn: true });

        if (!session) {
            return res.status(404).json({ message: "Active session not found or session is closed." });
        }
        
        // We have the session, extract relevant settings
        const { sessionType, blockUsb, websiteBlacklist, websiteWhitelist } = session;

        // Generate the token for WebSocket authentication
        const jwtSecret = process.env.JWT_SECRET;
        if (!jwtSecret) {
            console.error("FATAL: JWT_SECRET environment variable not set.");
            return res.status(500).json({ message: "Server configuration error." });
        }

        const tokenPayload = {
            userId: studentId, // Use the generated or existing ID
            role: 'student',
            sessionCode: sessionCode, // Include session code
            // Add student details directly to token
            studentName: studentName, 
            rollNo: rollNo,
            class: studentClass
        };
        const token = jwt.sign(tokenPayload, jwtSecret, { expiresIn: '1h' });

        console.log(`Student ${studentId} attempting to join session ${sessionCode}`);
        res.status(200).json({
            message: "Ready to join session via WebSocket.",
            sessionCode: sessionCode,
            studentId: studentId,
            token: token, // Send token for the student to authenticate WebSocket
            settings: { // Include session settings for the client
                sessionType: sessionType,
                blockUsb: blockUsb,
                websiteBlacklist: websiteBlacklist || [], // Ensure arrays exist
                websiteWhitelist: websiteWhitelist || []  // Ensure arrays exist
            }
        });

    } catch (error) {
        console.error("Error joining session:", error);
        res.status(500).json({ message: "Internal server error" });
    }
};

// Handles the initial student join via REST to get a token
exports.join = async (req, res) => {
  try {
    // studentId from validator is not used here, uses body directly
    const { studentName, class: studentClass, rollNo } = req.body; 
    const studentPcId = req.body.studentPcId; // Get potentially optional PC ID
    const sessionCode = req.params.code; 

    // Validate session exists and is active
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const session = await sessionsCollection.findOne({ 
      session_code: sessionCode, 
      isSessionOn: true 
    });
    if (!session) {
      return res.status(404).json({ message: "Invalid session code or session inactive" });
    }
    if (session.expiresAt && new Date() > new Date(session.expiresAt)) {
      return res.status(400).json({ message: "Session expired" });
    }
    
    // Check required student data (Input validation should handle this mostly)
    if (!studentName || !studentClass || !rollNo) {
      return res.status(400).json({ message: "Missing required student data (Name, Class, RollNo)" });
    }

    // Generate student PC ID if not provided
    const finalStudentPcId = studentPcId || crypto.randomBytes(16).toString('hex');

    // Create student document
    const studentDoc = {
      session_code: sessionCode,
      student_name: studentName,
      class: studentClass,
      roll_no: rollNo,
      student_pc: finalStudentPcId, 
      isConnected: false, // Will connect via WebSocket after getting token
      joinedAt: new Date()
    };

    const studentsCollection = MongoDBHelper.getCollection("students");
    // Consider adding a check if student_pc already exists for this session?
    const insertResult = await studentsCollection.insertOne(studentDoc);
    if (!insertResult.insertedId) {
      return res.status(500).json({ message: "Failed to save student data" });
    }

    await sessionsCollection.updateOne(
      { session_code: sessionCode },
      { $inc: { studentCount: 1 } }
    );

    // Generate JWT for this student
    const jwtSecret = process.env.JWT_SECRET;
    if (!jwtSecret) {
        console.error('FATAL ERROR: JWT_SECRET is not defined in .env');
        return res.status(500).json({ message: "Server configuration error: JWT secret missing." }); 
    }
    
    const studentTokenPayload = {
        userId: finalStudentPcId, 
        role: 'student',
        sessionCode: sessionCode,
        studentName: studentName, 
        rollNo: rollNo,
        class: studentClass
    };

    // Calculate expiry based on session remaining time
    const sessionDurationMinutes = session.expiresAt ? Math.max(1, Math.ceil((new Date(session.expiresAt).getTime() - Date.now()) / 60000)) : 60;
    const studentToken = jwt.sign(
        studentTokenPayload, 
        jwtSecret, 
        { expiresIn: `${sessionDurationMinutes}m` } 
    );

    // We have the session document from earlier validation
    const { sessionType, blockUsb, websiteBlacklist, websiteWhitelist } = session;

    res.status(201).json({
      student_pc: finalStudentPcId,
      token: studentToken, // Send token needed for WebSocket auth
      message: "Successfully registered. Use token to authenticate WebSocket.",
      settings: { // Include session settings for the client
        sessionType: sessionType,
        blockUsb: blockUsb,
        websiteBlacklist: websiteBlacklist || [], // Ensure arrays exist
        websiteWhitelist: websiteWhitelist || []  // Ensure arrays exist
      }
    });
  } catch (error) {
    console.error("Error in REST join session:", error);
    // Handle potential duplicate key errors if student_pc is unique index
    if (error.code === 11000) { 
        return res.status(409).json({ message: "Conflict: Student PC ID likely already registered for this session." });
    }
    res.status(500).json({ message: "Server Error joining session" });
  }
};

// Keep disconnect logic here? Or move to WebSocket?
// Moving to WebSocket seems more appropriate as it relates to the connection state.
/*
exports.disconnect = async (req, res) => {
  // ... Original disconnect logic ... 
};
*/ 