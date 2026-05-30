# LIVE_ROOM_FRONTEND_HANDOFF

Tài liệu này bàn giao contract backend hiện tại cho Codex 2 generate giao diện phòng học live bằng Electron + React + TypeScript + LiveKit. Nội dung dưới đây chỉ dựa trên source hiện tại; phần nào chưa thấy trong source sẽ ghi rõ "Chưa implement" hoặc "Không xác định từ source hiện tại".

# 1. Mục tiêu của live room frontend

Frontend live room cần làm:

- User đăng nhập bằng JWT của backend Spring Boot.
- Teacher/Admin có thể start live session.
- Student có thể join live session nếu là active member của classroom.
- Frontend nhận `livekitUrl` + `token` từ backend.
- Frontend dùng LiveKit client SDK để connect room.
- Frontend render local video/audio và remote participants.
- Frontend có nút mute/unmute mic, enable/disable camera, leave room.
- Frontend không tự generate LiveKit token.
- Frontend không biết `LIVEKIT_API_SECRET`.
- Frontend không gọi webhook LiveKit.

# 2. Backend live session overview

- `LiveSession` là entity biểu diễn một buổi học online thuộc một `Classroom`.
- `Classroom` là lớp học cố định, ví dụ `Java Backend K17`.
- `LiveSession` là từng buổi live trong classroom đó, ví dụ `Buổi 1 - Spring Boot Introduction`.
- Một `Classroom` có thể có nhiều `LiveSession` qua `LiveSession.classroom`.
- `LiveSessionStatus` gồm:
  - `SCHEDULED`
  - `LIVE`
  - `ENDED`
  - `CANCELLED`
- `livekitRoomName` là tên phòng kỹ thuật bên LiveKit, được lưu ở `live_sessions.livekit_room_name`.
- `livekitRoomName` khác với `Classroom.name`, `Classroom.classCode`, và `LiveSession.title`.
- Khi start session, nếu `livekitRoomName` chưa có, backend generate bằng method `generateRoomName`.
- Format roomName hiện tại:

```text
classroom-{classroomId}-session-{sessionId}
```

Ví dụ suy luận từ implementation:

```text
classroom-5b3...-session-a92...
```

- Token identity hiện đang dùng `currentUser.getId()`.
- Token display name hiện đang dùng `currentUser.getUsername()`.
- `livekitUrl` lấy từ config key `livekit.url`, giá trị từ env `${LIVEKIT_URL}`.
- Backend hiện chưa gọi LiveKit `CreateRoom`; start session chỉ set trạng thái DB, generate `roomName`, rồi generate token.

# 3. LiveSession domain model

## User

Entity `User`, table `users`.

Frontend cần biết:

- `id`: `String`
- `username`: `String`
- `role`: `Role`
- `passwordHash`: tồn tại trong entity nhưng không được expose ra frontend.

## Role

Enum:

```text
STUDENT
TEACHER
ADMIN
```

JWT authority dùng dạng `ROLE_STUDENT`, `ROLE_TEACHER`, `ROLE_ADMIN`.

## Classroom

Entity `Classroom`, table `classrooms`.

Field chính:

- `id`
- `name`
- `description`
- `teacher`
- `classCode`
- `createdAt`
- `updatedAt`

`Classroom` là lớp cố định, chứa nhiều live session.

## ClassroomMember

Entity `ClassroomMember`, table `classroom_members`.

Field chính:

- `id`
- `classroom`
- `user`
- `role`
- `status`
- `joinedAt`

Unique constraint: `classroom_id + user_id`.

## ClassroomMemberRole

```text
STUDENT
TEACHER
ASSISTANT
```

## ClassroomMemberStatus

```text
ACTIVE
PENDING
REMOVED
BLOCKED
```

Active membership check dùng `ClassroomMemberStatus.ACTIVE`.

## LiveSession

Entity `LiveSession`, table `live_sessions`.

Field frontend cần quan tâm, cũng khớp `LiveSessionResponse`:

| Field | Type | Meaning |
|---|---|---|
| `id` | `String` | Live session id |
| `classroomId` | `String` | Classroom chứa session |
| `classroomName` | `String` | Tên classroom |
| `hostId` | `String` | User id của host |
| `hostUsername` | `String` | Username của host |
| `title` | `String` | Tiêu đề buổi học |
| `description` | `String` | Mô tả |
| `status` | `LiveSessionStatus` | `SCHEDULED`, `LIVE`, `ENDED`, `CANCELLED` |
| `scheduledStartTime` | `LocalDateTime` JSON string | Thời gian dự kiến bắt đầu |
| `scheduledEndTime` | `LocalDateTime` JSON string | Thời gian dự kiến kết thúc |
| `actualStartTime` | `LocalDateTime` JSON string | Thời gian start thật, set khi start session |
| `actualEndTime` | `LocalDateTime` JSON string | Thời gian kết thúc thật, hiện chưa có API set |
| `livekitRoomName` | `String` | Room name kỹ thuật dùng cho LiveKit |
| `livekitRoomSid` | `String` | LiveKit room SID, hiện chưa được set bởi CRUD/start |
| `emptyTimeoutSeconds` | `Integer` | Default `300` |
| `departureTimeoutSeconds` | `Integer` | Default `20` |
| `maxParticipants` | `Integer` | Default `0` |
| `roomMetadata` | `String` | Metadata text, hiện CRUD không cho update |
| `createdAt` | `LocalDateTime` JSON string | Created timestamp |
| `updatedAt` | `LocalDateTime` JSON string | Updated timestamp |

