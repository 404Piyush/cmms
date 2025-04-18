const MongoDBHelper = require("./MongoDBHelper");

class ConnectionManager {
    constructor() {
        this.connections = new Map(); 
        this.checkInterval = null;
        this.timeoutSeconds = process.env.HEARTBEAT_TIMEOUT_SECONDS || 5;
    }

    async updateConnection(sessionCode, studentPc, studentDetails) {
        const now = Date.now();
        const existing = this.connections.get(studentPc);
        
        
        if (existing && !existing.isConnected) {
            await this.createNotification(sessionCode, {
                ...studentDetails,
                type: 'reconnection',
                message: `Student ${studentDetails.student_name} has reconnected`
            });
        }

        this.connections.set(studentPc, {
            lastPing: now,
            sessionCode,
            details: studentDetails,
            isConnected: true
        });
    }

    async checkConnections() {
        const now = Date.now();
        const timeoutMs = this.timeoutSeconds * 1000;

        for (const [studentPc, connection] of this.connections) {
            if (connection.isConnected && (now - connection.lastPing) > timeoutMs) {
                connection.isConnected = false;
                
                await this.createNotification(connection.sessionCode, {
                    ...connection.details,
                    type: 'disconnection',
                    message: `Student ${connection.details.student_name} has disconnected`
                });
            }
        }
    }

    async createNotification(sessionCode, details) {
        const notificationsCollection = MongoDBHelper.getCollection("notifications");
        await notificationsCollection.insertOne({
            session_code: sessionCode,
            type: details.type,
            student_name: details.student_name,
            student_class: details.student_class,
            student_roll: details.student_roll,
            message: details.message,
            created_at: new Date(),
            isRead: false
        });
    }

    startMonitoring() {
        if (!this.checkInterval) {
            this.checkInterval = setInterval(
                () => this.checkConnections(), 
                1000 
            );
        }
    }

    stopMonitoring() {
        if (this.checkInterval) {
            clearInterval(this.checkInterval);
            this.checkInterval = null;
        }
    }
}

module.exports = new ConnectionManager(); // Export singleton instance