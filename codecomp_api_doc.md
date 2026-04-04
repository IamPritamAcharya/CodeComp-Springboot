# CodeComp Backend API Documentation

**Base URL:** `http://localhost:8081`  
**Authentication:** Most endpoints require `Authorization: Bearer <JWT>`  
**WebSocket endpoint:** `/ws` (SockJS + STOMP)

This document is intended for frontend integration.

---

## 1) Authentication flow

### JWT model
The backend issues a JWT containing:
- `sub` = user id
- `role` = `USER`
- expiry = 24 hours

The JWT filter reads the `Authorization` header, extracts the user id, and places it into the request as `userId`. Frontend code should send the token on every protected request.

### OAuth flow
The Google OAuth success callback is:

`GET /auth/oauth-success`

This endpoint is handled by the backend after Google login and redirects to:

`http://localhost:3000/oauth-success?token=...&userId=...&email=...`

The frontend route `/oauth-success` should read those query parameters and store the token.

---

## 2) Standard error response format

Runtime exceptions are converted to HTTP 400:

```json
{
  "error": "Room not found"
}
```

Unexpected exceptions are converted to HTTP 500:

```json
{
  "error": "Something went wrong"
}
```

Common runtime messages used by this backend include:
- `User not authenticated via OAuth2`
- `Invalid token`
- `Room not found`
- `Contest not active`
- `User not in room`
- `Invalid problem`
- `Wrong Password`
- `Room is full`
- `User already in this room`
- `User already in another active room`
- `Only host can start the contest`
- `Only host can end the contest`
- `Room already started or finished`
- `Need exactly 2 players to start`
- `Not enough problems in DB`
- `Too many submissions. Please slow down.`

---

## 3) Authentication endpoints

### 3.1 POST `/auth/login`

Creates a JWT for a user id.

**Auth:** No  
**Request:** Query parameter