# 4. LiveSession APIs

Tất cả endpoint dưới đây cần app JWT:

```http
Authorization: Bearer <appJwt>
```

Summary:

| Method | Path | Purpose | Auth | Roles | Response |
|---|---|---|---|---|---|
| `POST` | `/api/classrooms/{classroomId}/sessions` | Tạo live session scheduled | JWT | `ADMIN`, managing `TEACHER` | `LiveSessionResponse` |
| `GET` | `/api/classrooms/{classroomId}/sessions` | List sessions của classroom | JWT | `ADMIN`, `TEACHER`, active `STUDENT` | `List<LiveSessionResponse>` |
| `GET` | `/api/sessions/{sessionId}` | Chi tiết session | JWT | `ADMIN`, `TEACHER`, active `STUDENT` qua service | `LiveSessionResponse` |
| `PUT` | `/api/sessions/{sessionId}` | Update session | JWT | `ADMIN`, managing `TEACHER` | `LiveSessionResponse` |
| `DELETE` | `/api/sessions/{sessionId}` | Delete session | JWT | `ADMIN`, managing `TEACHER` | `204 No Content` |
| `POST` | `/api/sessions/{sessionId}/start` | Start room và nhận host token | JWT | `ADMIN`, managing `TEACHER` | `LiveSessionTokenResponse` |
| `POST` | `/api/sessions/{sessionId}/join` | Join room và nhận participant token | JWT | `STUDENT` theo controller | `LiveSessionTokenResponse` |

## POST /api/classrooms/{classroomId}/sessions

Mục đích:

- Tạo phiên live trống trong classroom.
- Chưa connect LiveKit.
- Chưa generate token.
- Status mới là `SCHEDULED`.

Controller/method:

- `LiveSessionController.createSession`

Auth:

```java
@PreAuthorize("hasRole('ADMIN') or @classroomSecurity.canManageClassroom(#classroomId, authentication)")
```

Roles:

- `ADMIN`: được tạo.
- `TEACHER`: được tạo nếu có quyền quản lý classroom.
- `STUDENT`: không được tạo.

Request body:

```json
{
  "title": "Buổi 1 - Spring Boot Introduction",
  "description": "Giới thiệu Spring Boot",
  "scheduledStartTime": "2026-05-16T19:00:00",
  "scheduledEndTime": "2026-05-16T21:00:00",
  "emptyTimeoutSeconds": 300,
  "departureTimeoutSeconds": 20,
  "maxParticipants": 0
}
```

Required:

- `title` dùng `@NotBlank`.

Service logic:

- Load current user từ `Authentication.getName()`.
- Load classroom theo `classroomId`.
- Check quyền bằng `ensureCanManageClassroom`.
- Validate `scheduledEndTime` phải sau `scheduledStartTime` nếu cả hai có.
- Host:
  - Nếu current user là `ADMIN`: `host = classroom.teacher`.
  - Nếu current user là `TEACHER`: `host = currentUser`.
- Defaults:
  - `emptyTimeoutSeconds = 300`
  - `departureTimeoutSeconds = 20`
  - `maxParticipants = 0`

Success response:

```json
{
  "id": "session-id",
  "classroomId": "classroom-id",
  "classroomName": "Java Backend K17",
  "hostId": "teacher-id",
  "hostUsername": "teacher1",
  "title": "Buổi 1 - Spring Boot Introduction",
  "description": "Giới thiệu Spring Boot",
  "status": "SCHEDULED",
  "scheduledStartTime": "2026-05-16T19:00:00",
  "scheduledEndTime": "2026-05-16T21:00:00",
  "actualStartTime": null,
  "actualEndTime": null,
  "livekitRoomName": null,
  "livekitRoomSid": null,
  "emptyTimeoutSeconds": 300,
  "departureTimeoutSeconds": 20,
  "maxParticipants": 0,
  "roomMetadata": null,
  "createdAt": "2026-05-16T10:00:00",
  "updatedAt": "2026-05-16T10:00:00"
}
```

Lỗi có thể xảy ra:

- `400`: title blank, invalid schedule, negative timeout/max participants.
- `403`: không có quyền manage classroom.
- `404`: classroom hoặc current user không tìm thấy.

Frontend nên gọi ở:

- Teacher/Admin classroom detail page hoặc session management page.

## GET /api/classrooms/{classroomId}/sessions

Mục đích:

