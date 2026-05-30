# BACKEND_FRONTEND_HANDOFF

Tài liệu này được tạo từ source backend hiện tại để bàn giao cho Codex 2 viết frontend Electron. Quy tắc đọc tài liệu: endpoint, DTO, response và logic nào không thấy trong source sẽ được ghi rõ là "Chưa implement", "Implement một phần" hoặc "Không xác định từ source hiện tại".

# 1. Project Overview

- Tên project: `StreamingClassRoom`.
- Backend dùng Spring Boot 3.5.14, Java 21.
- Mục tiêu app: backend cho lớp học trực tuyến / streaming classroom, có user theo role, classroom cố định, LiveKit token generation và webhook receiver.
- JWT authentication: đã implement một phần. Có login tạo access token JWT 30 phút; chưa có endpoint refresh/logout dù có `RefreshTokenService`.
- Role-based authorization: đã implement bằng Spring Security và `@PreAuthorize`. Role enum: `STUDENT`, `TEACHER`, `ADMIN`.
- LiveKit token generation: đã implement endpoint `POST /api/stream/token`.
- LiveKit webhook: implement một phần. Có endpoint nhận webhook, verify bằng LiveKit SDK `WebhookReceiver`, dispatcher theo event type, nhưng chưa có handler cụ thể nào.
- Classroom management: đã implement CRUD classroom, join by class code, list members.
- Live session management: Chưa implement API. Có entity `LiveSession`, `SessionParticipant`, `AttendanceRecord`, `Recording`, nhưng chưa có repository/service/controller cho live session.

# 2. Tech Stack

Đọc từ `pom.xml`:

| Tech | Version / Artifact | Notes |
|---|---|---|
| Java | `21` | `java.version` |
| Spring Boot | `3.5.14` | Parent `spring-boot-starter-parent` |
| Spring Web | `spring-boot-starter-web` | REST API |
| Spring Security | `spring-boot-starter-security` | JWT filter + method security |
| Spring Data JPA | `spring-boot-starter-data-jpa` | JPA/Hibernate |
| Spring Validation | `spring-boot-starter-validation` | Used by classroom DTO validation |
| Spring WebSocket | `spring-boot-starter-websocket` | Dependency exists, no WebSocket endpoint found |
| PostgreSQL | `org.postgresql:postgresql` runtime | Database driver |
| Flyway | `flyway-core`, `flyway-database-postgresql` | DB migration |
| Lombok | `org.projectlombok:lombok` | Entity/service boilerplate |
| JWT | `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.5` | App access token |
| LiveKit server SDK | `io.livekit:livekit-server:0.12.0` | Token + webhook receiver |
| Dotenv | `me.paulschwarz:spring-dotenv:4.0.0` | Loads env vars |
| Devtools | `spring-boot-devtools` runtime optional | Development |
| Tests | `spring-boot-starter-test`, `spring-security-test` | Test deps |

Dependency management overrides:

- `com.google.protobuf:protobuf-java:3.25.7`
- `com.squareup.okio:okio-jvm:3.16.4`
- `com.google.errorprone:error_prone_annotations:2.41.0`
- `org.checkerframework:checker-qual:3.52.0`

# 3. Configuration and Environment Variables

Đọc từ `src/main/resources/application.properties` và config classes:

| Config key | Purpose | Required by frontend? | Notes |
|---|---|---|---|
| `spring.application.name` | App name | No | `StreamingClassRoom` |
| `server.port` | HTTP port | Indirect | Không cấu hình trong source. Spring Boot default là `8080` nếu không override bên ngoài. |
| `spring.datasource.url` | PostgreSQL URL | No | `jdbc:postgresql://localhost:5435/${POSTGRES_DB:}` |
| `spring.datasource.username` | DB username | No | Env `${POSTGRES_USER}` |
| `spring.datasource.password` | DB password | No | Env `${POSTGRES_PASSWORD}` |
| `spring.jpa.hibernate.ddl-auto` | Schema mode | No | `validate`; DB phải có migration khớp entity |
| `spring.jpa.show-sql` | Log SQL | No | `true` |
| `spring.flyway.baseline-on-migrate` | Flyway behavior | No | `true` |
| `spring.flyway.validate-on-migrate` | Flyway behavior | No | `true` |
| `jwt.secret.key` | App JWT signing secret | No | Env `${JWT_SECRET_KEY}`; không expose cho frontend |
| `livekit.api.key` | LiveKit server API key | No | Env `${LIVEKIT_API_KEY}`; backend dùng tạo token và verify webhook |
| `livekit.api.secret` | LiveKit server secret | No | Env `${LIVEKIT_API_SECRET}`; không expose cho frontend |
| `livekit.url` | LiveKit websocket/server URL | Yes, ideally | Chưa có config trong source hiện tại. Frontend không thể lấy LiveKit URL từ backend hiện tại. |
| CORS config | Browser/Electron HTTP access | Yes | Không có global CORS config. Chỉ `StreamController` có `@CrossOrigin(origins = "*")`. |
| Webhook config | LiveKit webhook verification | No | Không có webhook secret riêng; dùng LiveKit API key/secret qua `WebhookReceiver`. |

# 4. Authentication and Authorization

## 4.1 Auth flow hiện có

Public endpoints:

- `POST /api/auth/register`
- `POST /api/auth/login`

`SecurityConfig`:

- `@EnableMethodSecurity` đã bật, nên `@PreAuthorize` hoạt động.
- CSRF disabled: `csrf(AbstractHttpConfigurer::disable)`.
- Session stateless: `SessionCreationPolicy.STATELESS`.
- `/api/auth/**` được `permitAll`.
- Tất cả endpoint khác cần authenticated JWT: `.anyRequest().authenticated()`.
- JWT filter đặt trước `UsernamePasswordAuthenticationFilter`.

Frontend phải gửi JWT như sau cho các request protected:

```http
Authorization: Bearer <token>
```

## 4.2 Register

Controller: `AuthController.register`

```http
POST /api/auth/register
Content-Type: application/json
```

Request body:

```json
{
  "username": "student1",
  "password": "secret",
  "confirmPassword": "secret",
  "role": "STUDENT"
}
```

Response success:

- HTTP `201 Created`
- Body plain text:

