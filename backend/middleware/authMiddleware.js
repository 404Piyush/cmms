/**
 * Authentication and authorization middleware (REST API).
 * Provides functions to verify JWT tokens (sent via Authorization header) 
 * and check for specific roles (e.g., teacher) for protected REST endpoints.
 */
const jwt = require('jsonwebtoken');

/**
 * Middleware to authenticate JWT token.
 * Verifies the token from the Authorization header.
 */
function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (token == null) {
        // No token provided
        return res.status(401).json({ message: 'No token provided' });
    }

    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) {
            // Invalid token
            return res.status(403).json({ message: 'Invalid token' });
        }
        // Token is valid, attach user payload to request
        req.user = user;
        next(); // Proceed to the next middleware or route handler
    });
}

/**
 * Middleware to check if the authenticated user is a teacher.
 * Must be used after authenticateToken middleware.
 */
function isTeacher(req, res, next) {
    // Check if user object exists and has the role 'teacher'
    if (req.user && req.user.role === 'teacher') {
        next(); // User is a teacher, proceed
    } else {
        // User is not authorized
        res.status(403).json({ message: 'Forbidden: Requires teacher role' });
    }
}

module.exports = {
    authenticateToken,
    isTeacher
}; 