CREATE TABLE url_mapping (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code      VARCHAR(48)  NOT NULL,
    long_url        VARCHAR(2048) NOT NULL,
    created_by      VARCHAR(100),
    created_at      DATETIME NOT NULL,
    expires_at      DATETIME NULL,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_short_code UNIQUE (short_code)
) ENGINE=InnoDB;

CREATE INDEX idx_url_mapping_created_at ON url_mapping (created_at);

CREATE TABLE click_event (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code      VARCHAR(48)  NOT NULL,
    clicked_at      DATETIME NOT NULL,
    ip_address      VARCHAR(64),
    user_agent      VARCHAR(512),
    referrer        VARCHAR(2048),
    country         VARCHAR(100),
    device_type     VARCHAR(32),
    browser         VARCHAR(64),
    os              VARCHAR(64),
    CONSTRAINT fk_click_short_code FOREIGN KEY (short_code) REFERENCES url_mapping (short_code)
) ENGINE=InnoDB;

CREATE INDEX idx_click_event_short_code_time ON click_event (short_code, clicked_at);
CREATE INDEX idx_click_event_country ON click_event (short_code, country);
CREATE INDEX idx_click_event_device ON click_event (short_code, device_type);
