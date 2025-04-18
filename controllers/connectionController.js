const ConnectionManager = require('../utils/ConnectionManager');

// Heartbeat function removed as WebSocket handles liveness checks
/*
exports.heartbeat = async (req, res) => {
    try {
        const { session_code } = req.params;
        const { 
            student_pc,
            student_name,
            student_class,
            student_roll
        } = req.body;

        
        if (!student_pc || !student_name || !student_class || !student_roll) {
            return res.status(400).json({
                message: "Missing required student details"
            });
        }

        await ConnectionManager.updateConnection(
            session_code,
            student_pc,
            {
                student_name,
                student_class,
                student_roll
            }
        );

        res.status(200).json({ status: "ok" });

    } catch (error) {
        console.error("Error in heartbeat:", error);
        res.status(500).json({ message: "Server Error" });
    }
};
*/