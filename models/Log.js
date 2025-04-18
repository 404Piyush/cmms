const mongoose = require('mongoose');

const logSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true
  },
  studentId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Student'
  },
  eventType: {
    type: String,
    enum: ['connection', 'app_block', 'website_block', 'usb_detection'],
    required: true
  },
  details: String,
  timestamp: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('Log', logSchema);