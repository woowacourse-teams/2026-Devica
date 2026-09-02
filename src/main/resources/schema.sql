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