```text
Registration successful
```

Logic:

- Nếu `password` khác `confirmPassword`, trả `400 Bad Request` body plain text `Passwords do not match`.
- Nếu username đã tồn tại, `AuthService.register` throw `RuntimeException("Username already exists")`; chưa có handler riêng nên có thể thành Spring Boot default error response / 500.
- Password được BCrypt encode vào `passwordHash`.

DTO validation: Chưa implement cho `RegisterRequest`; không có `@Valid` trong controller.

## 4.3 Login

Controller: `AuthController.login`

```http
POST /api/auth/login
Content-Type: application/json
```

Request body:

```json
{
  "username": "student1",
  "password": "secret"
}
```

Response success:

- HTTP `200 OK`
- Body plain text, không phải JSON:

```text
Login successful <jwt-token>
```

Frontend note: cần parse token từ chuỗi sau prefix `Login successful `. Đây là contract hiện tại từ implementation.

JWT:

- Subject: username.
- Claim `role`: authority string như `ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN`.
- Expiration: 30 phút, hardcoded trong `JWTService.generateAccessToken`.
- Secret: `jwt.secret.key`, expected Base64 string.

## 4.4 Current user

Backend lấy current user bằng:

- `Authentication.getName()` trả username.
- `UserRepository.findByUsername(username)` trong `ClassroomService` và `ClassroomSecurity`.

## 4.5 Roles

`Role` enum:

```text
STUDENT
TEACHER
ADMIN
```

Spring Security authorities:

- `MyUserDetailsService` dùng `.roles(user.getRole().name())`, tạo authority `ROLE_<role>`.
- `JwtFilter` đọc role claim từ JWT và đưa thẳng vào `SimpleGrantedAuthority`.

## 4.6 Endpoint authorization summary

- Public: `/api/auth/**`.
- Required JWT: mọi endpoint khác.
- `@PreAuthorize` hiện có ở `ClassroomController`, `StreamController`, `TestController`.
- Webhook endpoint `/api/webhooks/livekit` không public theo `SecurityConfig`, dù đây là server-to-server LiveKit webhook. Đây là issue quan trọng cho tích hợp LiveKit.

# 5. Domain Model and Database Model

## 5.1 User

Class: `User`  
Table: `users`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `username` | `username` | `String` | not null, unique | Login username |
| `passwordHash` | `password_hash` | `String` | not null | Không trả ra API |
| `role` | `role` | `Role` | not null | `@Enumerated(EnumType.STRING)` |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |

Relationship: không khai báo outbound relationship.

## 5.2 RefreshToken

Class: `RefreshToken`  
Table: `refresh_tokens`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `Long` | PK | `@GeneratedValue(IDENTITY)` |
| `token` | `token` | `String` | not null, unique | Random UUID string |
| `expiryDate` | inferred `expiry_date` | `Instant` | not null | 7 ngày trong service |
| `user` | `user_id` | `User` | not null | `@ManyToOne` |
| `revoked` | `revoked` | `boolean` | not null | Revocation flag |

API refresh token: Chưa implement endpoint. `RefreshTokenService` tồn tại nhưng `AuthController` chưa expose refresh/login with refresh token.

## 5.3 Classroom

Class: `Classroom`  
Table: `classrooms`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `name` | `name` | `String` | not null | Classroom name |
| `description` | `description` | `String` | nullable | `text` |
| `teacher` | `teacher_id` | `User` | not null | `@ManyToOne(fetch = LAZY)` |
| `classCode` | `class_code` | `String` | not null, unique | Join code |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |
| `updatedAt` | `updated_at` | `LocalDateTime` | nullable | `@UpdateTimestamp` |

Ý nghĩa: lớp học cố định, không phải LiveKit room.

## 5.4 ClassroomMember

Class: `ClassroomMember`  
Table: `classroom_members`

Unique constraint: `classroom_id`, `user_id`.

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `classroom` | `classroom_id` | `Classroom` | not null | `@ManyToOne(fetch = LAZY)` |
| `user` | `user_id` | `User` | not null | `@ManyToOne(fetch = LAZY)` |
| `role` | `role` | `ClassroomMemberRole` | not null | enum string |
| `status` | `status` | `ClassroomMemberStatus` | not null | default Java value `ACTIVE` |
| `joinedAt` | `joined_at` | `LocalDateTime` | not null | `@CreationTimestamp`; service cũng set `LocalDateTime.now()` |

Enums:

- `ClassroomMemberRole`: `STUDENT`, `TEACHER`, `ASSISTANT`
- `ClassroomMemberStatus`: `ACTIVE`, `PENDING`, `REMOVED`, `BLOCKED`

## 5.5 LiveSession

Class: `LiveSession`  
Table: `live_sessions`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `classroom` | `classroom_id` | `Classroom` | not null | `@ManyToOne(fetch = LAZY)` |
| `host` | `host_id` | `User` | not null | `@ManyToOne(fetch = LAZY)` |
| `title` | `title` | `String` | not null | Session title |
| `description` | `description` | `String` | nullable | `text` |
| `status` | `status` | `LiveSessionStatus` | not null | default Java value `SCHEDULED` |
| `scheduledStartTime` | `scheduled_start_time` | `LocalDateTime` | nullable |  |
| `scheduledEndTime` | `scheduled_end_time` | `LocalDateTime` | nullable |  |
| `actualStartTime` | `actual_start_time` | `LocalDateTime` | nullable |  |
| `actualEndTime` | `actual_end_time` | `LocalDateTime` | nullable |  |
| `livekitRoomName` | `livekit_room_name` | `String` | unique | LiveKit room name |
| `livekitRoomSid` | `livekit_room_sid` | `String` | nullable | LiveKit room SID |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |
| `updatedAt` | `updated_at` | `LocalDateTime` | nullable | `@UpdateTimestamp` |

Enum `LiveSessionStatus`: `SCHEDULED`, `LIVE`, `ENDED`, `CANCELLED`.

API/service/repository: Chưa implement.

## 5.6 SessionParticipant

