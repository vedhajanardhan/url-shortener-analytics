CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at      DATETIME NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB;

-- Nullable so pre-existing rows (created before auth existed) stay valid;
-- ownership checks in the service layer treat a NULL owner as "unowned/legacy".
ALTER TABLE url_mapping ADD COLUMN user_id BIGINT NULL;
ALTER TABLE url_mapping ADD CONSTRAINT fk_url_mapping_user
    FOREIGN KEY (user_id) REFERENCES users (id);
CREATE INDEX idx_url_mapping_user_id ON url_mapping (user_id);
