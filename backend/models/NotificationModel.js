const NotificationModel = {
  session_code: String,      
  type: String,             
  student_name: String,     
  student_class: String,    
  student_roll: String,     
  message: String,          
  created_at: Date,        
  isRead: Boolean,         
  student_pc: String       
};

module.exports = NotificationModel;