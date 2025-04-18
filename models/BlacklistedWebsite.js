const mongoose = require('mongoose');

const blacklistedWebsiteSchema = new mongoose.Schema({
  sessionCode: {
    type: String,
    required: true
  },
  website: {
    type: String,
    required: true
  }
});

module.exports = mongoose.model('BlacklistedWebsite', blacklistedWebsiteSchema);