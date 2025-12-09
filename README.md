# Nabat - Real-Time Safety Alert Platform

🚨 A Citizen.com-inspired platform for real-time safety alerts that provides users with instant notifications about crimes, incidents, and hazards near their location.

## 🏗️ Architecture

Built with **Hexagonal Architecture** (Ports & Adapters) to keep the business logic independent of frameworks and infrastructure.

### Technology Stack

- **Backend**: Java 25, Spring Boot 4.0
- **Database**: PostgreSQL 16 (with H2 for development)
- **Real-time**: WebSocket for instant notifications
- **Build Tool**: Gradle
- **Architecture**: Hexagonal (Ports & Adapters)

### Project Structure

```
src/main/java/org/example/nabat/
├── domain/              # Business entities (framework-independent)
│   └── model/          # Alert, Location, AlertType, AlertSeverity, AlertStatus
├── application/         # Business logic layer
│   ├── port/
│   │   ├── in/         # Use case interfaces (primary ports)
│   │   └── out/        # Repository/service interfaces (secondary ports)
│   └── service/        # Use case implementations (@UseCase)
├── adapter/            # Infrastructure layer
│   ├── in/             # Primary adapters (REST, WebSocket)
│   └── out/            # Secondary adapters (JPA, Notifications)
└── config/             # Spring configuration
```

## 🚀 Quick Start

### Prerequisites

- Java 25
- PostgreSQL 16 (or use H2 for quick testing)
- Gradle (wrapper included)

### Option 1: Run with H2 (No setup needed)

```bash
gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

Access H2 console: http://localhost:8080/h2-console

### Option 2: Run with PostgreSQL

1. **Setup Database**:
   ```bash
   # Install PostgreSQL, then run:
   setup-db.bat
   ```

2. **Start Application**:
   ```bash
   gradlew.bat bootRun
   ```

See [LOCAL_DB_SETUP.md](LOCAL_DB_SETUP.md) for detailed database setup instructions.

## 📡 API Endpoints

### REST API

**Create Alert**
```http
POST /api/v1/alerts
Content-Type: application/json

{
  "title": "Traffic Accident",
  "description": "Major accident on Main Street",
  "type": "ACCIDENT",
  "severity": "HIGH",
  "latitude": 42.6977,
  "longitude": 23.3219,
  "reportedBy": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Get Nearby Alerts**
```http
GET /api/v1/alerts/nearby?latitude=42.6977&longitude=23.3219&radiusKm=5
```

### WebSocket

Connect to real-time alerts:
```
ws://localhost:8080/ws/alerts?userId=<your-user-id>
```

## 🏛️ Domain Model

### Alert Types
- `CRIME` - Criminal activity
- `FIRE` - Fire incidents
- `ACCIDENT` - Traffic accidents
- `NATURAL_DISASTER` - Earthquakes, floods, etc.
- `MEDICAL_EMERGENCY` - Medical situations
- `MISSING_PERSON` - Missing person reports

### Alert Severity
- `CRITICAL` - Immediate danger (10km notification radius)
- `HIGH` - Serious situation (5km radius)
- `MEDIUM` - Moderate concern (2km radius)
- `LOW` - Minor incident (1km radius)

### Alert Status
- `ACTIVE` - Currently active alert
- `RESOLVED` - Resolved/closed alert

## 🧪 Testing

Run tests:
```bash
gradlew.bat test
```

The project follows a testing strategy with:
- **Unit tests** for domain logic (no Spring dependencies)
- **Integration tests** with `@DataJpaTest` for repositories
- **Web tests** with `@WebMvcTest` for controllers

## 🗄️ Database Schema

The application uses three main tables:
- **users** - User accounts and profiles
- **alerts** - Safety alerts with geolocation
- **user_subscriptions** - User notification preferences

The schema includes geospatial indexes for efficient radius-based queries using the Haversine formula.

## 📦 Build

Build the project:
```bash
gradlew.bat clean build
```

Create executable JAR:
```bash
gradlew.bat bootJar
```

Run the JAR:
```bash
java -jar build/libs/nabat-0.0.1-SNAPSHOT.jar
```

## 🐳 Docker Support

PostgreSQL with Docker Compose:
```bash
docker-compose up -d
```

This starts PostgreSQL and pgAdmin (accessible at http://localhost:5050).

## 🔧 Configuration

The application supports multiple profiles:

- **dev** - H2 in-memory database
- **default** - PostgreSQL on localhost
- **prod** - PostgreSQL with environment variables

Configure via `application.properties` or environment variables:
- `DATABASE_URL`
- `DATABASE_USERNAME`
- `DATABASE_PASSWORD`
- `ALLOWED_ORIGINS`

## 🌟 Key Features

- ✅ Real-time WebSocket notifications
- ✅ Geospatial radius-based alert queries
- ✅ Hexagonal architecture for testability
- ✅ Multi-profile configuration (dev/prod)
- ✅ Severity-based notification radius
- ✅ RESTful API with validation
- ✅ Sample data for testing

## 📚 Documentation

- [Local Database Setup](LOCAL_DB_SETUP.md)
- [Database Setup Guide](DATABASE_SETUP.md)

## 🤝 Contributing

This is a learning project implementing hexagonal architecture patterns. Contributions are welcome!

## 📄 License

This project is for educational purposes.

## 🔗 Inspiration

Inspired by Citizen.com's approach to real-time safety alerts and community awareness.

---

Built with ❤️ using Java 25, Spring Boot 4.0, and Hexagonal Architecture

