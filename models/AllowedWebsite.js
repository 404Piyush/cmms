const mongoose = require('mongoose');

const allowedWebsiteSchema = new mongoose.Schema({
    sessionCode: {
        type: String,
        required: true
      },website: {
    type: String,
    required: true,
    unique: true
  }
});

module.exports = mongoose.model('AllowedWebsite', allowedWebsiteSchema);