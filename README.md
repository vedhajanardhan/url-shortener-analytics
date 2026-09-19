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
