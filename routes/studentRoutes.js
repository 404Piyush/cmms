/**
 * Defines routes related to student actions within a session.
 * Base Path: /api/session/:code/student
 * Includes joining and disconnecting from a session.
 * Input validation is applied.
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

// --- Routes ---

// Validate input for the join route
router.post(
    '/join',
    [
        body('studentName').trim().notEmpty().withMessage('studentName is required').escape(),
        body('studentId').trim().notEmpty().withMessage('studentId is required').escape(), // Note: Controller might use studentPcId
        body('class').trim().notEmpty().withMessage('class is required').escape(),
        body('rollNo').trim().notEmpty().withMessage('rollNo is required').escape(),
    ],
    handleValidationErrors, 
    studentController.join 
);

router.put('/disconnect', 
    [
        body('studentPcId').trim().notEmpty().withMessage('studentPcId is required').escape(),
    ],
    handleValidationErrors,
    studentController.disconnect
);

module.exports = router;