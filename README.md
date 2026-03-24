# CodeComp 🚀

A real-time competitive coding platform featuring 1v1 rooms, live leaderboard updates, automated judging, and secure authentication using JWT and Google OAuth2.

---

## 1. Overview

CodeComp enables users to:

* Create and join coding rooms
* Compete in real-time contests
* Submit code using Judge0
* View live leaderboard updates via WebSockets
* Track contest history and performance statistics

---

## 2. System Architecture

### High-Level Architecture

```
+-------------------+
|   Frontend (TBD)  |
+--------+----------+
         |
         v
+-------------------+
| Spring Boot API   |
| (Backend Core)    |
+--------+----------+
         |
----------------------------------------------
|            |             |                 |
v            v             v                 v
+--------+   +--------+   +-----------+   +-------------+
| Redis  |   |RabbitMQ|   | Postgres  |   |  Judge0 API |
| Cache  |   | Queue  |   | Database  |   | Code Runner |
+--------+   +--------+   +-----------+   +-------------+
```

---

## 3. Tech Stack

| Component        | Technology          |
| ---------------- | ------------------- |
| Backend          | Spring Boot         |
| Database         | PostgreSQL          |
| Cache            | Redis               |
| Queue            | RabbitMQ            |
| Code Execution   | Judge0              |
| Authentication   | JWT + Google OAuth2 |
| Realtime         | WebSockets (STOMP)  |
| Containerization | Docker              |

---

## 4. Key System Design Decisions

### 4.1 Real-time Updates

* Uses WebSockets (STOMP over SockJS)
* Topic-based room updates:

  ```
  /topic/room/{roomId}
  ```

---

### 4.2 Asynchronous Code Execution

* Submissions are sent to RabbitMQ
* Worker processes handle execution via Judge0
* Prevents blocking API threads

---

### 4.3 Caching & Rate Limiting

* Redis is used for:

  * Rate limiting
  * Fast lookup operations

---

### 4.4 Authentication Strategy

#### Dual Authentication System:

1. JWT (Primary)
2. Google OAuth2 (Login Provider)

Flow:

```
Google Login → Backend → Generate JWT → Use JWT everywhere
```

---

### 4.5 Contest Model

* Room-based contests
* 2 participants per room
* Scoring based on:

  * Problems solved
  * Penalty (time + attempts)

---

## 5. User Flows

### 5.1 Login Flow

```
User → /oauth2/authorization/google → Google Login → Backend Callback → JWT Generated → Returned to user
```

---

### 5.2 Room Creation Flow

```
User → POST /rooms/create → Room created with:
- roomId
- password
- hostUserId
```

---

### 5.3 Join Room Flow

```
User → POST /rooms/join → Validate roomId + password → Add participant
```

---

### 5.4 Contest Flow

```
Start Contest
↓
Solve Problems
↓
Submit Code
↓
Judge0 Execution
↓
Update Leaderboard (WebSocket)
```

---

### 5.5 Contest End

#### Manual:

```
Host → /rooms/end
```

#### Automatic:

```
Scheduler → checks every 5s → ends contest
```

---

## 6. API Endpoints

### Auth

#### Google OAuth2 Login

```
GET /oauth2/authorization/google
```

Response:

```json
{
  "userId": 1,
  "email": "user@gmail.com",
  "token": "JWT_TOKEN"
}
```

---

### Rooms

#### Create Room

```
POST /rooms/create
Authorization: Bearer <token>
```

#### Join Room

```
POST /rooms/join
Authorization: Bearer <token>
```

#### Start Contest

```
POST /rooms/start
```

#### End Contest

```
POST /rooms/end
```

---

### Submissions

#### Submit Code

```
POST /submissions
```

---

### Leaderboard

#### Get Leaderboard

```
GET /rooms/{roomId}/leaderboard
```

---

### Contest History

```
GET /users/{userId}/history
```

---

## 7. WebSocket Usage

### Connect

```
ws://localhost:8081/ws
```

---

### Subscribe

```
/topic/room/{roomId}
```

---

### Example Payload

```json
{
  "roomId": 1,
  "leaderboard": [...],
  "myProblems": [...],
  "opponentProblems": [...]
}
```

---

## 8. Running the Project (Docker)

### Prerequisites

* Docker
* Docker Compose
* Java 21 (for build)

---

### Step 1: Build JAR

```
mvn clean package -DskipTests
```

---

### Step 2: Run Services

```
docker compose up --build
```

---

### Services Started

| Service     | Port  |
| ----------- | ----- |
| Backend     | 8081  |
| Postgres    | 5432  |
| Redis       | 6379  |
| RabbitMQ    | 5672  |
| RabbitMQ UI | 15672 |
| Judge0      | 2358  |

---

## 9. Database Access

Use pgAdmin:

* Host: localhost
* Port: 5432
* User: postgres
* Password: postgres
* DB: codecomp

---

## 10. Environment Configuration

Example:

```
spring.datasource.url=jdbc:postgresql://postgres:5432/codecomp
spring.datasource.username=postgres
spring.datasource.password=postgres

spring.data.redis.host=redis

spring.rabbitmq.host=rabbitmq
```

---

## 11. Deployment Notes

* Full system requires multi-container deployment

Can be deployed using:

* Fly.io (paid)
* Hybrid (Render + Neon + Upstash)

---

## 13. Author

Pritam Acharya
