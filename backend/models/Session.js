const mongoose = require('mongoose');

const sessionSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true,
    unique: true
  },
  adminId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Admin',
    required: true
  },
  students: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Student'
  }],
  startTime: {
    type: Date,
    default: Date.now
  },
  endTime: {
    type: Date,
    default: null
  },
  isActive: {
    type: Boolean,
    default: true
  }
});


sessionSchema.methods.shutdownSession = async function() {
  if (this.isActive) {
    this.isActive = false;
    this.endTime = Date.now();
    await this.save();
    return this;
  }
  throw new Error('Session is already inactive');
};

module.exports = mongoose.model('Session', sessionSchema);