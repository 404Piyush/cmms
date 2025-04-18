/**
 * Controller functions for administrative actions.
 * Handles app blacklisting/whitelisting within a session,
 * retrieving teacher profile info (from token), and listing active sessions.
 */
const MongoDBHelper = require("../utils/MongoDBHelper");

exports.addApp = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { app_name, admin_pc } = req.body;

    
    if (!app_name || !admin_pc) {
      return res.status(400).json({
        message: "Missing required fields: app_name and admin_pc are required"
      });
    }

    
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const blacklistedAppsCollection = MongoDBHelper.getCollection("blacklisted_apps");

    
    const sessionExists = await sessionsCollection.findOne({ 
      session_code,
      isSessionOn: true 
    });
    
    if (!sessionExists) {
      return res.status(404).json({
        message: "Session not found or inactive"
      });
    }

    
    const sessionWithAdmin = await sessionsCollection.findOne({
      session_code,
      admin_pc,
      isSessionOn: true
    });

    if (!sessionWithAdmin) {
      return res.status(403).json({
        message: "Invalid admin credentials"
      });
    }

    
    if (sessionWithAdmin.expiresAt && new Date() > new Date(sessionWithAdmin.expiresAt)) {
      return res.status(400).json({
        message: "Session has expired"
      });
    }

    
    const existingApp = await blacklistedAppsCollection.findOne({
      session_code,
      app_name: { $regex: new RegExp(`^${app_name}$`, 'i') }
    });

    if (existingApp) {
      
      if (!existingApp.is_active) {
        const reactivateResult = await blacklistedAppsCollection.updateOne(
          { _id: existingApp._id },
          { 
            $set: { 
              is_active: true,
              added_at: new Date(),
              added_by: admin_pc 
            },
            $unset: { removed_at: "" }
          }
        );

        if (!reactivateResult.modifiedCount) {
          return res.status(500).json({
            message: "Failed to reactivate app"
          });
        }

        
        await sessionsCollection.updateOne(
          { session_code },
          { $inc: { blacklisted_apps: 1 } }
        );

        return res.status(200).json({
          message: "App successfully reactivated in blacklist",
          app: {
            ...existingApp,
            is_active: true,
            added_at: new Date(),
            added_by: admin_pc
          }
        });
      } else {
        
        return res.status(409).json({
          message: "App already blacklisted for this session"
        });
      }
    }

    
    const blacklistedApp = {
      session_code,
      app_name: app_name.toLowerCase(),
      added_by: admin_pc,
      added_at: new Date(),
      is_active: true
    };

    
    const result = await blacklistedAppsCollection.insertOne(blacklistedApp);

    if (!result.acknowledged) {
      return res.status(500).json({
        message: "Failed to blacklist app"
      });
    }

    
    await sessionsCollection.updateOne(
      { session_code },
      { $inc: { blacklisted_apps: 1 } }
    );

    
    res.status(201).json({
      message: "App successfully blacklisted",
      app: blacklistedApp
    });

  } catch (error) {
    console.error("Error in addApp:", error);
    res.status(500).json({
      message: "Server error while processing request",
      error: error.message
    });
  }
};


exports.getApps = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { admin_pc } = req.query;

    if (!admin_pc) {
        return res.status(400).json({
            message: "Missing required query parameter: admin_pc"
        });
    }
    
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const blacklistedAppsCollection = MongoDBHelper.getCollection("blacklisted_apps");

    
    const session = await sessionsCollection.findOne({
      session_code,
      admin_pc,
      isSessionOn: true
    });

    if (!session) {
      return res.status(403).json({
        message: "Invalid session code or admin credentials"
      });
    }

    
    const blacklistedApps = await blacklistedAppsCollection.find({
      session_code,
      is_active: true
    }).toArray();

    res.status(200).json({
      message: "Successfully retrieved blacklisted apps",
      apps: blacklistedApps
    });

  } catch (error) {
    console.error("Error in getApps:", error);
    res.status(500).json({
      message: "Server error while retrieving apps",
      error: error.message
    });
  }
};


exports.deleteApp = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { app_name, admin_pc } = req.body;  

    
    if (!app_name || !admin_pc) {
      return res.status(400).json({
        message: "Missing required fields: app_name and admin_pc are required"
      });
    }

    
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const blacklistedAppsCollection = MongoDBHelper.getCollection("blacklisted_apps");

    
    const session = await sessionsCollection.findOne({
      session_code,
      admin_pc,
      isSessionOn: true
    });

    if (!session) {
      return res.status(403).json({
        message: "Invalid session code or admin credentials"
      });
    }

    
    const app = await blacklistedAppsCollection.findOne({
      session_code,
      app_name: { $regex: new RegExp(`^${app_name}$`, 'i') },
      is_active: true
    });

    if (!app) {
      return res.status(404).json({
        message: "App not found in blacklist"
      });
    }

    
    const timeBlacklisted = Math.floor(
      (new Date() - new Date(app.added_at)) / (1000 * 60)
    );

    
    const result = await blacklistedAppsCollection.updateOne(
      { _id: app._id },
      { $set: { is_active: false, removed_at: new Date() } }
    );

    if (!result.modifiedCount) {
      return res.status(500).json({
        message: "Failed to remove app from blacklist"
      });
    }

    
    await sessionsCollection.updateOne(
      { session_code },
      { $inc: { blacklisted_apps: -1 } }
    );

    res.status(200).json({
      message: "App successfully removed from blacklist",
      app: {
        name: app.app_name,
        timeBlacklisted: `${timeBlacklisted} minutes`,
        addedAt: app.added_at,
        removedAt: new Date()
      }
    });

  } catch (error) {
    console.error("Error in deleteApp:", error);
    res.status(500).json({
      message: "Server error while removing app",
      error: error.message
    });
  }
};

// --- Get Admin Profile (Teacher Profile for the Session) ---
exports.getProfile = async (req, res) => {
    // The authenticateToken middleware already verified the JWT 
    // and attached the payload to req.user.
    // The isTeacher middleware verified the role.
    // We can assume req.user contains { userId: admin_pc, role: 'teacher', sessionCode: ... }
    console.log("Authenticated user requesting profile:", req.user);
    
    // You could potentially fetch more teacher details from another collection
    // using req.user.userId if needed, but for now, just return the token info.
    res.status(200).json({ 
        message: 'Successfully retrieved profile data from token',
        user: req.user 
    });
};

// --- Get List of Active Sessions (requires teacher role) ---
exports.getActiveSessions = async (req, res) => {
    console.log("Teacher requesting active sessions:", req.user);
    try {
        const sessionsCollection = MongoDBHelper.getCollection("sessions");
        
        // Find sessions that are currently active
        // Project only necessary fields
        const activeSessions = await sessionsCollection.find(
            { isSessionOn: true },
            { projection: { 
                _id: 0, // Exclude the internal MongoDB ID
                session_code: 1, 
                createdAt: 1, 
                expiresAt: 1, 
                studentCount: 1, 
                mode: { $cond: [ "$allowAllWebsites", "blacklist", "whitelist" ] } // Determine mode
            } }
        ).toArray();

        res.status(200).json({ 
            message: 'Successfully retrieved active sessions', 
            sessions: activeSessions
        });
    } catch (error) {
        console.error("Error in getActiveSessions:", error);
        res.status(500).json({ message: "Server error retrieving active sessions" });
    }
};