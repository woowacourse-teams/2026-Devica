CREATE TABLE IF NOT EXISTS product_category
(
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    code VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_category_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS usage_purpose
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    product_category_id BIGINT       NOT NULL,
    code                VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usage_purpose_category_code (product_category_id, code),
    CONSTRAINT fk_usage_purpose_product_category
        FOREIGN KEY (product_category_id) REFERENCES product_category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS question
(
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    usage_purpose_id BIGINT       NOT NULL,
    code             VARCHAR(255) NOT NULL,
    title            VARCHAR(256) NOT NULL,
    description      VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_purpose_code (usage_purpose_id, code),
    CONSTRAINT fk_question_usage_purpose
        FOREIGN KEY (usage_purpose_id) REFERENCES usage_purpose (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS question_option
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    question_id BIGINT       NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    content     VARCHAR(128) NOT NULL,
    description VARCHAR(256) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_question_option_question_code (question_id, code),
    CONSTRAINT fk_question_option_question
        FOREIGN KEY (question_id) REFERENCES question (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