- Lấy danh sách live sessions của classroom.

Controller/method:

- `LiveSessionController.getSessionsByClassroom`

Auth:

```java
@PreAuthorize("hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#classroomId, authentication)")
```

Service logic:

- `ADMIN`: xem được.
- `TEACHER`: xem được tất cả classroom sessions.
- `STUDENT`: chỉ xem nếu là active member của classroom.
- Query: `findByClassroomIdOrderByScheduledStartTimeDesc`.

Response body:

```json
[
  {
    "id": "session-id",
    "classroomId": "classroom-id",
    "classroomName": "Java Backend K17",
    "hostId": "teacher-id",
    "hostUsername": "teacher1",
    "title": "Buổi 1 - Spring Boot Introduction",
    "description": "Giới thiệu Spring Boot",
    "status": "LIVE",
    "scheduledStartTime": "2026-05-16T19:00:00",
    "scheduledEndTime": "2026-05-16T21:00:00",
    "actualStartTime": "2026-05-16T19:02:00",
    "actualEndTime": null,
    "livekitRoomName": "classroom-classroom-id-session-session-id",
    "livekitRoomSid": null,
    "emptyTimeoutSeconds": 300,
    "departureTimeoutSeconds": 20,
    "maxParticipants": 0,
    "roomMetadata": null,
    "createdAt": "2026-05-16T10:00:00",
    "updatedAt": "2026-05-16T19:02:00"
  }
]
```

Frontend notes:

- Student UI nên chỉ hiển thị nút `Join` nếu `status === "LIVE"`.
- Teacher/Admin UI có thể hiển thị nút `Start` nếu session chưa `LIVE`.

## GET /api/sessions/{sessionId}

Mục đích:

- Lấy chi tiết một live session.

Controller/method:

- `LiveSessionController.getSessionById`

Auth:

```java
@PreAuthorize("hasAnyRole('ADMIN','TEACHER','STUDENT')")
```

Service permission:

- `ADMIN`: xem được.
- `TEACHER`: xem được.
- `STUDENT`: chỉ xem nếu active member của classroom chứa session.

Response:

- `LiveSessionResponse`, giống mẫu ở trên.

Lỗi:

- `403`: không có quyền xem.
- `404`: session/current user không tìm thấy.

## PUT /api/sessions/{sessionId}

Mục đích:

- Cập nhật metadata/schedule/status của live session.

Controller/method:

- `LiveSessionController.updateSession`

Auth:

```java
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
```

Service permission:

- `ADMIN`: update được.
- `TEACHER`: update được nếu quản lý classroom chứa session.
- `STUDENT`: bị chặn ở controller.

Request body:

```json
{
  "title": "Buổi 1 - Spring Boot Introduction Updated",
  "description": "Mô tả mới",
  "status": "SCHEDULED",
  "scheduledStartTime": "2026-05-16T19:30:00",
  "scheduledEndTime": "2026-05-16T21:30:00",
  "emptyTimeoutSeconds": 300,
  "departureTimeoutSeconds": 20,
  "maxParticipants": 0
}
```

Không update qua endpoint này:

- `livekitRoomName`
- `livekitRoomSid`
- `actualStartTime`
- `actualEndTime`
- `roomMetadata`

Rule với session đang `LIVE` hoặc `ENDED`:

- Backend không cho update title/schedule/status/room settings.
- Chỉ cho update `description` vì `hasRestrictedUpdate` không tính `description`.

Lỗi:

- `400`: invalid schedule, negative values, update restricted fields khi session `LIVE`/`ENDED`.
- `403`: không có quyền.
- `404`: session/current user không tìm thấy.

## DELETE /api/sessions/{sessionId}

Mục đích:

- Xóa live session.

Controller/method:

- `LiveSessionController.deleteSession`

Auth:

```java
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
```

Service permission:

- `ADMIN`: xóa được.
- `TEACHER`: xóa được nếu quản lý classroom chứa session.
- `STUDENT`: bị chặn ở controller.

Rule:

- Không cho xóa nếu `status == LIVE`.
- `ENDED`, `CANCELLED`, `SCHEDULED`: source hiện tại không chặn.

Success:

- `204 No Content`

## POST /api/sessions/{sessionId}/start

Mục đích:

- Teacher/Admin bắt đầu buổi live.
- Backend update `LiveSession.status` thành `LIVE`.
- Backend tạo `livekitRoomName` nếu chưa có.
- Backend set `actualStartTime` nếu đang null.
- Backend generate LiveKit token dạng host/admin.
- Backend trả `livekitUrl` + `token` cho frontend.

Controller/method:

- `LiveSessionController.startSession`

Auth:

```java
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
```

Service permission:

- Sau controller, `LiveSessionService.startSession` gọi `ensureCanManageClassroom`.
- `ADMIN`: được start.
- `TEACHER`: được start nếu quản lý classroom chứa session.

Request:

- Không có request body.

Headers:

```http
Authorization: Bearer <appJwt>
```

