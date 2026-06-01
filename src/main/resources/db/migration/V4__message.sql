CREATE TABLE chat_messages (
    id VARCHAR(255) PRIMARY KEY,

    session_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,

    content TEXT NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'SENT',

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_chat_messages_session
        FOREIGN KEY (session_id)
        REFERENCES live_sessions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_chat_messages_sender
        FOREIGN KEY (sender_id)
        REFERENCES users(id),


    CONSTRAINT chk_chat_messages_status
        CHECK (status IN ('SENT', 'DELETED'))
);

CREATE INDEX idx_chat_messages_session_id
    ON chat_messages(session_id);

CREATE INDEX idx_chat_messages_sender_id
    ON chat_messages(sender_id);

CREATE INDEX idx_chat_messages_created_at
    ON chat_messages(created_at);

CREATE INDEX idx_chat_messages_session_created_at
    ON chat_messages(session_id, created_at);