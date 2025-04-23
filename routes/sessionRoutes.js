/**
 * Defines routes related to session management.
 * Base Path: /api/session
 * Includes session creation, ending session, and delegating student join.
 * Most other actions (join, app management) are handled via WebSocket.
 */
const express = require('express');
const router = express.Router();
const sessionController = require('../controllers/sessionController');
// const connectionController = require('../controllers/connectionController'); // Removed

// Re-add student routes for the join endpoint
const studentRoutes = require('./studentRoutes'); 

// const appRoutes = require('./appRoutes');
const { authenticateToken, isTeacher } = require('../middleware/authMiddleware');

router.post('/create', sessionController.createSession);
router.use('/:code/student', studentRoutes); // Only handles POST /join now
// router.use('/:code/app', appRoutes);        // Removed
router.post('/:code/end', authenticateToken, isTeacher, sessionController.endSession);

module.exports = router;