Class: `SessionParticipant`  
Table: `session_participants`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `session` | `session_id` | `LiveSession` | not null | `@ManyToOne(fetch = LAZY)` |
| `user` | `user_id` | `User` | not null | `@ManyToOne(fetch = LAZY)` |
| `livekitParticipantIdentity` | `livekit_participant_identity` | `String` | nullable | LiveKit identity |
| `livekitParticipantSid` | `livekit_participant_sid` | `String` | nullable | LiveKit SID |
| `joinedAt` | `joined_at` | `LocalDateTime` | nullable |  |
| `leftAt` | `left_at` | `LocalDateTime` | nullable |  |
| `durationSeconds` | `duration_seconds` | `Long` | nullable | default Java value `0L` |
| `role` | `role` | `SessionParticipantRole` | not null | enum string |
| `status` | `status` | `SessionParticipantStatus` | not null | default Java value `JOINED` |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |
| `updatedAt` | `updated_at` | `LocalDateTime` | nullable | `@UpdateTimestamp` |

Enums:

- `SessionParticipantRole`: `STUDENT`, `TEACHER`, `ASSISTANT`
- `SessionParticipantStatus`: `JOINED`, `LEFT`, `KICKED`, `DISCONNECTED`

API/service/repository: Chưa implement.

## 5.7 AttendanceRecord

Class: `AttendanceRecord`  
Table: `attendance_records`

Unique constraint: `session_id`, `user_id`.

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `session` | `session_id` | `LiveSession` | not null | `@ManyToOne(fetch = LAZY)` |
| `user` | `user_id` | `User` | not null | `@ManyToOne(fetch = LAZY)` |
| `status` | `status` | `AttendanceStatus` | not null | enum string |
| `joinedAt` | `joined_at` | `LocalDateTime` | nullable |  |
| `leftAt` | `left_at` | `LocalDateTime` | nullable |  |
| `totalDurationSeconds` | `total_duration_seconds` | `Long` | nullable | default Java value `0L` |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |
| `updatedAt` | `updated_at` | `LocalDateTime` | nullable | `@UpdateTimestamp` |

Enum `AttendanceStatus`: `PRESENT`, `ABSENT`, `LATE`, `LEFT_EARLY`, `EXCUSED`.

API/service/repository: Chưa implement.

## 5.8 LiveKitWebhookEvent

Class: `LiveKitWebhookEvent`  
Table: `livekit_webhook_events`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `eventType` | `event_type` | `String` | not null | Event name |
| `livekitRoomName` | `livekit_room_name` | `String` | nullable |  |
| `livekitRoomSid` | `livekit_room_sid` | `String` | nullable |  |
| `participantIdentity` | `participant_identity` | `String` | nullable |  |
| `participantSid` | `participant_sid` | `String` | nullable |  |
| `rawPayload` | `raw_payload` | `String` | not null | `columnDefinition = "jsonb"` |
| `processed` | `processed` | `Boolean` | not null | default Java value `false` |
| `processedAt` | `processed_at` | `LocalDateTime` | nullable |  |
| `errorMessage` | `error_message` | `String` | nullable | `text` |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |

Repository/service logic for persisting webhook events: Chưa implement.

## 5.9 Recording

Class: `Recording`  
Table: `recordings`

| Field | Column | Java type | Nullable/unique | Notes |
|---|---|---|---|---|
| `id` | `id` | `String` | not null, PK | `@UuidGenerator` |
| `session` | `session_id` | `LiveSession` | not null | `@ManyToOne(fetch = LAZY)` |
| `livekitEgressId` | `livekit_egress_id` | `String` | nullable | LiveKit egress id |
| `fileUrl` | `file_url` | `String` | nullable | `text` |
| `fileName` | `file_name` | `String` | nullable |  |
| `status` | `status` | `RecordingStatus` | not null | default Java value `PROCESSING` |
| `startedAt` | `started_at` | `LocalDateTime` | nullable |  |
| `endedAt` | `ended_at` | `LocalDateTime` | nullable |  |
| `createdAt` | `created_at` | `LocalDateTime` | not null | `@CreationTimestamp` |
| `updatedAt` | `updated_at` | `LocalDateTime` | nullable | `@UpdateTimestamp` |

Enum `RecordingStatus`: `PROCESSING`, `COMPLETED`, `FAILED`, `DELETED`.

API/service/repository: Chưa implement.

## 5.10 Flyway migration status

Migrations hiện có:

- `V1__initial.sql`: tạo bảng `users`.
- `V2__create_refresh_tokens_table.sql`: tạo bảng `refresh_tokens`.

Entity có nhưng chưa có migration:

- `classrooms`
- `classroom_members`
- `live_sessions`
- `session_participants`
- `attendance_records`
- `livekit_webhook_events`
- `recordings`

Vì `spring.jpa.hibernate.ddl-auto=validate`, backend có thể fail lúc startup nếu database chưa có các bảng trên.

Potential mismatch:

- `users.id` migration là `VARCHAR(50)`, entity dùng UUID string generated by Hibernate. UUID string thường dài 36 nên vẫn nằm trong 50.
- `RefreshToken.expiryDate` không có `@Column(name = "expiry_date")`, nhưng Spring Boot physical naming thường map `expiryDate` thành `expiry_date`. Không xác định từ source hiện tại nếu naming strategy bị override bên ngoài.
- `RefreshToken` dùng Lombok `@Data`, khác style entity còn lại; có thể gây issue lazy relationship nếu mở rộng sau này.

# 6. API Reference

## 6.1 Endpoint summary

