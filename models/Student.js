const mongoose = require('mongoose');

const studentSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true
  },
  name: {
    type: String,
    required: true
  },
  className: String,
  rollNo: String,
  pcId: {
    type: String,
    required: true
  },
  connectedAt: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('Student', studentSchema);