const mongoose = require('mongoose');

const blacklistedAppSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true
  },
  appName: {
    type: String,
    required: true
  }
});

module.exports = mongoose.model('BlacklistedApp', blacklistedAppSchema);