| Method | Path | Auth | Roles | Controller | Status |
|---|---|---|---|---|---|
| `POST` | `/api/auth/register` | Public | N/A | `AuthController.register` | Implemented |
| `POST` | `/api/auth/login` | Public | N/A | `AuthController.login` | Implemented |
| `GET` | `/` | Required JWT | `TEACHER`, `STUDENT`, `ADMIN` | `TestController.getMethodName` | Implemented test endpoint |
| `POST` | `/api/classrooms` | Required JWT | `ADMIN` | `ClassroomController.createClassroom` | Implemented |
| `GET` | `/api/classrooms` | Required JWT | `ADMIN`, `TEACHER`, `STUDENT` | `ClassroomController.getClassrooms` | Implemented |
| `GET` | `/api/classrooms/{id}` | Required JWT | `ADMIN`, `TEACHER`, active member | `ClassroomController.getClassroomById` | Implemented |
| `PUT` | `/api/classrooms/{id}` | Required JWT | `ADMIN` | `ClassroomController.updateClassroom` | Implemented |
| `DELETE` | `/api/classrooms/{id}` | Required JWT | `ADMIN` | `ClassroomController.deleteClassroom` | Implemented |
| `POST` | `/api/classrooms/join` | Required JWT | `STUDENT` | `ClassroomController.joinClassroom` | Implemented |
| `GET` | `/api/classrooms/{id}/members` | Required JWT | `ADMIN`, `TEACHER`, active member | `ClassroomController.getClassroomMembers` | Implemented |
| `POST` | `/api/stream/token` | Required JWT | `TEACHER`, `STUDENT` | `StreamController.generateToken` | Implemented một phần |
| `POST` | `/api/webhooks/livekit` | Required JWT by global security, plus LiveKit auth header in method | Không rõ | `WebhooksController.handleWebhook` | Implemented một phần, likely security issue |

Không thấy User APIs, LiveSession APIs, admin-specific APIs khác, WebSocket API.

## POST /api/auth/register

Purpose: đăng ký user mới.

Controller/Method: `AuthController.register`.

Auth:

- Public, do `/api/auth/**` được permitAll.
- Không có `@PreAuthorize`.

Request Headers:

```http
Content-Type: application/json
```

Request Body:

```json
{
  "username": "teacher1",
  "password": "secret",
  "confirmPassword": "secret",
  "role": "TEACHER"
}
```

Success Response:

```text
Registration successful
```

HTTP status: `201 Created`.

Possible Errors:

- `400`: password và confirmPassword khác nhau, plain text `Passwords do not match`.
- `500` hoặc Spring Boot default error response: username đã tồn tại do `RuntimeException`.

Frontend Notes:

- Dùng cho Register screen.
- Sau register hiện backend không tự login; FE nên điều hướng về Login.
- Role truyền trực tiếp từ UI. Nếu không muốn user tự chọn admin, backend hiện chưa chặn điều này.

## POST /api/auth/login

Purpose: đăng nhập và nhận JWT access token.

Controller/Method: `AuthController.login`.

Auth:

- Public.

Request Headers:

```http
Content-Type: application/json
```

Request Body:

```json
{
  "username": "teacher1",
  "password": "secret"
}
```

Success Response:

```text
Login successful eyJhbGciOi...
```

Possible Errors:

- Auth failure từ Spring Security authentication; response format không được custom trong source hiện tại.
- `500` hoặc default error nếu exception không được handler.

Frontend Notes:

- FE phải tách token khỏi plain text response.
- Lưu token ở Electron secure storage nếu có; tối thiểu lưu trong memory/auth store. Nếu dùng localStorage thì cân nhắc risk.
- Gửi mọi request protected với `Authorization: Bearer <token>`.

## GET /

Purpose: test endpoint trả `"hello"`.

Controller/Method: `TestController.getMethodName`.

Auth:

- Required JWT.
- `@PreAuthorize("hasAnyRole('TEACHER', 'STUDENT', 'ADMIN')")`

Success Response:

```text
hello
```

Frontend Notes:

- Không cần dùng cho app thật.

## POST /api/classrooms

Purpose: Admin tạo classroom cố định.

Controller/Method: `ClassroomController.createClassroom`.

Auth:

- Required JWT.
- Required role: `ADMIN`.
- `@PreAuthorize("hasRole('ADMIN')")`

Request Headers:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

Request Body:

```json
{
  "name": "Java Backend K17",
  "description": "Lớp học Spring Boot",
  "teacherId": "id-cua-giao-vien",
  "classCode": "JAVA-K17"
}
```

Success Response:

Suy luận từ implementation `ClassroomResponse`:

```json
{
  "id": "classroom-id",
  "name": "Java Backend K17",
  "description": "Lớp học Spring Boot",
  "classCode": "JAVA-K17",
  "teacherId": "teacher-id",
  "teacherUsername": "teacher1",
  "createdAt": "2026-05-15T10:00:00",
  "updatedAt": "2026-05-15T10:00:00",
  "memberCount": 1
}
```

Possible Errors:

- `400`: validation `@NotBlank`, duplicate class code, invalid teacher role.
- `403`: not admin.
- `404`: teacher not found.

Frontend Notes:

- Admin Classroom Management screen.
- FE cần chọn teacher bằng `teacherId`; chưa có endpoint list users/teachers, nên phần chọn teacher chưa được backend hỗ trợ.

## GET /api/classrooms

Purpose: lấy danh sách classroom theo role.

Controller/Method: `ClassroomController.getClassrooms`.

Auth:

- Required JWT.
- `@PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")`

Role logic:

- `ADMIN`, `TEACHER`: trả tất cả classrooms.
- `STUDENT`: chỉ trả classroom mà user là `ClassroomMember` status `ACTIVE`.

Success Response:

Suy luận từ implementation:

```json
[
  {
    "id": "classroom-id",
    "name": "Java Backend K17",
    "description": "Lớp học Spring Boot",
    "classCode": "JAVA-K17",
    "teacherId": "teacher-id",
    "teacherUsername": "teacher1",
    "createdAt": "2026-05-15T10:00:00",
    "updatedAt": "2026-05-15T10:00:00",
    "memberCount": 12
  }
]
```

Possible Errors:

- `403`: no allowed role or access denied.
- `404`: current user not found.

Frontend Notes:

- Dashboard / Classroom list screen.
- FE không nhận `passwordHash`.

## GET /api/classrooms/{id}

Purpose: xem chi tiết classroom.

Controller/Method: `ClassroomController.getClassroomById`.

Auth:

- Required JWT.
- `@PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#id, authentication)")`

Path Params:

- `id`: classroom id.

Success Response:

Suy luận từ implementation, same `ClassroomResponse`:

```json
{
  "id": "classroom-id",
  "name": "Java Backend K17",
  "description": "Lớp học Spring Boot",
  "classCode": "JAVA-K17",
  "teacherId": "teacher-id",
  "teacherUsername": "teacher1",
  "createdAt": "2026-05-15T10:00:00",
  "updatedAt": "2026-05-15T10:00:00",
  "memberCount": 12
}
```