Service flow:

1. Load current user.
2. Load session.
3. Check manage permission.
4. Nếu `session.status == LIVE`, throw `BadRequestException("Session is already LIVE")`.
5. Nếu `livekitRoomName` null/blank, generate room name:

```text
classroom-{classroomId}-session-{sessionId}
```

6. Set `status = LIVE`.
7. Nếu `actualStartTime == null`, set `LocalDateTime.now()`.
8. Save session.
9. Generate LiveKit token:
   - `roomName = savedSession.getLivekitRoomName()`
   - `identity = currentUser.getId()`
   - `displayName = currentUser.getUsername()`
   - `isRoomAdmin = true`
10. Return `LiveSessionTokenResponse`.

Response DTO:

```json
{
  "sessionId": "session-id",
  "classroomId": "classroom-id",
  "roomName": "classroom-classroom-id-session-session-id",
  "livekitUrl": "ws://localhost:7880",
  "token": "livekit-access-token",
  "identity": "current-user-id",
  "username": "teacher1",
  "status": "LIVE"
}
```

`role` hiện chưa có trong response.

Important:

- Backend hiện không gọi LiveKit `CreateRoom`.
- Backend chỉ tạo token; LiveKit server sẽ tạo room động khi user connect nếu cấu hình LiveKit cho phép.
- Backend không chặn start session có status `ENDED` hoặc `CANCELLED`; chỉ chặn `LIVE`.

Frontend:

- Gọi từ Session Detail page hoặc Classroom Detail session list khi Teacher/Admin bấm `Start`.
- Sau response, chuyển vào `LiveRoomPage` với `livekitUrl`, `token`, `roomName`, `sessionId`.

## POST /api/sessions/{sessionId}/join

Mục đích:

- User xin token để vào phòng LiveKit đã `LIVE`.
- Backend kiểm tra session đang `LIVE`.
- Backend kiểm tra quyền user.
- Backend generate LiveKit token.
- Backend trả `livekitUrl` + `token`.

Controller/method:

- `LiveSessionController.joinSession`

Auth:

```java
@PreAuthorize("hasRole('STUDENT')")
```

Quan trọng:

- Theo controller hiện tại, chỉ `STUDENT` gọi được `/join`.
- `ADMIN` và `TEACHER` không gọi được endpoint này vì `@PreAuthorize("hasRole('STUDENT')")`.

Service flow:

1. Load current user.
2. Load session.
3. Nếu `session.status != LIVE`, throw `BadRequestException`.
4. Check user là active member của classroom chứa session bằng `isActiveMember(classroomId, userId)`.
5. Nếu không active member, throw `ForbiddenException`.
6. Generate LiveKit token:
   - `roomName = session.getLivekitRoomName()`
   - `identity = currentUser.getId()`
   - `displayName = currentUser.getUsername()`
   - `isRoomAdmin = false`
7. Return `LiveSessionTokenResponse`.

Request:

- Không có request body.

Headers:

```http
Authorization: Bearer <appJwt>
```

Response:

```json
{
  "sessionId": "session-id",
  "classroomId": "classroom-id",
  "roomName": "classroom-classroom-id-session-session-id",
  "livekitUrl": "ws://localhost:7880",
  "token": "livekit-access-token",
  "identity": "student-user-id",
  "username": "student1",
  "status": "LIVE"
}
```

Notes:

- Student cần là `ClassroomMember.status == ACTIVE`.
- Vì controller chỉ cho `STUDENT`, teacher/admin reconnect bằng `/join` hiện chưa được hỗ trợ.
- Nếu teacher reload sau khi start, gọi `/start` lại sẽ bị lỗi `Session is already LIVE`. Đây là backend issue cần xử lý.

# 5. DTO contract cho frontend

Package DTO hiện tại: `com.zeet.StreamingClassRoom.DTO`.

## CreateLiveSessionRequest

```ts
type CreateLiveSessionRequest = {
  title: string;
  description?: string | null;
  scheduledStartTime?: string | null;
  scheduledEndTime?: string | null;
  emptyTimeoutSeconds?: number | null;
  departureTimeoutSeconds?: number | null;
  maxParticipants?: number | null;
};
```

Backend type:

- `title`: `String`, required, `@NotBlank`
- `description`: `String`, optional
- `scheduledStartTime`: `LocalDateTime`, optional
- `scheduledEndTime`: `LocalDateTime`, optional
- `emptyTimeoutSeconds`: `Integer`, optional
- `departureTimeoutSeconds`: `Integer`, optional
- `maxParticipants`: `Integer`, optional

Validation:

- `scheduledEndTime` phải sau `scheduledStartTime` nếu cả hai có.
- Timeout/maxParticipants phải `>= 0` nếu truyền.

## UpdateLiveSessionRequest

