/**
 * Defines routes related to app management (blacklisting/whitelisting) within a session.
 * Base Path: /api/session/:code/app
 * Includes adding, retrieving, and deleting apps from the session's list.
 */
const express = require('express');
const router = express.Router({ mergeParams: true });
const adminController = require('../controllers/adminController');

// Note: These routes currently expect admin_pc in the body/query. 
// Consider requiring authentication/authorization via middleware instead.
router.post('/', adminController.addApp);
router.get('/', adminController.getApps);
router.delete('/', adminController.deleteApp);  

module.exports = router;