-- 판매처를 화면 표시 이름(name) 대신 식별 코드(seller)로 구분하고,
-- 같은 제품에 같은 판매처의 같은 판매상품이 두 번 등록되지 않도록 막는다.
-- MySQL DDL은 롤백되지 않으므로, 적용할 수 없는 데이터가 있으면 컬럼을 바꾸기 전에 중단한다.
-- 오류를 원하는 값과 함께 내기 위해 검사 결과를 PK 임시 테이블에 두 번 넣는다.

-- 1. 식별 코드로 바꿀 수 없는 판매처 이름이 있으면 "Duplicate entry '<판매처 이름>'"으로 중단한다.
CREATE TEMPORARY TABLE unknown_product_offer_seller_names
(
    name VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE = InnoDB;

INSERT INTO unknown_product_offer_seller_names (name)
SELECT unknown.name
FROM (
    SELECT DISTINCT name
    FROM product_offer
    WHERE name NOT IN ('쿠팡', 'Apple 공홈', '학생복지스토어', '당근')
) unknown
CROSS JOIN (SELECT 1 AS attempt UNION ALL SELECT 2) twice;

-- 2. 이미 같은 제품·판매처·판매상품 ID 행이 여럿이면 "Duplicate entry '<모델 코드>'"로 중단한다.
--    판매처 이름과 식별 코드는 1:1이므로 이름 기준으로 검사한다.
CREATE TEMPORARY TABLE duplicate_product_offer_seller_items
(
    product_code VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE = InnoDB;

INSERT INTO duplicate_product_offer_seller_items (product_code)
SELECT duplicated.code
FROM (
    SELECT DISTINCT p.code
    FROM product_offer o
    JOIN product p ON p.id = o.product_id
    WHERE o.external_item_id IS NOT NULL
    GROUP BY p.code, o.product_id, o.name, o.external_item_id
    HAVING COUNT(*) > 1
) duplicated
CROSS JOIN (SELECT 1 AS attempt UNION ALL SELECT 2) twice;

DROP TEMPORARY TABLE duplicate_product_offer_seller_items;
DROP TEMPORARY TABLE unknown_product_offer_seller_names;

-- 3. 식별 코드를 채운 뒤 필수 값으로 바꾸고 중복 제약을 건다.
ALTER TABLE product_offer
    ADD COLUMN seller VARCHAR(32) NULL AFTER product_id;

UPDATE product_offer
SET seller = CASE name
                 WHEN '쿠팡' THEN 'COUPANG'
                 WHEN 'Apple 공홈' THEN 'APPLE_STORE'
                 WHEN '학생복지스토어' THEN 'STUDENT_STORE'
                 WHEN '당근' THEN 'DAANGN'
             END;

-- external_item_id가 NULL인 행은 MySQL 유니크 인덱스에서 서로 중복으로 보지 않는다.
ALTER TABLE product_offer
    MODIFY COLUMN seller VARCHAR(32) NOT NULL,
    ADD CONSTRAINT uk_product_offer_seller_item UNIQUE (product_id, seller, external_item_id);