```ts
type UpdateLiveSessionRequest = {
  title?: string | null;
  description?: string | null;
  status?: "SCHEDULED" | "LIVE" | "ENDED" | "CANCELLED" | null;
  scheduledStartTime?: string | null;
  scheduledEndTime?: string | null;
  emptyTimeoutSeconds?: number | null;
  departureTimeoutSeconds?: number | null;
  maxParticipants?: number | null;
};
```

Tất cả fields optional.

Rule:

- Nếu session hiện là `LIVE` hoặc `ENDED`, backend chỉ cho update `description`.
- Backend không cho update LiveKit fields trong request này.

## LiveSessionResponse

```ts
type LiveSessionResponse = {
  id: string;
  classroomId: string;
  classroomName: string;
  hostId: string;
  hostUsername: string;
  title: string;
  description: string | null;
  status: "SCHEDULED" | "LIVE" | "ENDED" | "CANCELLED";
  scheduledStartTime: string | null;
  scheduledEndTime: string | null;
  actualStartTime: string | null;
  actualEndTime: string | null;
  livekitRoomName: string | null;
  livekitRoomSid: string | null;
  emptyTimeoutSeconds: number | null;
  departureTimeoutSeconds: number | null;
  maxParticipants: number | null;
  roomMetadata: string | null;
  createdAt: string;
  updatedAt: string | null;
};
```

## LiveSessionTokenResponse

DTO dùng để frontend connect LiveKit:

```ts
type LiveSessionTokenResponse = {
  sessionId: string;
  classroomId: string;
  roomName: string;
  livekitUrl: string;
  token: string;
  identity: string;
  username: string;
  status: "SCHEDULED" | "LIVE" | "ENDED" | "CANCELLED";
};
```

Field meanings:

- `sessionId`: LiveSession id.
- `classroomId`: classroom chứa session.
- `roomName`: LiveKit roomName dùng để phân biệt phòng.
- `livekitUrl`: URL LiveKit server, ví dụ `ws://localhost:7880`.
- `token`: LiveKit access token, dùng trực tiếp với LiveKit client SDK.
- `identity`: hiện là `User.id`.
- `username`: hiện là `User.username`, dùng làm display name.
- `role`: hiện chưa có trong response.
- `status`: status của session, thường là `LIVE` sau start/join.

# 6. Quy tắc phân quyền

## ADMIN

- Tạo session: có.
- Start session: có.
- Join session: không qua `/join` hiện tại vì controller chỉ `hasRole('STUDENT')`.
- Update/delete: có.
- Xem list/detail: có.

## TEACHER

- Tạo session: có nếu quản lý classroom.
- Start session: có nếu quản lý classroom.
- Update/delete: có nếu quản lý classroom.
- Xem list/detail: có, hiện source cho teacher xem tất cả sessions/classrooms.
- Điều kiện teacher quản lý classroom:
  - `classroom.teacher.id == currentUser.id`, hoặc
  - tồn tại `ClassroomMember` với `classroomId`, `userId`, `status ACTIVE`, `role TEACHER`.

## STUDENT

- Xem sessions: có nếu active member của classroom.
- Xem detail: có nếu session thuộc classroom mà student là active member.
- Join live room: có qua `/api/sessions/{sessionId}/join` nếu session `LIVE` và active member.
- Start/update/delete: không.

## Controller `@PreAuthorize`

- Create:

```java
hasRole('ADMIN') or @classroomSecurity.canManageClassroom(#classroomId, authentication)
```

- List by classroom:

```java
hasAnyRole('ADMIN','TEACHER') or @classroomSecurity.isActiveMember(#classroomId, authentication)
```

- Detail:

```java
hasAnyRole('ADMIN','TEACHER','STUDENT')
```

- Update/delete:

```java
hasAnyRole('ADMIN','TEACHER')
```

- Start:

```java
hasAnyRole('ADMIN','TEACHER')
```

- Join:

```java
hasRole('STUDENT')
```

Service internal checks:

- `ensureCanManageClassroom`
- `ensureCanViewClassroomSessions`
- `canTeacherManageClassroom`
- `isActiveMember`

# 7. LiveKit token flow

Service: `StreamService`.

Method:

```java
generateToken(String roomName, String identity, String displayName, boolean isRoomAdmin)
```

Config:

- `livekit.api.key` từ `${LIVEKIT_API_KEY}`
- `livekit.api.secret` từ `${LIVEKIT_API_SECRET}`
- `livekit.url` từ `${LIVEKIT_URL}` nhưng `StreamService` chỉ dùng key/secret; URL được dùng trong `LiveSessionService`.

Implementation:

```java
AccessToken token = new AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET);
token.setIdentity(identity);
token.setName(displayName);
```

Khi `isRoomAdmin == true`, grants:

- `RoomJoin(true)`
- `RoomName(roomName)`
- `RoomAdmin(true)`

Khi `isRoomAdmin == false`, grants:

- `RoomJoin(true)`
- `RoomName(roomName)`

Frontend:

- Tuyệt đối không tự generate token.
- Không được biết `LIVEKIT_API_SECRET`.
- Chỉ nhận token từ backend qua `/start` hoặc `/join`.

