
CREATE EXTENSION IF NOT EXISTS pgcrypto;
-- =========================
-- classrooms
-- =========================
CREATE TABLE classrooms (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    name VARCHAR(255) NOT NULL,
    description TEXT,

    teacher_id VARCHAR(255) NOT NULL,
    class_code VARCHAR(100) NOT NULL UNIQUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_classrooms_teacher
        FOREIGN KEY (teacher_id)
        REFERENCES users(id)
);

CREATE INDEX idx_classrooms_teacher_id
    ON classrooms(teacher_id);

CREATE INDEX idx_classrooms_class_code
    ON classrooms(class_code);

-- =========================
-- classroom_members
-- =========================
CREATE TABLE classroom_members (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    classroom_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,

    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',

    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_classroom_members_classroom
        FOREIGN KEY (classroom_id)
        REFERENCES classrooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_classroom_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT uq_classroom_members_classroom_user
        UNIQUE (classroom_id, user_id),

    CONSTRAINT chk_classroom_members_role
        CHECK (role IN ('STUDENT', 'TEACHER', 'ASSISTANT')),

    CONSTRAINT chk_classroom_members_status
        CHECK (status IN ('ACTIVE', 'PENDING', 'REMOVED', 'BLOCKED'))
);

CREATE INDEX idx_classroom_members_classroom_id
    ON classroom_members(classroom_id);

CREATE INDEX idx_classroom_members_user_id
    ON classroom_members(user_id);

CREATE INDEX idx_classroom_members_status
    ON classroom_members(status);

-- =========================
-- live_sessions
-- =========================
CREATE TABLE live_sessions (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    classroom_id VARCHAR(255) NOT NULL,
    host_id VARCHAR(255) NOT NULL,

    title VARCHAR(255) NOT NULL,
    description TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'SCHEDULED',

    scheduled_start_time TIMESTAMP,
    scheduled_end_time TIMESTAMP,

    actual_start_time TIMESTAMP,
    actual_end_time TIMESTAMP,

    livekit_room_name VARCHAR(255) UNIQUE,
    livekit_room_sid VARCHAR(255),

    empty_timeout_seconds INTEGER DEFAULT 300,
    departure_timeout_seconds INTEGER DEFAULT 20,
    max_participants INTEGER DEFAULT 0,
    room_metadata TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_live_sessions_classroom
        FOREIGN KEY (classroom_id)
        REFERENCES classrooms(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_live_sessions_host
        FOREIGN KEY (host_id)
        REFERENCES users(id),

    CONSTRAINT chk_live_sessions_status
        CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED'))
);

CREATE INDEX idx_live_sessions_classroom_id
    ON live_sessions(classroom_id);

CREATE INDEX idx_live_sessions_host_id
    ON live_sessions(host_id);

CREATE INDEX idx_live_sessions_status
    ON live_sessions(status);

CREATE INDEX idx_live_sessions_livekit_room_name
    ON live_sessions(livekit_room_name);

CREATE INDEX idx_live_sessions_livekit_room_sid
    ON live_sessions(livekit_room_sid);

-- =========================
-- session_participants
-- =========================
CREATE TABLE session_participants (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    session_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,

    livekit_participant_identity VARCHAR(255),
    livekit_participant_sid VARCHAR(255),

    joined_at TIMESTAMP,
    left_at TIMESTAMP,

    duration_seconds BIGINT DEFAULT 0,

    role VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'JOINED',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_session_participants_session
        FOREIGN KEY (session_id)
        REFERENCES live_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_session_participants_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT chk_session_participants_role
        CHECK (role IN ('STUDENT', 'TEACHER', 'ASSISTANT')),

    CONSTRAINT chk_session_participants_status
        CHECK (status IN ('JOINED', 'LEFT', 'KICKED', 'DISCONNECTED'))
);

CREATE INDEX idx_session_participants_session_id
    ON session_participants(session_id);

CREATE INDEX idx_session_participants_user_id
    ON session_participants(user_id);

CREATE INDEX idx_session_participants_identity
    ON session_participants(livekit_participant_identity);

CREATE INDEX idx_session_participants_sid
    ON session_participants(livekit_participant_sid);

CREATE INDEX idx_session_participants_status
    ON session_participants(status);

-- =========================
-- attendance_records
-- =========================
CREATE TABLE attendance_records (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    session_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,

    status VARCHAR(30) NOT NULL,

    joined_at TIMESTAMP,
    left_at TIMESTAMP,

    total_duration_seconds BIGINT DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_attendance_records_session
        FOREIGN KEY (session_id)
        REFERENCES live_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_attendance_records_user
        FOREIGN KEY (user_id)
        REFERENCES users(id),

    CONSTRAINT uq_attendance_records_session_user
        UNIQUE (session_id, user_id),

    CONSTRAINT chk_attendance_records_status
        CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'LEFT_EARLY', 'EXCUSED'))
);

CREATE INDEX idx_attendance_records_session_id
    ON attendance_records(session_id);

CREATE INDEX idx_attendance_records_user_id
    ON attendance_records(user_id);

CREATE INDEX idx_attendance_records_status
    ON attendance_records(status);

-- =========================
-- livekit_webhook_events
-- =========================
CREATE TABLE livekit_webhook_events (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    event_type VARCHAR(100) NOT NULL,

    livekit_room_name VARCHAR(255),
    livekit_room_sid VARCHAR(255),

    participant_identity VARCHAR(255),
    participant_sid VARCHAR(255),

    raw_payload JSONB NOT NULL,

    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP,

    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_livekit_webhook_events_event_type
    ON livekit_webhook_events(event_type);

CREATE INDEX idx_livekit_webhook_events_room_name
    ON livekit_webhook_events(livekit_room_name);

CREATE INDEX idx_livekit_webhook_events_room_sid
    ON livekit_webhook_events(livekit_room_sid);

CREATE INDEX idx_livekit_webhook_events_participant_identity
    ON livekit_webhook_events(participant_identity);

CREATE INDEX idx_livekit_webhook_events_participant_sid
    ON livekit_webhook_events(participant_sid);

CREATE INDEX idx_livekit_webhook_events_processed
    ON livekit_webhook_events(processed);

-- =========================
-- recordings
-- =========================
CREATE TABLE recordings (
    id VARCHAR(255) PRIMARY KEY DEFAULT gen_random_uuid()::text,

    session_id VARCHAR(255) NOT NULL,

    livekit_egress_id VARCHAR(255),
    file_url TEXT,
    file_name VARCHAR(255),

    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',

    started_at TIMESTAMP,
    ended_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_recordings_session
        FOREIGN KEY (session_id)
        REFERENCES live_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_recordings_status
        CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED', 'DELETED'))
);

CREATE INDEX idx_recordings_session_id
    ON recordings(session_id);

CREATE INDEX idx_recordings_livekit_egress_id
    ON recordings(livekit_egress_id);

CREATE INDEX idx_recordings_status
    ON recordings(status);