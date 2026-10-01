CREATE TABLE board_post
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    usage_purpose_id BIGINT       NOT NULL,
    title            VARCHAR(255) NOT NULL,
    content          TEXT         NOT NULL,
    created_at       DATETIME     NOT NULL,
    updated_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_board_post_usage_purpose
        FOREIGN KEY (usage_purpose_id) REFERENCES usage_purpose (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
