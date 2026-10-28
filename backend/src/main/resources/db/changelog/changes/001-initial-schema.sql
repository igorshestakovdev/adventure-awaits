--liquibase formatted sql

--changeset adventure-book:001-initial-schema
--comment: Create the initial schema or adopt existing H2 tables without changing their data.
CREATE TABLE IF NOT EXISTS imported_books (
  id VARCHAR(160) PRIMARY KEY,
  content CLOB NOT NULL,
  imported_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS game_saves (
  id UUID PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  book_id VARCHAR(160) NOT NULL,
  book_version VARCHAR(64) NOT NULL,
  section_id BIGINT NOT NULL,
  health INTEGER NOT NULL,
  status VARCHAR(20) NOT NULL,
  revision BIGINT NOT NULL,
  saved_at TIMESTAMP WITH TIME ZONE NOT NULL
);
