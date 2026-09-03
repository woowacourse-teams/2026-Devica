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

CREATE TABLE IF NOT EXISTS cpu
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    manufacturer VARCHAR(32)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    core_count   INT          NOT NULL,
    score        INT          NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS product
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    product_category_id BIGINT       NOT NULL,
    brand               VARCHAR(64)  NOT NULL,
    name                VARCHAR(128) NOT NULL,
    code                VARCHAR(64)  NOT NULL,
    description         TEXT         NULL,
    released_at         DATE         NULL,
    created_at          DATETIME     NOT NULL,
    updated_at          DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_code (code),
    CONSTRAINT fk_product_product_category
        FOREIGN KEY (product_category_id) REFERENCES product_category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS laptop
(
    id               BIGINT        NOT NULL,
    cpu_id           BIGINT        NOT NULL,
    os               VARCHAR(16)   NOT NULL,
    memory_gb        INT           NOT NULL,
    storage_gb       INT           NOT NULL,
    weight_g         INT           NOT NULL,
    screen_size_inch DECIMAL(3, 1) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_laptop_product
        FOREIGN KEY (id) REFERENCES product (id),
    CONSTRAINT fk_laptop_cpu
        FOREIGN KEY (cpu_id) REFERENCES cpu (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS product_offer
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    product_id       BIGINT      NOT NULL,
    name             VARCHAR(64) NOT NULL,
    price            BIGINT      NOT NULL,
    external_item_id VARCHAR(64) NULL,
    purchase_url     TEXT        NOT NULL,
    status           VARCHAR(32) NOT NULL,
    created_at       DATETIME    NOT NULL,
    updated_at       DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_offer_product
        FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