Possible Errors:

- `403`: student không phải active member.
- `404`: classroom not found.

Frontend Notes:

- Classroom Detail screen.
- Student route guard nên chỉ mở từ classroom list đã trả về hoặc handle `403`.

## PUT /api/classrooms/{id}

Purpose: Admin cập nhật classroom.

Controller/Method: `ClassroomController.updateClassroom`.

Auth:

- Required JWT.
- Required role: `ADMIN`.
- `@PreAuthorize("hasRole('ADMIN')")`

Request Body:

```json
{
  "name": "Tên mới",
  "description": "Mô tả mới",
  "teacherId": "teacher-id-moi",
  "classCode": "CODE-MOI"
}
```

Fields đều optional ở DTO. Service chỉ update `name`, `teacherId`, `classCode` nếu non-blank; `description` update được thành chuỗi rỗng/null nếu field present là null? Java record không phân biệt missing/null.

Success Response:

Suy luận từ implementation, same `ClassroomResponse`.

Possible Errors:

- `400`: class code trùng, teacher role không hợp lệ.
- `403`: not admin.
- `404`: classroom/teacher not found.

Frontend Notes:

- Admin edit classroom form.
- Nếu đổi teacher, backend tự đảm bảo teacher mới có `ClassroomMember` role `TEACHER`, status `ACTIVE`.

## DELETE /api/classrooms/{id}

Purpose: Admin xóa classroom.

Controller/Method: `ClassroomController.deleteClassroom`.

Auth:

- Required JWT.
- Required role: `ADMIN`.
- `@PreAuthorize("hasRole('ADMIN')")`

Success Response:

- HTTP `204 No Content`.

Logic:

- Xóa `classroom_members` theo classroom id trước.
- Sau đó xóa classroom.

Possible Errors:

- `403`: not admin.
- `404`: classroom not found.
- Runtime DB error nếu có live sessions/foreign keys khác trỏ classroom; chưa xử lý trong source.

Frontend Notes:

- Admin classroom management.
- Sau success, remove item khỏi list.

## POST /api/classrooms/join

Purpose: Student join classroom bằng class code.

Controller/Method: `ClassroomController.joinClassroom`.

Auth:

- Required JWT.
- Required role: `STUDENT`.
- `@PreAuthorize("hasRole('STUDENT')")`

Request Body:

```json
{
  "classCode": "JAVA-K17"
}
```

Success Response:

Suy luận từ implementation `ClassroomMemberResponse`:

```json
{
  "id": "member-id",
  "userId": "student-id",
  "username": "student1",
  "classroomId": "classroom-id",
  "classroomName": "Java Backend K17",
  "role": "STUDENT",
  "status": "ACTIVE",
  "joinedAt": "2026-05-15T10:00:00"
}
```

Logic:

- Nếu chưa từng join: tạo `ClassroomMember` role `STUDENT`, status `ACTIVE`.
- Nếu đã `ACTIVE`: trả member hiện tại.
- Nếu `PENDING` hoặc `REMOVED`: set lại `ACTIVE`, role `STUDENT`, update `joinedAt`.
- Nếu `BLOCKED`: throw `ForbiddenException`.

Possible Errors:

- `400`: validation `classCode` blank.
- `403`: not student hoặc blocked.
- `404`: classroom not found hoặc current user not found.

Frontend Notes:

- Join Classroom screen/modal.
- Sau success, thêm classroom vào student list hoặc refetch `GET /api/classrooms`.

## GET /api/classrooms/{id}/members

Purpose: lấy danh sách thành viên classroom.

Controller/Method: `ClassroomController.getClassroomMembers`.

Auth:

- Required JWT.
- `@PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#id, authentication)")`

Success Response:

Suy luận từ implementation:

```json
[
  {
    "id": "member-id",
    "userId": "user-id",
    "username": "student1",
    "classroomId": "classroom-id",
    "classroomName": "Java Backend K17",
    "role": "STUDENT",
    "status": "ACTIVE",
    "joinedAt": "2026-05-15T10:00:00"
  }
]
```

Possible Errors:

- `403`: student không thuộc lớp.
- `404`: classroom/current user not found.

Frontend Notes:

- Classroom Detail screen, Members tab.
- Response hiện trả cả member không `ACTIVE` nếu tồn tại, vì repository gọi `findByClassroomId` không filter status.

## POST /api/stream/token

Purpose: tạo LiveKit access token để frontend connect vào room.

Controller/Method: `StreamController.generateToken`.

Auth:

- Required JWT.
- Required roles: `TEACHER`, `STUDENT`.
- `@PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")`
- Controller có `@CrossOrigin(origins = "*")`.

Request Headers:

```http
Authorization: Bearer <token>
Content-Type: application/json
```

Request Body:

```json
{
  "roomName": "some-room",
  "identity": "student1"
}
```

Success Response:

```json
{
  "token": "livekit-jwt-token"
}
```

Logic:

- Nếu requester authority là `ROLE_TEACHER`, LiveKit grants: `RoomJoin(true)`, `RoomName(roomName)`, `RoomAdmin(true)`.
- Nếu requester là student, grants: `RoomJoin(true)`, `RoomName(roomName)`.
- `identity` lấy trực tiếp từ request body, không đối chiếu với authenticated username.
- Backend không trả LiveKit URL.
- Backend không tạo LiveKit room bằng API; chỉ tạo token.

Possible Errors:

- `400`: thiếu `roomName` hoặc `identity`, response `{"error":"Missing roomName or identity"}`.
- `403`: role không phải teacher/student.

Frontend Notes:

- Live room screen gọi endpoint này để lấy token.
- FE cần biết LiveKit URL từ config riêng vì backend không expose.
- Nên truyền `identity` là username/current user identity, nhưng backend chưa enforce.

## POST /api/webhooks/livekit

Purpose: nhận LiveKit webhook server-to-server.

Controller/Method: `WebhooksController.handleWebhook`.

Auth:

- Theo method signature, yêu cầu header `Authorization`.
- `WebhooksService` dùng `WebhookReceiver(LIVEKIT_API_KEY, LIVEKIT_API_SECRET).receive(payload, authHeader)`.
- Tuy nhiên `SecurityConfig` yêu cầu app JWT cho mọi endpoint ngoài `/api/auth/**`, nên endpoint này hiện không public. LiveKit webhook có thể bị chặn trước khi vào controller.

