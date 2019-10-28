CREATE USER chatserver;
CREATE DATABASE chat;
GRANT ALL ON DATABASE chat TO chatserver;

SET DATABASE = chat;

DROP TABLE IF EXISTS chatroom_snapshots;
DROP TABLE IF EXISTS chatroom_events;
DROP TABLE IF EXISTS chatroom_participants;
DROP TABLE IF EXISTS chatrooms;
DROP TABLE IF EXISTS refresh_tokens;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS snapshot;
DROP TABLE IF EXISTS journal;

CREATE TABLE IF NOT EXISTS journal (
    ordering BIGSERIAL,
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    deleted BOOLEAN DEFAULT FALSE,
    tags VARCHAR(255) DEFAULT NULL,
    message BYTEA NOT NULL,
    PRIMARY KEY (persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON journal(ordering);


CREATE TABLE IF NOT EXISTS snapshot (
    persistence_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    created BIGINT NOT NULL,
    snapshot BYTEA NOT NULL,
    PRIMARY KEY (persistence_id, sequence_number)
);


CREATE TABLE IF NOT EXISTS users (
    user_id VARCHAR(255) NOT NULL,
    user_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id)
);


CREATE TABLE IF NOT EXISTS refresh_tokens (
    user_id VARCHAR(255) NOT NULL,
    refresh_token VARCHAR(255) NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, refresh_token),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS chatrooms (
    chatroom_id VARCHAR(255) NOT NULL,
    chatroom_name VARCHAR(255) NOT NULL,
    is_open BOOLEAN DEFAULT TRUE,
    PRIMARY KEY (chatroom_id)
);


CREATE TABLE IF NOT EXISTS chatroom_participants (
    user_id VARCHAR(255) NOT NULL,
    chatroom_id VARCHAR(255) NOT NULL,
    participated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, chatroom_id),
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (chatroom_id) REFERENCES chatrooms(chatroom_id) ON DELETE CASCADE
);


CREATE TABLE IF NOT EXISTS chatroom_events (
    ordering BIGSERIAL,
    chatroom_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    manifest VARCHAR(255) DEFAULT NULL,
    event BYTEA NOT NULL,
    PRIMARY KEY (chatroom_id, sequence_number),
    FOREIGN KEY (chatroom_id) REFERENCES chatrooms(chatroom_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX chatroom_event_ordering ON chatroom_events(ordering);


CREATE TABLE chatroom_snapshots (
    chatroom_id VARCHAR(255) NOT NULL,
    sequence_number BIGINT NOT NULL,
    manifest VARCHAR(255) NOT NULL,
    snapshot BYTEA NOT NULL,
    PRIMARY KEY (chatroom_id, sequence_number)
);
