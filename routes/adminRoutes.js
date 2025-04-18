/**
 * Defines administrative routes, primarily for teacher-specific actions.
 * Base Path: /api/admin
 * All routes require authentication and teacher role authorization.
 */
const express = require('express');
const router = express.Router();
const { authenticateToken, isTeacher } = require('../middleware/authMiddleware');
const adminController = require('../controllers/adminController'); // Assuming admin functions are here

// --- Protected Admin Routes ---
// All routes defined below will first require a valid token (authenticateToken) 
// and then check if the user has the 'teacher' role (isTeacher).

// Example: Get admin/teacher profile (requires auth + teacher role)
router.get('/profile', authenticateToken, isTeacher, adminController.getProfile); 

// Example: Endpoint to get all active sessions (requires auth + teacher role)
router.get('/sessions', authenticateToken, isTeacher, adminController.getActiveSessions);

// Add other teacher/admin specific routes here...
// e.g., router.post('/some-admin-action', authenticateToken, isTeacher, adminController.performAction);


// --- Potentially Unprotected or Differently Protected Routes ---
// If there were admin routes that *don't* require teacher role, 
// or only require authentication but not specific role, they would go here or use different middleware.
// router.get('/public-admin-info', adminController.getPublicInfo);


module.exports = router;