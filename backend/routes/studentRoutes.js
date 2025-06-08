/**
 * Defines REST routes for initial student actions, primarily joining a session.
 * Base Path: /api/session/:code/student
 */
const express = require('express');
const router = express.Router({ mergeParams: true });
const { body, validationResult } = require('express-validator');
const studentController = require('../controllers/studentController');

// Middleware to handle validation results
const handleValidationErrors = (req, res, next) => {
  const errors = validationResult(req);
  if (!errors.isEmpty()) {
    return res.status(400).json({ errors: errors.array() });
  }
  next();
};

// Route for student to join and get a token
router.post(
    '/join',
    [
        // Validation for the join request body
        body('studentName').trim().notEmpty().withMessage('studentName is required').escape(),
        body('class').trim().notEmpty().withMessage('class is required').escape(),
        body('rollNo').trim().notEmpty().withMessage('rollNo is required').escape(),
        body('studentPcId').optional().trim().escape() // Optional PC ID
    ],
    handleValidationErrors, 
    studentController.join 
);

// Disconnect is handled via WebSocket state/close event

module.exports = router; 