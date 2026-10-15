-- 제품 목록이 정렬·가격 조건에 쓰는 최저가를 미리 계산해 둔다(docs/adr/0043-제품-목록-최저가-사전-계산.md)
CREATE TABLE product_min_price
(
    product_id BIGINT NOT NULL,
    min_price  BIGINT NOT NULL,
    PRIMARY KEY (product_id),
    CONSTRAINT fk_product_min_price_product
        FOREIGN KEY (product_id) REFERENCES product (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
