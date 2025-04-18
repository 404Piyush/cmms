/**
 * Defines routes related to session management.
 * Base Path: /api/session
 * Includes session creation, ending session, and delegating 
 * session-specific student and app routes.
 */
const express = require('express');
const router = express.Router();
const sessionController = require('../controllers/sessionController');
const connectionController = require('../controllers/connectionController');

// Import route modules
const studentRoutes = require('./studentRoutes');
const appRoutes = require('./appRoutes');

// Import auth middleware for protected routes
const { authenticateToken, isTeacher } = require('../middleware/authMiddleware');

// Working routes
router.post('/create', sessionController.createSession);
router.use('/:code/student', studentRoutes); // join and disconnect endpoints work
router.use('/:code/app', appRoutes);       // app routes work

// Endpoint for teacher to end a session
router.post('/:code/end', authenticateToken, isTeacher, sessionController.endSession);

// Routes needing controller implementation
// router.use('/admin', adminRoutes);         // Need admin controller functions
// router.use('/:code/web', websiteRoutes);   // Need website controller functions 
// router.use('/:code/notification', notificationRoutes); // Need notification controller functions

module.exports = router;