Ví dụ payload JWT LiveKit ở mức mô tả, không phải token thật:

```json
{
  "iss": "devkey",
  "sub": "user-id",
  "name": "username",
  "video": {
    "roomJoin": true,
    "room": "classroom-classroom-id-session-session-id",
    "roomAdmin": true
  }
}
```

Với student, `roomAdmin` không có hoặc false.

# 8. LiveKit roomName

- `roomName` là tên phòng kỹ thuật bên LiveKit.
- `roomName` khác với classroom name, classCode, session title.
- `roomName` được lưu vào `live_sessions.livekit_room_name`.
- `roomName` được dùng trong token grant `RoomName(roomName)`.
- Nếu 2 user có token cùng `roomName` và cùng `livekitUrl`, họ vào cùng phòng LiveKit.

Format hiện tại:

```text
classroom-{session.getClassroom().getId()}-session-{session.getId()}
```

Method:

```java
public String generateRoomName(LiveSession session) {
    return "classroom-" + session.getClassroom().getId() + "-session-" + session.getId();
}
```

# 9. Frontend live room flow

## 9.1 Teacher/Admin start room

1. Teacher/Admin ở màn hình classroom detail hoặc session detail.
2. User bấm `Start`.
3. FE gọi:

```http
POST /api/sessions/{sessionId}/start
Authorization: Bearer <appJwt>
```

4. BE trả `LiveSessionTokenResponse`.
5. FE lấy `livekitUrl` và `token`.
6. FE chuyển sang `LiveRoomPage`.
7. FE gọi LiveKit client SDK connect.
8. FE enable camera/microphone nếu user đồng ý.
9. FE render local participant.
10. FE lắng nghe và render remote participants.

## 9.2 Student join room

1. Student xem danh sách live sessions.
2. Chỉ hiển thị nút `Join` khi `session.status === "LIVE"`.
3. Student bấm `Join`.
4. FE gọi:

```http
POST /api/sessions/{sessionId}/join
Authorization: Bearer <appJwt>
```

5. BE trả `LiveSessionTokenResponse`.
6. FE connect LiveKit bằng `livekitUrl` + `token`.
7. FE publish audio/video nếu user cho phép.
8. FE render remote participants.

## 9.3 Teacher reconnect

Source hiện tại có vấn đề:

- Nếu teacher reload app sau khi session đã `LIVE`, gọi `/start` lại sẽ lỗi `Session is already LIVE`.
- Teacher không gọi được `/join` vì endpoint có `@PreAuthorize("hasRole('STUDENT')")`.
- Vì vậy frontend hiện chưa có flow reconnect tốt cho teacher/admin.

Workaround tạm thời:

- Không auto-call `/start` khi session đã `LIVE`.
- Hiển thị lỗi thân thiện và yêu cầu backend bổ sung endpoint/reuse `/join` cho teacher/admin, hoặc cho `/start` trả token nếu session đã `LIVE` và user có quyền manage.

# 10. LiveRoomPage requirements cho Codex 2

Components nên generate:

- `LiveRoomPage`
- `VideoGrid`
- `ParticipantTile`
- `LocalParticipantTile`
- `RemoteParticipantTile`
- `RoomControls`
- `ParticipantsSidebar`
- `ConnectionStatus`
- `ErrorBanner`

Chức năng:

- Connect LiveKit bằng `livekitUrl + token`.
- Hiển thị local camera.
- Hiển thị remote participants.
- Button bật/tắt microphone.
- Button bật/tắt camera.
- Button leave room.
- Hiển thị trạng thái `connecting`, `connected`, `disconnected`.
- Hiển thị lỗi nếu token invalid hoặc connect fail.
- Khi leave room, disconnect LiveKit room và quay lại session detail/classroom detail.
- Không gọi webhook.
- Không generate token.
- Không lưu LiveKit API secret.

State tối thiểu:

- `room`
- `connectionState`
- `localParticipant`
- `remoteParticipants`
- `isMicEnabled`
- `isCameraEnabled`
- `error`

# 11. LiveKit client SDK notes

Codex 2 nên dùng package:

```bash
npm install livekit-client
```

Flow kỹ thuật:

1. Tạo `Room` instance.
2. Connect:

```ts
await room.connect(livekitUrl, token);
```

3. Enable camera/mic bằng API phù hợp của `livekit-client`, ví dụ thông qua local participant.
4. Subscribe/render remote tracks.
5. Cleanup khi component unmount:
   - remove listeners
   - disconnect room
6. Disconnect khi user bấm leave.
7. Tránh connect nhiều lần khi React re-render:
   - dùng `useEffect` phụ thuộc vào stable `livekitUrl/token`
   - guard bằng ref/state nếu cần.

Rendering:

- Local tile render local tracks.
- Remote tiles render subscribed video/audio tracks.
- Audio tracks remote cần được attached để nghe được tiếng.

# 12. API client cho Electron + React

Đề xuất cấu trúc:

