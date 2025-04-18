/**
 * Controller functions for student actions within a session.
 * Handles joining and disconnecting students.
 */
const crypto = require('crypto');
const MongoDBHelper = require("../utils/MongoDBHelper");

exports.join = async (req, res) => {
  try {
    const { studentName, class: studentClass, rollNo, studentPcId } = req.body;
    const sessionCode = req.params.code; 

    // --- CHECK SESSION FIRST ---
    const sessionsCollection = MongoDBHelper.getCollection("sessions");
    const session = await sessionsCollection.findOne({ 
      session_code: sessionCode, 
      isSessionOn: true 
    });
    if (!session) {
      return res.status(404).json({ message: "Invalid session code or session inactive" });
    }
    if (session.expiresAt && new Date() > new Date(session.expiresAt)) {
      return res.status(400).json({ message: "Session expired" });
    }
    // --- END CHECK SESSION ---
    
    // Now check student data
    if (!studentName || !studentClass || !rollNo) {
      return res.status(400).json({ message: "Missing required student data" });
    }

    
    const studentDoc = {
      session_code: sessionCode,
      student_name: studentName,
      class: studentClass,
      roll_no: rollNo,
      student_pc: studentPcId || crypto.randomBytes(8).toString('hex'),
      isConnected: true,
      joinedAt: new Date()
    };

    
    const studentsCollection = MongoDBHelper.getCollection("students");
    const result = await studentsCollection.insertOne(studentDoc);
    if (!result.acknowledged) {
      return res.status(500).json({ message: "Failed to save student data" });
    }

    
    await sessionsCollection.updateOne(
      { session_code: sessionCode },
      { $inc: { studentCount: 1 } }
    );

    
    res.status(201).json({
      student_pc: studentDoc.student_pc,
      message: "Successfully joined session"
    });
  } catch (error) {
    console.error("Error in join session:", error);
    res.status(500).json({ message: "Server Error" });
  }
};


exports.disconnect = async (req, res) => {
  try {
    const sessionCode = req.params.code;
    const { studentPcId } = req.body;

    // Check body first (via validator) - handled by router
    // if (!studentPcId) {
    //   return res.status(400).json({ message: "Missing studentPcId in request body" });
    // }

    const studentsCollection = MongoDBHelper.getCollection("students");
    const sessionsCollection = MongoDBHelper.getCollection("sessions");

    // --- CHECK SESSION FIRST ---
    const session = await sessionsCollection.findOne({ session_code: sessionCode });
    if (!session) {
      // Use 404 for consistency if session doesn't exist
      return res.status(404).json({ message: "Session not found" }); 
    }
    // Optional: Check if session is active? Depends on if disconnect should work on inactive sessions
    // if (!session.isSessionOn) {
    //     return res.status(400).json({ message: "Session is inactive" });
    // }
    // --- END CHECK SESSION ---

    // Now find the student within the (valid) session
    const student = await studentsCollection.findOne({
      session_code: sessionCode,
      student_pc: studentPcId,
      isConnected: true,
    });
    if (!student) {
      return res.status(404).json({ message: "Student not found or already disconnected" });
    }

    
    const disconnectTime = new Date();
    const totalTimeConnected = Math.floor((disconnectTime - new Date(student.joinedAt)) / 1000);

    
    let remainingDuration = null;
    if (session.expiresAt) {
      remainingDuration = Math.max(0, Math.floor((new Date(session.expiresAt) - disconnectTime) / 1000));
    }

    
    const result = await studentsCollection.updateOne(
      { _id: student._id },
      { $set: { isConnected: false, disconnectedAt: disconnectTime } }
    );
    if (result.modifiedCount === 0) {
      return res.status(500).json({ message: "Failed to disconnect student" });
    }

    
    await sessionsCollection.updateOne(
      { session_code: sessionCode },
      { $inc: { studentCount: -1 } }
    );

    
    res.status(200).json({
      message: "Student disconnected successfully",
      student: {
        student_name: student.student_name,
        class: student.class,
        roll_no: student.roll_no,
        totalTimeConnected, 
      },
      remainingSessionDuration: remainingDuration 
    });
  } catch (error) {
    console.error("Error disconnecting student:", error);
    res.status(500).json({ message: "Server Error" });
  }
};