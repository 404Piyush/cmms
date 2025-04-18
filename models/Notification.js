const mongoose = require('mongoose');

const notificationSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true
  },
  studentId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Student'
  },
  message: {
    type: String,
    required: true
  },
  type: {
    type: String,
    enum: ['connection', 'app_termination', 'usb_alert', 'website_block'],
    required: true
  },
  read: {
    type: Boolean,
    default: false
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('Notification', notificationSchema);