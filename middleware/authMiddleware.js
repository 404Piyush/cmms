/**
 * Authentication and authorization middleware.
 * Provides functions to verify JWT tokens and check for specific roles (e.g., teacher).
 */
const jwt = require('jsonwebtoken'); // Assuming you'll use JWT for tokens

// Placeholder function to verify JWT token
const authenticateToken = (req, res, next) => {
    // Get token from Authorization header (Bearer TOKEN)
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (token == null) {
        // No token provided
        return res.status(401).json({ message: 'Authentication token required' });
    }
    
    // Basic check for JWT structure (3 parts separated by dots)
    if (token.split('.').length !== 3) {
        console.error('JWT Format Error: Token does not have 3 parts');
        return res.status(403).json({ message: 'Invalid token format' });
    }

    // Use the actual secret from environment variables
    const secretKey = process.env.JWT_SECRET;
    if (!secretKey) {
        // This should not happen if the createSession check is in place, but good practice
        console.error('FATAL ERROR: JWT_SECRET is not defined in .env for verification.');
        return res.status(500).json({ message: 'Server configuration error.'});
    }

    jwt.verify(token, secretKey, (err, user) => {
        if (err) {
            // Token is invalid or expired
            console.error('JWT Verification Error:', err.message);
            return res.status(403).json({ message: 'Invalid or expired token' }); 
        }
        // Token is valid, attach user payload to request object
        req.user = user; // The payload decoded from JWT (e.g., { userId: '...', role: 'teacher' })
        console.log('Authenticated user:', user);
        next(); // Proceed to the next middleware or route handler
    });
};

// Placeholder function to check if the authenticated user is a teacher/admin
const isTeacher = (req, res, next) => {
    // This middleware should run *after* authenticateToken
    if (!req.user) {
        // Should not happen if authenticateToken ran first
        return res.status(500).json({ message: 'User not authenticated'});
    }
    
    if (req.user.role !== 'teacher') { // Assuming role is stored in the JWT payload
        return res.status(403).json({ message: 'Forbidden: Teacher access required' });
    }
    
    console.log('Authorization successful: User is a teacher');
    next(); // User is authenticated and is a teacher
};

module.exports = {
    authenticateToken,
    isTeacher,
}; 