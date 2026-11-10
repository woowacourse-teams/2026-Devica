-- V1.29 판매처 보정 전에 같은 제품·판매처·판매상품 ID의 중복을 DB에서 차단한다.
-- 판매처를 상수로 지정하지 않으며, 판매상품 ID를 쓰는 판매처에는 모두 같은 규칙이 적용된다.
-- external_item_id가 NULL인 행(ID를 쓰지 않는 판매처, ID가 없는 기존 행)은 MySQL 유니크 인덱스에서 서로 중복으로 보지 않는다.
CREATE UNIQUE INDEX uk_product_offer_seller_item
    ON product_offer (product_id, name, external_item_id);