Request Body:

- Raw string payload từ LiveKit.

Success Response:

```text
Webhook received
```

Status: `200 OK`.

Logic:

- Verify/parse webhook bằng LiveKit SDK.
- Dispatch theo `event.getEvent()`.
- Nếu không có handler, log `No handler found for event type: ...`.
- Nếu exception trong service, chỉ log stderr, controller vẫn trả `200 OK`.

Frontend Notes:

- Frontend không gọi endpoint này.
- Đây là server-to-server endpoint cho LiveKit.

# 7. Frontend User Flows

## 7.1 Register

- Endpoint: `POST /api/auth/register`
- Request: `username`, `password`, `confirmPassword`, `role`
- Response: plain text `Registration successful`
- Frontend behavior: validate password confirm ở UI, submit, on 201 route to login.
- Trạng thái: Implemented một phần. Thiếu DTO validation và role protection; user có thể gửi `ADMIN`.

## 7.2 Login

- Endpoint: `POST /api/auth/login`
- Request: `username`, `password`
- Response: plain text `Login successful <token>`
- FE lưu JWT: khuyến nghị Electron secure storage hoặc auth store memory; nếu cần persistence thì local encrypted storage.
- FE gửi JWT: `Authorization: Bearer <token>`
- Trạng thái: Implemented một phần. Response chưa phải JSON và chưa trả user profile/role tách riêng.

## 7.3 Logout

- Backend logout endpoint: Chưa implement.
- FE behavior: xóa token local/auth store và điều hướng về Login.
- Trạng thái: Chưa implement backend.

## 7.4 Admin creates classroom

- Endpoint: `POST /api/classrooms`
- Required role: `ADMIN`
- Request: `name`, `description`, `teacherId`, `classCode`
- Response: `ClassroomResponse`
- FE behavior: create form, submit, append/refetch list.
- Trạng thái: Implemented.
- Gap: chưa có endpoint list teachers/users để chọn `teacherId`.

## 7.5 Admin manages classrooms

- List: `GET /api/classrooms`
- Create: `POST /api/classrooms`
- Update: `PUT /api/classrooms/{id}`
- Delete: `DELETE /api/classrooms/{id}`
- Role: `ADMIN`
- Trạng thái: Implemented.

## 7.6 Teacher views classrooms

- Endpoint: `GET /api/classrooms`
- Role: `TEACHER`
- Response: list `ClassroomResponse`
- Rule: teacher xem tất cả classroom, không bị giới hạn teacher owner.
- Trạng thái: Implemented.

## 7.7 Student views joined classrooms

- Endpoint: `GET /api/classrooms`
- Role: `STUDENT`
- Rule: chỉ classroom có `ClassroomMember.status = ACTIVE` với current user.
- Trạng thái: Implemented.

## 7.8 Student joins classroom by class code

- Endpoint: `POST /api/classrooms/join`
- Request: `{ "classCode": "JAVA-K17" }`
- Response: `ClassroomMemberResponse`
- Trạng thái: Implemented.

## 7.9 Teacher creates live session

- Endpoint: Chưa implement.
- Request/response: Chưa implement.
- Trạng thái: Chưa implement.

## 7.10 Teacher starts live session

- Endpoint: Chưa implement.
- Có gọi LiveKit createRoom không: Không. Không thấy code gọi createRoom.
- Có trả token không: Chỉ có `POST /api/stream/token`, không gắn với `LiveSession`.
- Trạng thái: Chưa implement / LiveKit token độc lập đã có.

## 7.11 Student joins live session

- Endpoint dedicated join session: Chưa implement.
- Backend kiểm tra quyền học sinh thuộc classroom/session: Chưa implement.
- Backend trả LiveKit token: Có `POST /api/stream/token`, nhưng endpoint không kiểm tra membership/session và nhận `roomName`, `identity` từ FE.
- FE dùng token connect LiveKit: có thể dùng token trả về từ `/api/stream/token`, nhưng cần tự biết LiveKit URL.
- Trạng thái: Implemented một phần.

## 7.12 Live room screen

Backend hỗ trợ hiện tại:

- `POST /api/stream/token` trả LiveKit token.
- Backend không trả `livekitUrl`.
- Backend không tạo/check `LiveSession`.
- Backend không kiểm tra classroom membership khi xin token.

Frontend nên làm:

- Dùng LiveKit client SDK trong Electron.
- Connect bằng `livekitUrl`, `roomName`, `token`.
- `livekitUrl`: lấy từ frontend env/config vì backend chưa expose.
- Xin quyền mic/camera.
- Publish audio/video tracks.
- Render remote participants.
- Mute/unmute camera/mic xử lý client-side.
- Handle disconnect/reconnect client-side.

Phần thiếu backend:

- create/start session API.
- join session API có kiểm tra quyền.
- API trả `{ livekitUrl, roomName, token }`.
- recording/attendance/session state.

## 7.13 LiveKit webhook flow

- Webhook là server-to-server; frontend không gọi webhook.
- Backend có `POST /api/webhooks/livekit`.
- Backend dùng LiveKit SDK `WebhookReceiver` để receive/verify payload.
- Backend dispatch theo event type.
- Event handler cụ thể: Chưa implement. Không có class implement `LiveKitEventHandler` trong source hiện tại.
- Các event chưa có handler: `room_started`, `room_finished`, `participant_joined`, `participant_left`, `track_published`, `track_unpublished`, `egress_started`, `egress_ended`.
- Backend chưa persist `LiveKitWebhookEvent`.
- Backend chưa cập nhật `LiveSession`, `SessionParticipant`, `AttendanceRecord`, `Recording`.

# 8. LiveKit Integration Details

