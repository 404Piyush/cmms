/**
 * Main server setup file for the Classroom Management and Monitoring System.
 * Configures Express, middleware (security, CORS, JSON parsing), WebSocket server,
 * database connection, API routes, and starts the HTTP server.
 */
require("dotenv").config();
const express = require("express");
const cors = require("cors");
const http = require('http');
const { WebSocketServer } = require('ws');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');
const connectDB = require("./config/db");
const sessionRoutes = require("./routes/sessionRoutes");
const ConnectionManager = require('./utils/ConnectionManager');
const adminRoutes = require('./routes/adminRoutes');
const studentRoutes = require('./routes/studentRoutes');
const appRoutes = require('./routes/appRoutes');
// const notificationRoutes = require('./routes/notificationRoutes'); // REMOVED
// const assetRoutes = require('./routes/assetRoutes'); // REMOVED
// const workOrderRoutes = require('./routes/workOrderRoutes'); // REMOVED
const app = express();
const PORT = process.env.PORT || 5000;

app.use(helmet());

const limiter = rateLimit({
	windowMs: 15 * 60 * 1000,
	max: 100,
	standardHeaders: true,
	legacyHeaders: false,
});
app.use(limiter);

app.use(express.json());
app.use(cors());

connectDB();

app.use("/api/session", sessionRoutes);
app.use("/api/admin", adminRoutes);
app.use("/api/students", studentRoutes);
app.use("/api/app", appRoutes);
// app.use("/api/notifications", notificationRoutes); // REMOVED
// app.use("/api/assets", assetRoutes); // REMOVED
// app.use("/api/workorders", workOrderRoutes); // REMOVED

const server = http.createServer(app);

const wss = new WebSocketServer({ server });

const sessions = {};

wss.on('connection', (ws, req) => {
    console.log('Client connected via WebSocket');
    
    const urlParams = new URLSearchParams(req.url.split('?')[1]);
    const sessionCode = urlParams.get('sessionCode');
    const role = urlParams.get('role');
    const userId = urlParams.get('userId');

    if (!sessionCode || !role || !userId) {
        console.log('WebSocket connection rejected: Missing sessionCode, role, or userId');
        ws.terminate();
        return;
    }

    // TODO: Add validation for sessionCode & userId against active sessions/tokens
    console.log(`WebSocket connected: Session=${sessionCode}, Role=${role}, ID=${userId}`);

    ws.sessionCode = sessionCode;
    ws.role = role;
    ws.userId = userId;

    if (!sessions[sessionCode]) {
        sessions[sessionCode] = { teacher: null, students: new Map() };
    }

    if (role === 'teacher') {
        if (sessions[sessionCode].teacher) {
             console.log(`Teacher already connected for session ${sessionCode}. Terminating new connection.`);
             ws.terminate();
             return;
        }
        sessions[sessionCode].teacher = ws;
        console.log(`Teacher ${userId} connected to session ${sessionCode}`);
    } else if (role === 'student') {
        sessions[sessionCode].students.set(userId, ws);
        console.log(`Student ${userId} connected to session ${sessionCode}`);
        const teacherWs = sessions[sessionCode].teacher;
        if (teacherWs && teacherWs.readyState === ws.OPEN) {
            teacherWs.send(JSON.stringify({ type: 'student_joined', payload: { studentId: userId } }));
        }
    }

    ws.on('message', (message) => {
        console.log('received: %s', message);
        try {
            const parsedMessage = JSON.parse(message);
            
            const targetSession = sessions[ws.sessionCode];
            if (!targetSession) return;

            if (ws.role === 'student') {
                const teacherWs = targetSession.teacher;
                if (teacherWs && teacherWs.readyState === ws.OPEN) {
                    parsedMessage.payload = parsedMessage.payload || {}; // Ensure payload exists
                    parsedMessage.payload.studentId = ws.userId;
                    teacherWs.send(JSON.stringify(parsedMessage));
                    console.log(`Forwarded message from student ${ws.userId} to teacher.`);
                }
            } else if (ws.role === 'teacher') {
                const targetStudentId = parsedMessage.targetStudentId;
                if (targetStudentId) {
                    const studentWs = targetSession.students.get(targetStudentId);
                    if (studentWs && studentWs.readyState === ws.OPEN) {
                        studentWs.send(JSON.stringify(parsedMessage));
                         console.log(`Sent command from teacher to student ${targetStudentId}.`);
                    }
                } else {
                    targetSession.students.forEach((studentWs, studentId) => {
                        if (studentWs.readyState === ws.OPEN) {
                            studentWs.send(JSON.stringify(parsedMessage));
                        }
                    });
                    console.log(`Broadcast command from teacher to all students in session ${ws.sessionCode}.`);
                }
            }

        } catch (error) {
            console.error('Failed to parse message or invalid message format:', error);
        }
    });

    ws.on('close', () => {
        console.log(`WebSocket disconnected: Session=${ws.sessionCode}, Role=${ws.role}, ID=${ws.userId}`);
        const targetSession = sessions[ws.sessionCode];
        if (!targetSession) return;

        if (ws.role === 'teacher') {
            if (targetSession.teacher === ws) {
                targetSession.teacher = null;
                 console.log(`Teacher ${ws.userId} disconnected from session ${ws.sessionCode}`);
            }
        } else if (ws.role === 'student') {
            if (targetSession.students.get(ws.userId) === ws) {
                targetSession.students.delete(ws.userId);
                console.log(`Student ${ws.userId} disconnected from session ${ws.sessionCode}`);
                const teacherWs = targetSession.teacher;
                if (teacherWs && teacherWs.readyState === ws.OPEN) {
                    teacherWs.send(JSON.stringify({ type: 'student_left', payload: { studentId: ws.userId } }));
                }
            }
        }
        if (targetSession.teacher === null && targetSession.students.size === 0) {
            delete sessions[ws.sessionCode];
            console.log(`Session ${ws.sessionCode} closed as it is empty.`);
        }
    });

    ws.on('error', (error) => {
        console.error('WebSocket error:', error);
        ws.close(); 
    });
});

server.listen(PORT, () => {
    console.log(`Server (including WebSocket) running on port ${PORT}`);
    ConnectionManager.startMonitoring();
});

process.on('SIGTERM', () => {
    console.log('SIGTERM signal received: Closing connections.');
    ConnectionManager.stopMonitoring();
    server.close(() => {
        console.log('HTTP server closed.');
        process.exit(0);
    });
});