```text
src/api/httpClient.ts
src/api/liveSessionApi.ts
src/types/liveSession.ts
src/pages/LiveRoomPage.tsx
src/components/live/VideoGrid.tsx
src/components/live/ParticipantTile.tsx
src/components/live/LocalParticipantTile.tsx
src/components/live/RemoteParticipantTile.tsx
src/components/live/RoomControls.tsx
src/components/live/ParticipantsSidebar.tsx
src/components/live/ConnectionStatus.tsx
src/components/live/ErrorBanner.tsx
```

`httpClient`:

- `baseURL` từ `VITE_API_BASE_URL`.
- Gắn `Authorization: Bearer <appJwt>`.
- `401` thì logout hoặc redirect login.
- Nên handle cả JSON và plain text, vì login backend hiện trả plain text.

`liveSessionApi` chỉ tạo function cho endpoint thật sự tồn tại:

- `createLiveSession(classroomId, payload)`
- `getSessionsByClassroom(classroomId)`
- `getLiveSessionById(sessionId)`
- `updateLiveSession(sessionId, payload)`
- `deleteLiveSession(sessionId)`
- `startSession(sessionId)`
- `joinSession(sessionId)`

Không tạo API frontend cho webhook.

# 13. Environment variables cho frontend

Backend config:

- `livekit.url=${LIVEKIT_URL}`
- `.env` hiện có `LIVEKIT_URL=ws://localhost:7880`.
- Backend trả `livekitUrl` trong `LiveSessionTokenResponse`.

Frontend env:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Frontend có cần `VITE_LIVEKIT_URL` không?

- Ưu tiên dùng `response.livekitUrl` từ backend.
- Có thể thêm fallback:

```text
VITE_LIVEKIT_URL=ws://localhost:7880
```

Nhưng fallback chỉ dùng khi backend response thiếu URL hoặc trong mock/dev mode.

Không đưa vào frontend:

- `LIVEKIT_API_KEY`
- `LIVEKIT_API_SECRET`
- `JWT_SECRET_KEY`

Docker/LiveKit dev:

- `docker-compose.yaml` expose LiveKit `7880`, `7881`, `7882/udp`, `50000-50050/udp`.
- `livekit.yaml` webhook URL: `http://host.docker.internal:8080/api/webhooks/livekit`.

# 14. Webhook notes

- Webhook là server-to-server.
- FE không gọi webhook.
- Sau khi FE connect thành công, LiveKit sẽ gửi events cho backend nếu webhook được config.
- Sau khi user rời phòng, LiveKit gửi participant left event nếu server config đúng.
- Backend hiện có:
  - `POST /api/webhooks/livekit`
  - `WebhooksService`
  - `WebhookReceiver`
  - `WebhooksDispatcher`
  - interface `LiveKitEventHandler`
- Backend hiện chưa có class implement `LiveKitEventHandler`.
- Vì vậy các event như `participant_joined`, `participant_left`, `room_started`, `room_finished`, `track_published`, `egress_started`, `egress_ended` chưa được xử lý.
- `session_participants` và `attendance_records` entity/table có, nhưng chưa thấy logic cập nhật khi webhook về.
- Endpoint webhook hiện có thể bị Spring Security chặn vì `SecurityConfig` chỉ permit `/api/auth/**`; `/api/webhooks/livekit` vẫn nằm trong `.anyRequest().authenticated()`.

# 15. Backend Issues / Notes

