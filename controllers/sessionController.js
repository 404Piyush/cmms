/**
 * Controller functions for session management.
 * Handles creation and termination of monitoring sessions.
 */
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const jwt = require('jsonwebtoken');
const MongoDBHelper = require("../utils/MongoDBHelper");

exports.createSession = async (req, res) => {
  try {
    const sessionCode = generateSecureCode(6);
    const adminPC = crypto.randomBytes(16).toString("hex"); 
    const duration = req.body.duration || 60;
    const allowAllWebsites = req.body.allowAllWebsites !== undefined ? req.body.allowAllWebsites : true;
    const expiresAt = new Date(Date.now() + duration * 60000);
    
    const sessionDoc = {
      session_code: sessionCode,
      admin_pc: adminPC, 
      isSessionOn: true,
      createdAt: new Date(),
      expiresAt,
      studentCount: 0,
      blacklisted_apps: 0,
      website_count: 0,
      allowAllWebsites,
    };

    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    await sessionsCollection.insertOne(sessionDoc);

    logEvent(`Session created: ${sessionCode}, ${adminPC}, ExpiresAt: ${expiresAt}, Mode: ${allowAllWebsites ? 'Blacklist' : 'Whitelist'}`);

    const jwtSecret = process.env.JWT_SECRET;
    if (!jwtSecret) {
        console.error('FATAL ERROR: JWT_SECRET is not defined in .env');
        return res.status(500).json({ message: "Server configuration error: JWT secret missing." }); 
    }
    
    const tokenPayload = {
        userId: adminPC, 
        role: 'teacher',
        sessionCode: sessionCode 
    };
    
    const token = jwt.sign(
        tokenPayload, 
        jwtSecret, 
        { expiresIn: `${duration}m` } 
    );
    
    res.status(201).json({
      session_code: sessionCode,
      admin_pc: adminPC, 
      expires_at: expiresAt,
      mode: allowAllWebsites ? 'blacklist' : 'whitelist',
      token: token 
    });
  } catch (error) {
    console.error("Error creating session:", error);
    res.status(500).json({ message: "Server Error" });
  }
};

exports.endSession = async (req, res) => {
    try {
        const sessionCode = req.params.code;
        const requestingAdminPc = req.user.userId; 

        const sessionsCollection = MongoDBHelper.getCollection("sessions");
        
        const session = await sessionsCollection.findOne({ 
            session_code: sessionCode, 
            isSessionOn: true 
        });

        if (!session) {
            return res.status(404).json({ message: "Active session not found with this code" });
        }

        if (session.admin_pc !== requestingAdminPc) {
            return res.status(403).json({ message: "Forbidden: You do not have permission to end this session." });
        }

        const result = await sessionsCollection.updateOne(
            { session_code: sessionCode },
            { $set: { 
                isSessionOn: false, 
                expiresAt: new Date() 
            } }
        );

        if (result.modifiedCount === 0) {
            return res.status(500).json({ message: "Failed to update session status" });
        }

        logEvent(`Session ended by teacher: ${sessionCode}, AdminPC: ${requestingAdminPc}`);
        
        res.status(200).json({ message: "Session ended successfully" });

    } catch (error) {
        console.error("Error ending session:", error);
        res.status(500).json({ message: "Server Error" });
    }
};

function generateSecureCode(length) {
  return crypto.randomBytes(length)
    .toString("base64")
    .replace(/[^a-zA-Z0-9]/g, "")
    .substring(0, length);
}

function logEvent(message) {
  const logPath = path.join(__dirname, "../logs.txt");
  const logMsg = `[${new Date().toISOString()}] ${message}\n`;
  fs.appendFile(logPath, logMsg, (err) => {
    if (err) console.error("Log error:", err);
  });
}
