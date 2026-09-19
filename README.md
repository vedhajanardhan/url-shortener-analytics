# 🔗 URL Shortener & Analytics

A production-ready URL shortening and analytics platform built with **Java and Spring Boot**, designed to provide secure URL management, fast redirection, click tracking, analytics, caching, rate limiting, and QR code generation.

The system uses an event-driven architecture with **Apache Kafka**, **Redis caching**, **MySQL persistence**, and **JWT-based authentication**.

---

## 🚀 Live Demo

- **Backend API:** https://url-shortener-analytics-vgbg.onrender.com
- **Swagger UI:** https://url-shortener-analytics-vgbg.onrender.com/swagger-ui/index.html
- **Health Check:** https://url-shortener-analytics-vgbg.onrender.com/actuator/health

---

## ✨ Features

### 🔗 URL Shortening
- Create short URLs from long URLs
- Generate unique short codes
- Redirect users to the original URL
- Track URL creation and usage

### 📊 Click Analytics
- Track URL clicks
- Record click events asynchronously using Apache Kafka
- Maintain analytics data for shortened URLs
- Support analytics-oriented event processing

### ⚡ Redis Caching
- Cache frequently accessed URL mappings
- Reduce database load during redirects
- Improve response performance
- Redis-backed rate limiting and counters

### 🔐 JWT Authentication
- Secure authentication using JSON Web Tokens
- Protected API endpoints
- User-based URL management
- Stateless authentication architecture

### 🚦 Rate Limiting
- Protect APIs from excessive requests
- Redis-backed request limiting
- Helps prevent API abuse

### 📱 QR Code Generation
- Generate QR codes for shortened URLs
- Cache generated QR codes for improved performance

### 🗄️ Database Management
- MySQL for persistent application data
- Flyway for database schema migrations
- JPA/Hibernate for database interaction

### 📖 API Documentation
- Swagger/OpenAPI integration
- Interactive API testing
- Automatically generated API documentation

### 🩺 Health Monitoring
- Spring Boot Actuator
- Application health endpoint
- Liveness and readiness monitoring

### 🐳 Containerization
- Dockerized Spring Boot application
- Production-ready container configuration
- Suitable for cloud deployment

---

## 🏗️ Architecture

```text
                         ┌──────────────────────┐
                         │       Client         │
                         │ Browser / Postman    │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    Spring Boot API   │
                         │                      │
                         │ Authentication       │
                         │ URL Management       │
                         │ Redirection          │
                         │ Analytics APIs       │
                         └───────┬───────┬──────┘
                                 │       │
                    ┌────────────┘       └─────────────┐
                    ▼                                  ▼
             ┌─────────────┐                    ┌─────────────┐
             │    Redis    │                    │    MySQL    │
             │             │                    │             │
             │ Cache       │                    │ Users       │
             │ Rate Limit  │                    │ URLs        │
             │ Counters    │                    │ Analytics   │
             └─────────────┘                    └─────────────┘

                         ┌──────────────────────┐
                         │       Kafka          │
                         │                      │
                         │ Click Events         │
                         │ Async Processing     │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │ Analytics Processing │
                         └──────────────────────┘


🛠️ Tech Stack
| Category           | Technology                  |
| ------------------ | --------------------------- |
| Language           | Java 17                     |
| Framework          | Spring Boot 3.3.4           |
| Security           | Spring Security + JWT       |
| Database           | MySQL                       |
| ORM                | Spring Data JPA / Hibernate |
| Database Migration | Flyway                      |
| Cache              | Redis                       |
| Messaging          | Apache Kafka                |
| API Documentation  | Swagger / OpenAPI           |
| Monitoring         | Spring Boot Actuator        |
| Build Tool         | Maven                       |
| Containerization   | Docker                      |
| Deployment         | Render                      |
| Version Control    | Git + GitHub                |

📂 Project Structure
url-shortener-analytics/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── vedha/
│   │   │           └── urlshortener/
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── migration/
│   │
│   └── test/
│
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
└── .gitignore

🔐 Authentication

The application uses JWT-based authentication.

The general authentication flow is:

Client
   │
   │ Login / Register
   ▼
Authentication API
   │
   │ JWT Token
   ▼
Client
   │
   │ Authorization: Bearer <token>
   ▼
Protected APIs
JWT allows the backend to authenticate requests without maintaining server-side session state.

🔗 URL Shortening Flow
User submits long URL
        │
        ▼
Spring Boot API
        │
        ├── Validate request
        │
        ├── Generate unique short code
        │
        ├── Store mapping in MySQL
        │
        └── Cache mapping in Redis
        │
        ▼
Return shortened URL

📊 Analytics Flow

Click analytics are designed around asynchronous event processing.

User clicks short URL
        │
        ▼
Spring Boot API
        │
        ├── Resolve short URL
        │
        ├── Redirect user
        │
        └── Publish click event
                    │
                    ▼
              Apache Kafka
                    │
                    ▼
          Analytics Processing
                    │
                    ▼
                MySQL
Using Kafka helps separate the redirect path from analytics processing and supports event-driven processing.

---

## ⚡ Redis Usage

Redis is used for performance-sensitive operations such as:

- URL mapping cache
- Rate limiting
- Counters
- QR code caching
- Frequently accessed data

This reduces unnecessary database queries and improves the responsiveness of URL redirection.

---

## 🚦 Rate Limiting

The application implements Redis-backed rate limiting to protect APIs from excessive requests.

```text
Incoming Request
       │
       ▼
