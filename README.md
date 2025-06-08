# 🎓 Classroom Management and Monitoring System (CMMS)

A comprehensive real-time classroom management solution that enables teachers to monitor and control student computer activities during sessions. The system consists of a robust backend API with WebSocket support and a desktop client application.

## 📸 Screenshots

### Teacher Dashboard
![Teacher Interface](docs/images/teacher-dashboard.jpg)
*Real-time monitoring of student activities with session management controls*

### Student Connection Interface  
![Student Interface](docs/images/student-interface.jpg)
*Clean and intuitive student connection interface*

### Session Management
![Session Management](docs/images/session-management.jpg)
*Create and manage classroom sessions with real-time controls*

### Monitoring Features
![Monitoring](docs/images/monitoring-features.jpg)
*Advanced monitoring capabilities including app blocking and website filtering*

## 🚀 Features

### 🧑‍🏫 For Teachers
- **Real-time Session Management**: Create, monitor, and end classroom sessions
- **Student Activity Monitoring**: View real-time student computer activities
- **Application Control**: Block/allow specific applications during sessions
- **Website Filtering**: Manage allowed/blocked websites
- **Live Notifications**: Receive instant updates on student activities
- **Session Analytics**: Track session duration and student participation

### 👨‍🎓 For Students
- **Easy Session Joining**: Simple interface to join classroom sessions
- **Real-time Sync**: Automatic synchronization with teacher controls
- **Activity Tracking**: Transparent activity monitoring
- **Secure Connection**: JWT-based authentication and secure WebSocket connections

### 🔧 Technical Features
- **WebSocket Communication**: Real-time bidirectional communication
- **JWT Authentication**: Secure session-based authentication
- **MongoDB Integration**: Robust data persistence
- **Cross-platform Desktop App**: Java-based client application
- **RESTful API**: Well-structured API endpoints
- **Rate Limiting**: Built-in protection against abuse
- **Security Headers**: Helmet.js integration for enhanced security

## 🏗️ Architecture

```
CMMS/
├── backend/           # Node.js/Express Backend
│   ├── config/        # Database & environment configuration
│   ├── controllers/   # API endpoint controllers
│   ├── models/        # MongoDB data models
│   ├── routes/        # Express route definitions
│   ├── middleware/    # Custom middleware functions
│   ├── utils/         # Utility functions and helpers
│   └── server.js      # Main server entry point
│
├── frontend/          # Java Desktop Application
│   └── CMMS 3/        # Main application directory
│       ├── src/       # Java source code
│       ├── target/    # Compiled artifacts
│       └── pom.xml    # Maven configuration
│
└── docs/              # Documentation and assets
    └── images/        # Screenshot images
```

## 🛠️ Technology Stack

### Backend
- **Runtime**: Node.js
- **Framework**: Express.js
- **Database**: MongoDB with Mongoose ODM
- **Real-time**: WebSocket (ws library)
- **Authentication**: JSON Web Tokens (JWT)
- **Security**: Helmet.js, CORS, Rate limiting
- **Environment**: dotenv for configuration

### Frontend
- **Language**: Java
- **Build Tool**: Maven
- **Database Driver**: MongoDB Java Driver
- **UI Framework**: JavaFX/Swing

## 📋 Prerequisites

### Backend Requirements
- Node.js (v14 or higher)
- npm or yarn
- MongoDB database

### Frontend Requirements  
- Java JDK 8 or higher
- Maven 3.6+

## ⚡ Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/cmms.git
cd cmms
```

### 2. Backend Setup

```bash
# Navigate to backend directory
cd backend

# Install dependencies
npm install

# Create environment file
cp .env.example .env

# Configure your environment variables in .env
# MONGO_URI=your_mongodb_connection_string
# JWT_SECRET=your_jwt_secret_key
# PORT=5001

# Start development server
npm run dev
```

### 3. Frontend Setup

```bash
# Navigate to frontend directory
cd frontend/CMMS\ 3

# Compile the application
mvn clean compile

# Run the application
mvn exec:java -Dexec.mainClass="com.cmms.ui.MainApplication"

