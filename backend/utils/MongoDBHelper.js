
const mongoose = require("mongoose");

module.exports.getCollection = (name) => mongoose.connection.collection(name);