- Service tạo token: `StreamService.generateToken(String roomName, String identity, boolean isTeacher)`.
- Controller token API: `POST /api/stream/token`.
- Token identity: `identity` từ request body, không lấy từ `Authentication`.
- Token name: set bằng `identity`.
- Room name: `roomName` từ request body.
- Teacher grant: `RoomJoin(true)`, `RoomName(roomName)`, `RoomAdmin(true)`.
- Student grant: `RoomJoin(true)`, `RoomName(roomName)`.
- Response field: `{ "token": "<livekit-token>" }`.
- LiveKit URL backend có trả về không: Không.
- LiveKit URL config trong backend: Chưa có `livekit.url`.
- Backend có gọi createRoom không: Không.
- Backend có deleteRoom không: Không.
- Backend có webhook endpoint không: Có, `POST /api/webhooks/livekit`.
- Backend có verify webhook Authorization/signature không: Có ý định bằng `WebhookReceiver.receive(payload, authHeader)`, nhưng endpoint có thể bị Spring Security JWT chặn trước.
- Backend có dispatcher không: Có `WebhooksDispatcher`.
- Backend có event handlers không: Chưa có implementation cụ thể.

Event handling status:

| Event | Status |
|---|---|
| `room_started` | Chưa implement |
| `room_finished` | Chưa implement |
| `participant_joined` | Chưa implement |
| `participant_left` | Chưa implement |
| `track_published` | Chưa implement |
| `track_unpublished` | Chưa implement |
| `egress_started` | Chưa implement |
| `egress_ended` | Chưa implement |

# 9. Error Response Format

Custom exceptions:

- `ResourceNotFoundException extends RuntimeException`
- `BadRequestException extends RuntimeException`
- `ForbiddenException extends RuntimeException`

Global handler: `GlobalExceptionHandler`.

Handled response format:

```json
{
  "status": 404,
  "message": "Classroom not found",
  "timestamp": "2026-05-15T10:00:00"
}
```

Handled statuses:

- `404`: `ResourceNotFoundException`
- `400`: `BadRequestException`
- `400`: `MethodArgumentNotValidException`
- `403`: `ForbiddenException`, `AccessDeniedException`

Not fully standardized:

- Auth login failures from Spring Security.
- Raw `RuntimeException` in `AuthService.register`, `AuthService.refreshAccessToken`, `RefreshTokenService.verifyExpiration`.
- JWT parsing exception in `JwtFilter` is not caught around `getUsernameFromToken`.

Frontend error handling:

- Prefer using HTTP status as primary source.
- If body has `status/message/timestamp`, show `message`.
- If body is plain text, show text.
- If default Spring Boot error, fallback to `error`/`message` fields or generic message.

# 10. CORS and Frontend Connection Notes

- Global CORS config: Chưa thấy trong source.
- `StreamController` only: `@CrossOrigin(origins = "*")`.
- Allowed origins globally: Không xác định từ source hiện tại.
- Allowed methods globally: Không xác định từ source hiện tại.
- Allowed headers globally: Không xác định từ source hiện tại.
- Allow credentials: Không xác định từ source hiện tại.
- CSRF: disabled globally in `SecurityConfig`, phù hợp REST/JWT.
- Frontend Electron base API URL: nếu không override server port, dùng `http://localhost:8080`.
- Note: Browser/Electron renderer using fetch may hit CORS for endpoints khác `/api/stream/token` nếu frontend origin khác backend. Electron main process requests thường không bị CORS như browser renderer.

# 11. Required Frontend Screens

| Screen | Roles | APIs | Data | Actions/buttons | Backend support |
|---|---|---|---|---|---|
| Login | Public | `POST /api/auth/login` | username/password | Login | Supported một phần; response plain text |
| Register | Public | `POST /api/auth/register` | username/password/confirmPassword/role | Register | Supported một phần; role self-select risk |
| Dashboard | `ADMIN`, `TEACHER`, `STUDENT` | `GET /api/classrooms` | classroom list | Open detail | Supported |
| Admin classroom management | `ADMIN` | classroom CRUD endpoints | classroom fields | Create/Edit/Delete | Supported |
| Teacher classroom list | `TEACHER` | `GET /api/classrooms` | all classrooms | Open detail | Supported |
| Student classroom list | `STUDENT` | `GET /api/classrooms` | joined active classrooms | Open detail, join by code | Supported |
| Classroom detail | `ADMIN`, `TEACHER`, active student | `GET /api/classrooms/{id}`, `GET /api/classrooms/{id}/members` | classroom + members | View members | Supported |
| Join classroom by code | `STUDENT` | `POST /api/classrooms/join` | classCode | Join | Supported |
| Live room screen | `TEACHER`, `STUDENT` | `POST /api/stream/token` | roomName, identity | Join LiveKit room, mic/cam controls | Partial |
| Live session list | N/A | Chưa implement | N/A | N/A | Not implemented |
| Profile | N/A | Chưa implement | N/A | N/A | Not implemented |
| Logout | Any authenticated | No backend API | local auth state | Clear token | Frontend-only |

Future suggestion:

- User/teacher picker screen for admin classroom create/update needs backend user listing API.
- Live session screens need backend live session APIs.

# 12. Recommended Frontend API Client Structure

Use a single HTTP client wrapper:

- `apiClient`
  - base URL: `http://localhost:8080` by default, configurable.
  - request interceptor: attach `Authorization: Bearer <token>` if token exists.
  - response handler: support JSON, plain text, and Spring default errors.

Backend-supported modules:

- `authApi`
  - `register({ username, password, confirmPassword, role })`
  - `login({ username, password })` returns parsed token from plain text
- `classroomApi`
  - `createClassroom`
  - `listClassrooms`
  - `getClassroom`
  - `updateClassroom`
  - `deleteClassroom`
  - `joinClassroom`
  - `listMembers`
- `livekitApi`
  - `createToken({ roomName, identity })`

Not backend-supported yet:

- `userApi.listTeachers`
- `liveSessionApi.create/list/start/join/end`
- `recordingApi`
- `attendanceApi`
- Frontend should not call `webhookApi`; webhook is LiveKit server-to-server.

Auth store:

- Store token.
- Decode JWT client-side only for UX role routing if needed, but treat backend as source of truth.
- Since login response does not return role as JSON, FE may decode JWT claim `role` (`ROLE_STUDENT`, etc.) or infer after calling protected APIs. Decoding JWT on frontend is for UI only, not security.

Route guards:

