CREATE TABLE board_comment
(
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    board_post_id BIGINT        NOT NULL,
    content       VARCHAR(1000) NOT NULL,
    created_at    DATETIME      NOT NULL,
    updated_at    DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_board_comment_board_post
        FOREIGN KEY (board_post_id) REFERENCES board_post (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