```http
POST /auth/login?userId=1
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Notes:**
- This endpoint does not validate the user id against the database.
- It only generates a token for the supplied id.

---

### 3.2 GET `/auth/oauth-success`

OAuth2 success callback. The backend uses the authenticated Google profile, stores/updates the user, generates a JWT, and redirects to the frontend.

**Auth:** Handled by OAuth session  
**Request:** No body

**Redirect example:**
```text
http://localhost:3000/oauth-success?token=JWT_HERE&userId=12&email=user@example.com
```

**Notes:**
- If the user already exists by email, only the name is updated.
- New users are saved with provider = `GOOGLE`.

---

## 4) Room / contest endpoints

All endpoints in this section require a valid JWT unless noted otherwise.

### 4.1 POST `/rooms/create`

Creates a new room and adds the host as the first participant.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Request:** Query parameter

```http
POST /rooms/create?password=1234
```

`password` is optional. If omitted or blank, the backend generates a 4-digit password.

**Response:** `Room` object

```json
{
  "id": 101,
  "hostUserId": 1,
  "password": "1234",
  "status": "WAITING",
  "startTime": null,
  "endTime": null,
  "duration": null
}
```

**Behavior:**
- Creates room with status `WAITING`
- Creates a `Participant` row for the host with score `0`

---

### 4.2 POST `/rooms/join`

Joins an existing room.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Request:** Query parameters

```http
POST /rooms/join?roomId=101&password=1234
```

**Response:** plain string

```text
Joined successfully
```

**Other possible responses:**
```text
Wrong Password
Room is full
User already in this room
User already in another active room
```

**Behavior:**
- Room must exist
- Password must match exactly
- Room must have fewer than 2 participants
- A user cannot join multiple `WAITING` or `ACTIVE` rooms at the same time

---

### 4.3 POST `/rooms/start`

Starts the contest for a room.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Request:** Query parameter

```http
POST /rooms/start?roomId=101
```

**Response:** plain string

```text
Contest started
```

**Behavior:**
- Only the host can start the contest
- Room must be in `WAITING` status
- Exactly 2 participants must be present
- The backend assigns the first 3 problems from the `problems` table to the room
- For each participant, it creates `ParticipantProblem` entries for those assigned problems
- Room status becomes `ACTIVE`
- `startTime` is set to current time
- `duration` is set to `10 minutes`

**Failure examples:**
```text
Only host can start the contest
Room already started or finished
Need exactly 2 players to start
Not enough problems in DB
```

---

### 4.4 POST `/rooms/submit`

Submits code for a room problem. The submission is stored first and then sent to RabbitMQ for judging.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Request body:** `SubmissionRequest`

```json
{
  "roomId": 101,
  "problemId": 11,
  "code": "public class Main { ... }",
  "languageId": 54
}
```

**Important:**  
The backend fills `userId` from the JWT, so the frontend does **not** need to send `userId`.

**Response:**
```json
{
  "status": "PENDING"
}
```

**Behavior:**
- Submission is rate limited to 5 submissions per 10 seconds per user
- Room must be `ACTIVE`
- User must be in the room
- Problem must belong to the room
- A `Submission` row is created with status `PENDING`
- Submission id is sent to RabbitMQ queue `judge-queue`

**Failure examples:**
```json
{ "error": "Too many submissions. Please slow down." }
```

---

### 4.5 GET `/rooms/leaderboard?roomId=101`

Returns the computed room leaderboard.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Response:** array of `LeaderboardResponse`

```json
[
  {
    "userId": 1,
    "solved": 2,
    "penalty": 1240,
    "lastSolvedTime": 1712001200000,
    "rank": 1
  },
  {
    "userId": 2,
    "solved": 1,
    "penalty": 1760,
    "lastSolvedTime": 1712001800000,
    "rank": 2
  }
]
```

**Sorting rules:**
1. Higher `solved` first
2. Lower `penalty` first
3. Earlier `lastSolvedTime` first

**Penalty formula:**
- `penalty = attemptsPenalty * 600 + timeTakenSeconds`
- `timeTakenSeconds = (solvedAt - roomStartTime) / 1000`

---

### 4.6 POST `/rooms/end`

Ends the contest manually.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Request:** Query parameter

```http
POST /rooms/end?roomId=101
```

**Response:** `EndContestResponse`

```json
{
  "winnerUserId": 1,
  "result": "WIN"
}
```

**Possible `result` values:**
- `WIN`
- `DRAW`

**Behavior:**
- Only the host can end the contest
- Room must be `ACTIVE`
- Backend computes leaderboard and determines winner
- Room status becomes `FINISHED`
- `endTime` is set
- A `ContestHistory` entry is stored for each participant

**If the contest is tied:**
```json
{
  "winnerUserId": null,
  "result": "DRAW"
}
```

---

### 4.7 GET `/rooms/stats`

Returns contest statistics for the current user.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Response:** map

```json
{
  "totalContests": 8,
  "wins": 5,
  "losses": 2,
  "draws": 1,
  "totalSolved": 14,
  "winRate": 62.5
}
```

**Notes:**
- Uses the authenticated user id from the JWT
- Reads data from `contest_history`

---

### 4.8 GET `/rooms/profile`

Returns the current user profile summary plus recent contests.

**Auth:** Yes  
**Headers:**
```http
Authorization: Bearer <JWT>
```

**Response:**
```json
{
  "stats": {
    "totalContests": 8,
    "wins": 5,
    "losses": 2,
    "draws": 1,
    "totalSolved": 14,
    "winRate": 62.5
  },
  "recentMatches": [
    {
      "id": 41,
      "userId": 1,
      "roomId": 101,
      "solved": 2,
      "penalty": 1240,
      "result": "WIN",
      "timestamp": 1712002000000
    },
    {
      "id": 40,
      "userId": 1,
      "roomId": 99,
      "solved": 1,
      "penalty": 1760,
      "result": "LOSS",
      "timestamp": 1711999000000
    }
  ]
}
```

**Notes:**
- `recentMatches` is the latest 5 records from `contest_history`
- Order is newest first

---

## 5) WebSocket / realtime updates

### 5.1 STOMP endpoint `/ws`

The frontend should connect using SockJS/STOMP to:

```text
/ws
```

### 5.2 Subscribe topic

Subscribe to:

```text
/topic/room/{roomId}
```

Example:

```text
/topic/room/101
```

### 5.3 Payload format

The backend pushes a `RoomStateResponse` object.

```json
{
  "roomId": 101,
  "leaderboard": [
    {
      "userId": 1,
      "solved": 2,
      "penalty": 1240,
      "lastSolvedTime": 1712001200000,
      "rank": 1
    },
    {
      "userId": 2,
      "solved": 1,
      "penalty": 1760,
      "lastSolvedTime": 1712001800000,
      "rank": 2
    }
  ],
  "myProblems": [
    {
      "id": 501,
      "userId": 1,
      "roomId": 101,
      "problemId": 11,
      "attempts": 1,
      "penalty": 0,
      "solved": true,
      "solvedAt": 1712001200000
    }
  ],
  "opponentProblems": [
    {
      "id": 502,
      "userId": 2,
      "roomId": 101,
      "problemId": 11,
      "attempts": 0,
      "penalty": 0,
      "solved": false,
      "solvedAt": null
    }
  ]
}
```

**Important frontend note:**  
The backend names these fields `myProblems` and `opponentProblems`, but it currently fills them based on internal user ordering in the room map. The payload does not explicitly identify which array belongs to the authenticated user. Frontend code should not assume the first list always belongs to the current viewer unless it verifies the user id.

---

## 6) Data models returned by the API

### 6.1 `Room`
Returned by `POST /rooms/create`

```json
{
  "id": 101,
  "hostUserId": 1,
  "password": "1234",
  "status": "WAITING",
  "startTime": null,
  "endTime": null,
  "duration": null
}
```

### 6.2 `LeaderboardResponse`

```json
{
  "userId": 1,
  "solved": 2,
  "penalty": 1240,
  "lastSolvedTime": 1712001200000,
  "rank": 1
}
```

### 6.3 `EndContestResponse`

```json
{
  "winnerUserId": 1,
  "result": "WIN"
}
```

### 6.4 `ContestHistory`

```json
{
  "id": 41,
  "userId": 1,
  "roomId": 101,
  "solved": 2,
  "penalty": 1240,
  "result": "WIN",
  "timestamp": 1712002000000
}
```

### 6.5 `SubmissionRequest` request body

```json
{
  "roomId": 101,
  "problemId": 11,
  "code": "public class Main { ... }",
  "languageId": 54
}
```

### 6.6 `RoomStateResponse`

```json
{
  "roomId": 101,
  "leaderboard": [],
  "myProblems": [],
  "opponentProblems": []
}
```

---

## 7) Frontend integration checklist

### Required headers
For protected requests:
```http
Authorization: Bearer <JWT>
Content-Type: application/json
```

### Typical flow
1. User signs in through Google OAuth or receives a manual token from `/auth/login`
2. Frontend stores JWT
3. Frontend calls `/rooms/create` or `/rooms/join`
4. When room reaches 2 participants, host calls `/rooms/start`
5. Frontend subscribes to `/ws` and listens on `/topic/room/{roomId}`
6. Users submit code through `/rooms/submit`
7. UI updates from websocket messages and leaderboard polling if needed
8. Host ends contest with `/rooms/end` or backend auto-ends it after 10 minutes

### Suggested polling / realtime usage
- Use WebSocket for live leaderboard and problem state updates
- Use `/rooms/leaderboard` only if you need a manual refresh fallback

---

## 8) Quick endpoint summary

| Method | Path | Auth | Response type |
|---|---|---:|---|
| POST | `/auth/login` | No | JSON token |
| GET | `/auth/oauth-success` | OAuth session | Redirect |
| POST | `/rooms/create` | Yes | `Room` JSON |
| POST | `/rooms/join` | Yes | Plain string |
| POST | `/rooms/start` | Yes | Plain string |
| POST | `/rooms/submit` | Yes | `{ "status": "PENDING" }` |
| GET | `/rooms/leaderboard` | Yes | Array of leaderboard objects |
| POST | `/rooms/end` | Yes | `EndContestResponse` |
| GET | `/rooms/stats` | Yes | JSON map |
| GET | `/rooms/profile` | Yes | JSON map |
| WS | `/ws` | No | STOMP connection |
| Topic | `/topic/room/{roomId}` | N/A | `RoomStateResponse` |

---

## 9) Frontend examples

### Submit code with fetch
```js
await fetch("http://localhost:8081/rooms/submit", {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${token}`,
  },
  body: JSON.stringify({
    roomId: 101,
    problemId: 11,
    code: sourceCode,
    languageId: 54,
  }),
});
```

### Join room with fetch
```js
await fetch("http://localhost:8081/rooms/join?roomId=101&password=1234", {
  method: "POST",
  headers: {
    "Authorization": `Bearer ${token}`,
  },
});
```

### Subscribe to room updates
```js
stompClient.subscribe(`/topic/room/${roomId}`, (message) => {
  const roomState = JSON.parse(message.body);
  console.log(roomState);
});
```

---

## 10) Backend constraints worth knowing

- Contest duration is hardcoded to 10 minutes
- Exactly 2 participants are required to start
- First 3 problems in the database are assigned to every contest
- Submission rate limit is 5 submissions per 10 seconds per user
- Auto-end scheduler checks active rooms every 5 seconds
- Judge execution is done through Judge0
- Results are broadcast to frontend through Redis → WebSocket