- Public: Login, Register.
- Authenticated: Dashboard, Classroom list/detail, Live room.
- Role guarded:
  - Admin management: `ROLE_ADMIN`.
  - Join classroom by code: `ROLE_STUDENT`.
  - Stream token screen: `ROLE_TEACHER` or `ROLE_STUDENT`.

# 13. Backend Issues Found

| File | Issue | Impact | Suggested fix |
|---|---|---|---|
| `AuthController.login` | Login response is plain text `Login successful <token>` | FE must brittle-parse token | Return JSON `{ "accessToken": "...", "tokenType": "Bearer" }` |
| `AuthController.register` / `RegisterRequest` | No `@Valid`, no validation annotations | Bad input may pass to service | Add validation and handler |
| `AuthService.register` | Allows client to choose any `Role`, including `ADMIN` | Security risk | Restrict public register to student or admin-only role creation |
| `AuthService.register` | Throws raw `RuntimeException` for duplicate username | Error response not standardized | Throw `BadRequestException` |
| `AuthService` / `MyUserDetailsService` | Uses `var` in two places | Style mismatch with recent requirement | Replace with explicit types |
| `SecurityConfig` | Only `/api/auth/**` is public; webhook not permitted | LiveKit webhook likely blocked | Permit `/api/webhooks/livekit` or add separate webhook security filter |
| `JwtFilter` | Calls `jwtService.getUsernameFromToken(token)` before try/catch validation | Malformed/non-app Bearer token can throw before controller | Wrap parsing in try/catch |
| `StreamController.generateToken` | `identity` from request body, not authenticated username | User can request token for another identity | Use `authentication.getName()` as identity or validate |
| `StreamController.generateToken` | No classroom/session authorization | Any teacher/student can join any `roomName` | Tie token generation to `LiveSession` and membership |
| `StreamController.generateToken` | Backend returns token only, no `livekitUrl` | FE needs separate config | Add response DTO with `livekitUrl`, `roomName`, `token` |
| `application.properties` | No `livekit.url` | FE cannot discover LiveKit URL | Add config and expose through token response or frontend env |
| `WebhooksService.handleIncomingEvent` | Catches exception and only logs, controller still returns 200 | Failed webhook processing is invisible to LiveKit retries | Return error status or persist failed event |
| `service/webhooks` | No `LiveKitEventHandler` implementations | No attendance/session updates | Implement handlers for room/participant/egress events |
| `LiveKitWebhookEvent` | Entity exists but no repository/service usage | Webhook logs not persisted | Add repository and save raw events |
| `src/main/resources/db/migration` | Migrations only for `users`, `refresh_tokens` | With `ddl-auto=validate`, app may fail startup | Add migrations for all new entities |
| `RefreshTokenService` | No controller endpoint uses refresh token | Refresh token feature dead code | Add refresh endpoint or remove until needed |
| `RefreshToken` | Uses Lombok `@Data` on entity with relationship | Potential lazy loading/toString/equals issues | Use `@Getter/@Setter` |
| CORS | No global CORS config | Electron renderer/browser may fail requests | Add `CorsConfigurationSource` and `http.cors(...)` |
| LiveSession entities | Entities exist, no repositories/controllers/services | FE cannot build session management | Implement live session backend contract |

# 14. Missing Backend Pieces

Missing or partial pieces confirmed from source:

- User listing/profile APIs: Chưa implement.
- Admin list teachers/users for classroom create: Chưa implement.
- Refresh token endpoint: Chưa implement.
- Logout endpoint: Chưa implement.
- LiveSession CRUD: Chưa implement.
- Teacher creates live session: Chưa implement.
- Teacher starts live session: Chưa implement.
- Join live session API with membership/session check: Chưa implement.
- LiveKit room creation/delete via backend: Chưa implement.
- LiveKit token response including `livekitUrl`: Chưa implement.
- Webhook handler implementations: Chưa implement.
- `participant_joined` handler: Chưa implement.
- `participant_left` handler: Chưa implement.
- Attendance logic: Chưa implement.
- Recording logic / egress integration: Chưa implement.
- Persist webhook raw payload: Chưa implement.
- Global CORS config: Chưa implement.
- Flyway migrations for classroom/live session related entities: Chưa implement.

Implemented:

- CRUD classroom.
- Join classroom by class code.
- List classroom members.
- Global error handler for classroom custom exceptions.
- LiveKit token generation endpoint, partial.
- Webhook receiver/dispatcher, partial.

# 15. Contract Summary for Codex 2

- Base API URL: `http://localhost:8080` unless backend runs with external `server.port`.
- Auth flow:
  - Register: `POST /api/auth/register`, plain text response.
  - Login: `POST /api/auth/login`, plain text `Login successful <token>`.
  - Store parsed JWT token.
  - Send protected requests with:

```http
Authorization: Bearer <token>
```

- Roles:
  - `STUDENT`
  - `TEACHER`
  - `ADMIN`
  - JWT claim contains `ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN`.

- Main implemented endpoints:
  - Auth register/login.
  - Classroom CRUD for admin.
  - Classroom list/detail by role rules.
  - Student join classroom by class code.
  - Classroom members list.
  - LiveKit token creation via `POST /api/stream/token`.

- Main not-yet-implemented endpoints:
  - User profile/list users/list teachers.
  - Refresh/logout.
  - LiveSession CRUD/start/join/end.
  - Attendance/recording APIs.
  - Backend endpoint exposing LiveKit URL.

- LiveKit join flow currently possible:
  1. FE gets app JWT by login.
  2. FE decides `roomName` itself or from future session data.
  3. FE calls `POST /api/stream/token` with `{ "roomName": "...", "identity": "..." }`.
  4. Backend returns `{ "token": "..." }`.
  5. FE connects LiveKit using frontend-configured `livekitUrl` + returned token.

- Important frontend constraints:
  - Handle both JSON and plain text responses.
  - Handle `403` for role/access restrictions.
  - Do not call webhook endpoint from frontend.
  - Classroom `teacherId` requires a user id, but backend has no user listing endpoint.
  - For LiveKit, backend does not enforce session/classroom membership yet.

- Known backend limitations:
  - CORS not globally configured.
  - Webhook endpoint likely blocked by JWT security.
  - DB migrations missing for most entities.
  - Login/register response contract should be improved before production.
  - Live session domain exists as entities only, no API.
