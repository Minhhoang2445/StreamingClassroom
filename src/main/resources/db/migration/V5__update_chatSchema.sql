ALTER TABLE chat_messages
ADD COLUMN message_type VARCHAR(30) NOT NULL DEFAULT 'TEXT',
ADD COLUMN file_name VARCHAR(255),
ADD COLUMN file_path TEXT,
ADD COLUMN file_url TEXT,
ADD COLUMN file_type VARCHAR(100),
ADD COLUMN file_size BIGINT;

ALTER TABLE chat_messages
ADD CONSTRAINT chk_chat_messages_message_type
CHECK (message_type IN ('TEXT', 'FILE'));