# Or build executable JAR
mvn clean package
java -jar target/cmms-app.jar
```

## 🔧 Configuration

### Backend Environment Variables

Create a `.env` file in the backend directory:

```bash
# Database Configuration
MONGO_URI=mongodb://localhost:27017/classroom
# or for MongoDB Atlas:
# MONGO_URI=mongodb+srv://username:password@cluster.mongodb.net/classroom

# JWT Secret (generate a strong secret)
JWT_SECRET=your-super-secret-jwt-key-here

# Server Configuration
PORT=5001
NODE_ENV=development

# Security Settings (optional)
CORS_ORIGIN=http://localhost:3000
RATE_LIMIT_WINDOW_MS=900000
RATE_LIMIT_MAX_REQUESTS=100
```

### Frontend Configuration

Update the MongoDB connection in the Java application:

```java
// src/main/java/com/cmms/utils/MongoDBHelper.java
private static final String CONN_STR = System.getenv("MONGODB_URI");
```

Set environment variable before running:
```bash
export MONGODB_URI="your_mongodb_connection_string"
```

## 🐳 Docker Setup (Optional)

### Using Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down
```

## 📡 API Documentation

### Authentication Endpoints

#### Create Session
```http
POST /api/session/create
Content-Type: application/json

{
  "teacherId": "teacher123",
  "sessionName": "Math Class"
}
```

#### Join Session  
```http
POST /api/session/join
Content-Type: application/json

{
  "sessionCode": "ABC123",
  "studentId": "student456",
  "studentName": "John Doe"
}
```

### WebSocket Events

#### Client Events
- `authenticate`: Authenticate with JWT token
- `get_session_details`: Request session information
- `block_application`: Block specific application
- `update_blocked_websites`: Update website blocklist

#### Server Events
- `connection_ack`: Connection acknowledgment
- `student_joined`: New student joined session
- `settings_update`: Settings changed notification
- `force_disconnect`: Force client disconnection

## 🧪 Testing

### Backend Tests
```bash
cd backend
npm test
```

### API Testing with Postman
1. Import the Postman collection from `docs/api/CMMS.postman_collection.json`
2. Set up environment variables
3. Run the test suite

## 🚀 Deployment

### Backend Deployment (Heroku)

```bash
# Install Heroku CLI
# Login to Heroku
heroku login

# Create app
heroku create your-cmms-backend

# Set environment variables
heroku config:set MONGO_URI="your_production_mongodb_uri"
heroku config:set JWT_SECRET="your_production_jwt_secret"

# Deploy
git push heroku main
```

### Frontend Distribution

```bash
cd frontend/CMMS\ 3

# Build executable JAR
mvn clean package

# The executable will be in target/cmms-app.jar
# Distribute this JAR file to client machines
```

## 🔒 Security Considerations

- **Environment Variables**: Never commit `.env` files or hardcode credentials
- **JWT Secrets**: Use strong, randomly generated secrets in production
- **CORS Configuration**: Restrict CORS origins in production
- **Rate Limiting**: Implement appropriate rate limits for your use case
- **Input Validation**: All API inputs are validated using express-validator
- **Security Headers**: Helmet.js provides security headers

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Development Guidelines
- Follow existing code style and conventions
- Add tests for new features
- Update documentation as needed
- Ensure all tests pass before submitting PR

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

If you encounter any issues or have questions:

1. Check the [Issues](https://github.com/yourusername/cmms/issues) page
2. Create a new issue with detailed description
3. Include logs and steps to reproduce

## 🙏 Acknowledgments

- Built with ❤️ for modern classroom management
- Thanks to all contributors and testers
- Special thanks to the open-source community

---
## 👥 Contributors

- [@404Piyush](https://github.com/404Piyush) – Lead Developer & System Architect
- [@Hitansh1601](https://github.com/Hitansh1601) – Documentation & Report Preparation
- [@vaibhavmish20](https://github.com/vaibhavmish20) – Presentation & Report Formatting  


<div align="center">
  <strong>Happy Teaching! 🎓</strong>
</div> 