| File/method | Vấn đề | Ảnh hưởng tới frontend | Gợi ý sửa backend |
|---|---|---|---|
| `LiveSessionController.joinSession` | `@PreAuthorize("hasRole('STUDENT')")` | Teacher/Admin không dùng `/join` để reconnect được | Cho `ADMIN/TEACHER` join nếu có quyền, hoặc thêm `/api/sessions/{id}/reconnect` |
| `LiveSessionService.startSession` | Nếu session đã `LIVE` thì throw `Session is already LIVE` | Teacher reload mất token không thể lấy token mới qua `/start` | Nếu user can manage và session `LIVE`, trả token mới thay vì throw |
| `LiveSessionService.startSession` | Chưa chặn start session `ENDED` hoặc `CANCELLED` | FE có thể start lại session đã kết thúc/hủy nếu backend cho phép | Chặn `ENDED/CANCELLED` hoặc định nghĩa rule rõ |
| `LiveSessionService.startSession` | Chưa gọi LiveKit `CreateRoom` | Room phụ thuộc LiveKit auto-create on connect; không có room SID | Implement createRoom nếu cần quản lý room lifecycle |
| `LiveSessionService.joinSession` | Check `isActiveMember` cho mọi user trong service | Nếu sau này mở controller cho teacher/admin join, họ cũng cần ClassroomMember ACTIVE hoặc logic riêng | Cho admin/teacher pass theo role/manage permission |
| `LiveSessionService.joinSession` | Không check `livekitRoomName` null | Nếu session `LIVE` nhưng roomName null do dữ liệu lỗi, token roomName null/invalid có thể xảy ra | Validate roomName trước generate token |
| `LiveSessionTokenResponse` | Không có `role` field | FE muốn phân biệt host/student trong room phải dùng app auth store/JWT | Thêm `role` nếu FE cần |
| `StreamService.generateToken` | Grants chỉ có join/admin/name, chưa có explicit publish/subscriber grants | Không xác định từ source hiện tại nếu SDK defaults đủ cho publish audio/video | Confirm LiveKit grant defaults hoặc add canPublish/canSubscribe nếu SDK hỗ trợ |
| `StreamController` | File đang comment toàn bộ controller cũ `/api/stream/token` | Không còn generic token endpoint; đây là tốt cho production nhưng doc cũ có thể sai | FE chỉ dùng `/sessions/{id}/start` và `/sessions/{id}/join` |
| `SecurityConfig` | Không permit `/api/webhooks/livekit` | LiveKit webhook có thể nhận 401/403 trước controller | Permit webhook endpoint và rely on LiveKit `WebhookReceiver` auth |
| `WebhooksService.handleIncomingEvent` | Catch exception rồi chỉ log, controller vẫn trả 200 | LiveKit không retry khi backend xử lý fail | Trả non-2xx hoặc persist failed event |
| `service/webhooks` | Chưa có event handler implementation | Attendance/participants không cập nhật | Implement handlers cho participant/session events |
| `application.properties` / CORS | Không thấy global CORS config | Electron renderer/web dev có thể bị CORS | Thêm CORS config cho dev origins |
| `AuthController.login` | Login trả plain text `Login successful <token>` | FE phải parse chuỗi | Trả JSON `{ accessToken, tokenType }` |
| `.env` | Có secret thật trong local file | Không đưa secret vào frontend/docs public | Dùng `.env.example`, gitignore secrets |

# 16. Contract Summary for Codex 2

- Start room endpoint:

```http
POST /api/sessions/{sessionId}/start
Authorization: Bearer <appJwt>
```

- Join room endpoint:

```http
POST /api/sessions/{sessionId}/join
Authorization: Bearer <appJwt>
```

- Response để connect LiveKit:

```ts
{
  sessionId: string;
  classroomId: string;
  roomName: string;
  livekitUrl: string;
  token: string;
  identity: string;
  username: string;
  status: LiveSessionStatus;
}
```

- Frontend phải dùng:
  - `livekitUrl`
  - `token`
  - optional display info: `identity`, `username`, `roomName`
- Role được start:
  - `ADMIN`
  - `TEACHER` có quyền manage classroom.
- Role được join hiện tại:
  - Chỉ `STUDENT` theo controller.
  - Student phải active member và session phải `LIVE`.
- Không gọi webhook.
- Không tự tạo token.
- Không lưu LiveKit API secret.
- `LiveRoomPage` cần:
  - connect room
  - render local/remote tracks
  - mic/camera controls
  - leave/disconnect
  - connection/error states

# 17. Output cuối cùng

File này được tạo tại:

```text
docs/LIVE_ROOM_FRONTEND_HANDOFF.md
```

Các file backend chính đã đọc:

- `pom.xml`
- `application.properties`
- `.env`
- `docker-compose.yaml`
- `livekit.yaml`
- `config/SecurityConfig.java`
- `config/JwtFilter.java`
- `security/ClassroomSecurity.java`
- `controller/LiveSessionController.java`
- `controller/ClassroomController.java`
- `controller/AuthController.java`
- `controller/WebhooksController.java`
- `controller/StreamController.java`
- `service/LiveSessionService.java`
- `service/StreamService.java`
- `service/AuthService.java`
- `service/JWTService.java`
- `service/WebhooksService.java`
- `service/webhooks/*`
- `repository/LiveSessionRepository.java`
- `repository/ClassroomRepository.java`
- `repository/ClassroomMemberRepository.java`
- `repository/UserRepository.java`
- `model/LiveSession.java`
- `model/LiveSessionStatus.java`
- `model/Classroom.java`
- `model/ClassroomMember.java`
- `model/User.java`
- `DTO/*LiveSession*.java`
- `exception/*`
- `db/migration/V3__basic_schema.sql`

Endpoint live session đã document:

- `POST /api/classrooms/{classroomId}/sessions`
- `GET /api/classrooms/{classroomId}/sessions`
- `GET /api/sessions/{sessionId}`
- `PUT /api/sessions/{sessionId}`
- `DELETE /api/sessions/{sessionId}`
- `POST /api/sessions/{sessionId}/start`
- `POST /api/sessions/{sessionId}/join`

Việc Codex 2 cần làm ở frontend:

- Implement API client cho live session endpoints.
- Implement session list/detail actions `Start` và `Join`.
- Implement `LiveRoomPage` với LiveKit client SDK.
- Dùng `livekitUrl` + `token` từ backend response.
- Quản lý mic/camera/leave/connection state.
- Không gọi webhook và không tự generate token.