Redis Rate Limiter
       │
   ┌───┴────┐
   │        │
Allowed   Limit Exceeded
   │        │
   ▼        ▼
 API      HTTP Error
This helps protect the service from abusive or unusually high request rates.

📱 QR Code Generation

The platform supports QR code generation for shortened URLs.
Short URL
    │
    ▼
QR Generator
    │
    ▼
QR Code
Generated QR codes can be cached using Redis to avoid unnecessary regeneration.

🗄️ Database

The application uses MySQL for persistent storage.

Major data areas include:

-Users
-URL mappings
-Click/analytics information

Database schema changes are managed through Flyway migrations.

This allows database changes to be version-controlled and applied automatically during application startup.

📖 API Documentation
Interactive API documentation is available through Swagger UI.

Swagger UI

https://url-shortener-analytics-vgbg.onrender.com/swagger-ui/index.html

Swagger provides an interactive interface for exploring and testing the backend APIs.

🩺 Health Monitoring

The application uses Spring Boot Actuator for health monitoring.

Health Endpoint:

https://url-shortener-analytics-vgbg.onrender.com/actuator/health

The endpoint exposes application health information including:
-Application status
-Liveness
-Readiness

☁️ Deployment
The backend is deployed on Render using Docker.

Production Architecture:
                    ┌───────────────────┐
                    │      Render       │
                    │   Spring Boot API │
                    └─────────┬─────────┘
                              │
             ┌────────────────┼────────────────┐
             │                │                │
             ▼                ▼                ▼
        ┌─────────┐      ┌─────────┐      ┌─────────┐
        │  Aiven  │      │ Upstash │      │  Aiven  │
        │  MySQL  │      │  Redis  │      │  Kafka  │
        └─────────┘      └─────────┘      └─────────┘
Production Services:-
-Application: Render
-Database: Aiven MySQL
-Cache: Upstash Redis
-Messaging: Aiven Apache Kafka

## 🐳 Docker

The project includes a Dockerfile for containerized deployment.

### Build

```bash
docker build -t url-shortener-analytics .
####Run

docker run -p 8080:8080 url-shortener-analytics

For local multi-service development, the project also includes Docker Compose configuration.

## 💻 Local Setup

### Prerequisites

- Java 17
- Maven
- MySQL
- Redis
- Apache Kafka
- Docker (optional)

### Clone Repository

```bash
git clone https://github.com/vedhajanardhan/url-shortener-analytics.git
cd url-shortener-analytics

Build & Run
mvn clean install
mvn spring-boot:run

🔑 Environment Variables

Configure the required environment variables for:

-MySQL
-Redis
-Apache Kafka
-JWT
-Application URL
-CORS

🧪 Testing

Run tests:
mvn test
Build and verify:
mvn clean verify
APIs can be tested using Swagger UI or Postman.

🔒 Security
-JWT-based authentication
-Protected API endpoints
-Redis-backed rate limiting
-Environment-based secret configuration
-CORS configuration

📈 Performance & Scalability
-Redis provides caching for frequently accessed URL mappings.
-Kafka enables asynchronous click-event processing.
-Flyway provides version-controlled database migrations.
-JWT enables stateless authentication.

🧠 Engineering Concepts
-REST API development
-Spring Boot
-Spring Security
-JWT authentication
-JPA/Hibernate
-MySQL
-Redis caching
-Rate limiting
-Apache Kafka
-Event-driven architecture
-Docker
-API documentation
-Health monitoring
-Cloud deployment

🚀 Future Improvements
-Advanced analytics dashboard
-Geographic click analytics
-Device and browser analytics
-Custom URL aliases
-URL expiration
-CI/CD pipeline
-Prometheus/Grafana monitoring
-Distributed tracing

👩‍💻 Author

Vedha Janardhan

Information Science Engineering Student
Backend-Focused Software Engineer/developer

GitHub:
https://github.com/vedhajanardhan
