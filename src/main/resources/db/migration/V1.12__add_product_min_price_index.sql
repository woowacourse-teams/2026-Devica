-- 가격순 목록이 최저가 순서대로 읽다가 필요한 건수에서 멈출 수 있게 한다
-- InnoDB 보조 인덱스에는 PK(product_id)가 붙어 있어 같은 가격 안에서는 id 순서로 읽힌다
ALTER TABLE product_min_price
    ADD INDEX idx_product_min_price_min_price (min_price),
    ALGORITHM = INPLACE,
    LOCK = NONE;
