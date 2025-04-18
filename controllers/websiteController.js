const MongoDBHelper = require("../utils/MongoDBHelper");

exports.addWebsite = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { website_url, admin_pc } = req.body;

    
    if (!website_url || !admin_pc) {
      return res.status(400).json({
        message: "Missing required fields: website_url and admin_pc are required"
      });
    }

    
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const websitesCollection = MongoDBHelper.getCollection("websites");

    
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

    
    const existingWebsite = await websitesCollection.findOne({
      session_code,
      website_url: { $regex: new RegExp(`^${website_url}$`, 'i') }
    });

    if (existingWebsite) {
      if (!existingWebsite.is_active) {
        
        const reactivateResult = await websitesCollection.updateOne(
          { _id: existingWebsite._id },
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
            message: "Failed to reactivate website"
          });
        }

        
        await sessionsCollection.updateOne(
          { session_code },
          { $inc: { website_count: 1 } }
        );

        return res.status(200).json({
          message: `Website successfully reactivated in ${session.allowAllWebsites ? 'blacklist' : 'whitelist'}`,
          website: {
            ...existingWebsite,
            is_active: true,
            added_at: new Date(),
            added_by: admin_pc
          }
        });
      } else {
        return res.status(409).json({
          message: "Website already in list"
        });
      }
    }

    
    const websiteDoc = {
      session_code,
      website_url: website_url.toLowerCase(),
      added_by: admin_pc,
      added_at: new Date(),
      is_active: true,
      list_type: session.allowAllWebsites ? 'blacklist' : 'whitelist'
    };

    
    const result = await websitesCollection.insertOne(websiteDoc);

    if (!result.acknowledged) {
      return res.status(500).json({
        message: "Failed to add website"
      });
    }

    
    await sessionsCollection.updateOne(
      { session_code },
      { $inc: { website_count: 1 } }
    );

    res.status(201).json({
      message: `Website successfully added to ${session.allowAllWebsites ? 'blacklist' : 'whitelist'}`,
      website: websiteDoc
    });

  } catch (error) {
    console.error("Error in addWebsite:", error);
    res.status(500).json({
      message: "Server error while processing request",
      error: error.message
    });
  }
};

exports.getWebsites = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { admin_pc } = req.body;

    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const websitesCollection = MongoDBHelper.getCollection("websites");

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

    const websites = await websitesCollection.find({
      session_code,
      is_active: true
    }).toArray();

    res.status(200).json({
      message: "Successfully retrieved websites",
      mode: session.allowAllWebsites ? 'blacklist' : 'whitelist',
      websites
    });

  } catch (error) {
    console.error("Error in getWebsites:", error);
    res.status(500).json({
      message: "Server error while retrieving websites",
      error: error.message
    });
  }
};

exports.deleteWebsite = async (req, res) => {
  try {
    const session_code = req.params.code;
    const { website_url, admin_pc } = req.body;

    if (!website_url || !admin_pc) {
      return res.status(400).json({
        message: "Missing required fields: website_url and admin_pc are required"
      });
    }

    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const websitesCollection = MongoDBHelper.getCollection("websites");

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

    const website = await websitesCollection.findOne({
      session_code,
      website_url: { $regex: new RegExp(`^${website_url}$`, 'i') },
      is_active: true
    });

    if (!website) {
      return res.status(404).json({
        message: "Website not found in list"
      });
    }

    const timeInList = Math.floor(
      (new Date() - new Date(website.added_at)) / (1000 * 60)
    );

    const result = await websitesCollection.updateOne(
      { _id: website._id },
      { $set: { is_active: false, removed_at: new Date() } }
    );

    if (!result.modifiedCount) {
      return res.status(500).json({
        message: "Failed to remove website from list"
      });
    }

    await sessionsCollection.updateOne(
      { session_code },
      { $inc: { website_count: -1 } }
    );

    res.status(200).json({
      message: `Website successfully removed from ${session.allowAllWebsites ? 'blacklist' : 'whitelist'}`,
      website: {
        url: website.website_url,
        timeInList: `${timeInList} minutes`,
        addedAt: website.added_at,
        removedAt: new Date()
      }
    });

  } catch (error) {
    console.error("Error in deleteWebsite:", error);
    res.status(500).json({
      message: "Server error while removing website",
      error: error.message
    });
  }
};