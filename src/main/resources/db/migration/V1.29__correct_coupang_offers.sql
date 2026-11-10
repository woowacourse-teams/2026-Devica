-- 쿠팡 판매처 연결·가격·품절 상태를 보정하고, 당근 참고가를 추가하며 삭제 요청한 제품을 제거한다.
-- 공유 환경에 적용하기 전까지 아래 목록에 행을 추가한다. 적용 후에는 새 버전을 사용한다.
CREATE TEMPORARY TABLE coupang_offer_corrections
(
    product_code             VARCHAR(64) NOT NULL PRIMARY KEY,
    previous_external_id     VARCHAR(64) NOT NULL,
    previous_purchase_url    TEXT        NOT NULL,
    external_item_id         VARCHAR(64) NOT NULL,
    purchase_url             TEXT        NOT NULL,
    price                    BIGINT      NOT NULL,
    checked_at               DATE        NOT NULL
) ENGINE = InnoDB;

INSERT INTO coupang_offer_corrections
    (product_code, previous_external_id, previous_purchase_url,
     external_item_id, purchase_url, price, checked_at)
VALUES
    -- 제품 1: 사용자 요청에 따라 27,390원 쿠폰 할인을 포함한 가격.
    ('MDH74KH/A', '94919713475',
     'https://www.coupang.com/vp/products/9410641464?itemId=27961674147&vendorItemId=94919713475',
     '94919713476',
     'https://www.coupang.com/vp/products/9410641464?itemId=27961674150&vendorItemId=94919713476',
     1975590, '2026-11-10'),
    -- 제품 9: 기존 연결은 동일하며, 사용자 첨부 화면의 할인 가격을 반영한다.
    ('Z1ML001Y3', '95050035786',
     'https://www.coupang.com/vp/products/9140128274?itemId=28093741944&vendorItemId=95050035786',
     '95050035786',
     'https://www.coupang.com/vp/products/9140128274?itemId=28093741944&vendorItemId=95050035786',
     4900050, '2026-11-10'),
    -- 제품 15: 기존과 같은 상품·옵션의 새 판매처이며, 사용자와 검색 API에서 가격을 확인했다.
    ('16Z90TS-GU7WK', '95514237961',
     'https://www.coupang.com/vp/products/8670821592?itemId=25170697413&vendorItemId=95514237961',
     '95599972655',
     'https://www.coupang.com/vp/products/8670821592?itemId=25170697413&vendorItemId=95599972655',
     3500000, '2026-11-10'),
    -- 제품 16: 기존과 같은 상품·옵션의 새 판매처이며, 사용자와 검색 API에서 가격을 확인했다.
    ('16Z90TR-SD7WK', '92715203892',
     'https://www.coupang.com/vp/products/8829567591?itemId=25726533506&vendorItemId=92715203892',
     '95658850422',
     'https://www.coupang.com/vp/products/8829567591?itemId=25726533506&vendorItemId=95658850422',
     3991000, '2026-11-10'),
    -- 제품 17: 기존 연결은 동일하며, 페이지와 검색 API에서 같은 가격을 확인했다.
    ('83SK0009KR', '95173538944',
     'https://www.coupang.com/vp/products/9478742459?itemId=28219563534&vendorItemId=95173538944',
     '95173538944',
     'https://www.coupang.com/vp/products/9478742459?itemId=28219563534&vendorItemId=95173538944',
     3310980, '2026-11-10'),
    -- 제품 19: 같은 모델·옵션의 새 판매상품으로 연결한다.
    ('UM3406GA-QL151W', '94616357403',
     'https://www.coupang.com/vp/products/9470833374?itemId=27653967019&vendorItemId=94616357403',
     '95469143931',
     'https://www.coupang.com/vp/products/9470833374?itemId=27653967019&vendorItemId=95469143931',
     2874900, '2026-11-10'),
    -- 제품 20: 기존 연결은 동일하며, 시크릿 탭에서 같은 모델·옵션의 현재 가격을 확인했다.
    ('G614PM-TS171W', '95503812693',
     'https://www.coupang.com/vp/products/9568845900?itemId=28559191661&vendorItemId=95503812693',
     '95503812693',
     'https://www.coupang.com/vp/products/9568845900?itemId=28559191661&vendorItemId=95503812693',
     4039000, '2026-11-10');

CREATE TEMPORARY TABLE coupang_offer_correction_targets
(
    offer_id     BIGINT      NOT NULL PRIMARY KEY,
    product_id   BIGINT      NOT NULL,
    product_code VARCHAR(64) NOT NULL
) ENGINE = InnoDB;

START TRANSACTION;

-- 가격 갱신 서비스와 같은 방식으로 제품별 모든 판매처를 잠근다.
SELECT o.id
FROM product_offer o
JOIN product p ON p.id = o.product_id
LEFT JOIN coupang_offer_corrections c ON c.product_code = p.code
WHERE c.product_code IS NOT NULL
   OR p.code IN ('MDE14KH/A', 'Z1KN0001E', 'Z1MM001CH', 'MGEC4KH/A', 'NT960UJH-X72A', '83LU000UKR',
                 'NT760VJT-A72A')
ORDER BY o.product_id, o.id
FOR UPDATE;

-- 이미 수동 적용했거나 같은 날 이후에 확인한 행은 건너뛴다.
-- checked_at은 DATE이므로 당일의 더 최신 가격도 보호하도록 < 비교를 사용한다.
-- 다른 옵션의 가격이 섞이지 않도록 연결 정보와 가격은 함께 갱신하거나 함께 유지한다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
JOIN coupang_offer_corrections c ON c.product_code = p.code
WHERE o.name = '쿠팡'
  AND (o.checked_at IS NULL OR o.checked_at < c.checked_at)
  AND (
      (o.external_item_id = c.previous_external_id AND o.purchase_url = c.previous_purchase_url)
      OR (o.external_item_id = c.external_item_id AND o.purchase_url = c.purchase_url)
  );

UPDATE product_offer o
JOIN coupang_offer_correction_targets t ON t.offer_id = o.id
JOIN coupang_offer_corrections c ON c.product_code = t.product_code
SET o.purchase_url = c.purchase_url,
    o.external_item_id = c.external_item_id,
    o.price = c.price,
    o.checked_at = c.checked_at,
    o.updated_at = CURRENT_TIMESTAMP;

-- 제품 5: 2026-11-10 동일 옵션의 일시품절을 확인했다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'MDE14KH/A'
  AND o.name = '쿠팡'
  AND o.external_item_id = '93875613386'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/9140128274?itemId=26906195808&vendorItemId=93875613386'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 제품 7: 2026-11-10 동일 옵션의 일시품절을 확인했다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'Z1KN0001E'
  AND o.name = '쿠팡'
  AND o.external_item_id = '94002473487'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/9140128274?itemId=27033962079&vendorItemId=94002473487'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 제품 10: 2026-11-10 사용자 첨부 화면에서 동일 모델·옵션의 일시품절을 확인했다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'Z1MM001CH'
  AND o.name = '쿠팡'
  AND o.external_item_id = '95050035747'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/9140128274?itemId=28093741917&vendorItemId=95050035747'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 제품 12: 2026-11-10 사용자 첨부 화면에서 동일 모델·옵션의 일시품절을 확인했다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'MGEC4KH/A'
  AND o.name = '쿠팡'
  AND o.external_item_id = '94919837921'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/9410700446?itemId=27961799447&vendorItemId=94919837921'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 제품 14: 사용자 요청으로 기존 쿠팡 판매처를 품절 처리한다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'NT960UJH-X72A'
  AND o.name = '쿠팡'
  AND o.external_item_id = '94658532515'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/9339542914?itemId=27696774261&vendorItemId=94658532515'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 제품 18: 2026-11-10 사용자 첨부 화면에서 동일 모델·옵션의 일시품절을 확인했다.
-- 이미 품절 처리했거나 같은 날 이후 가격을 재확인한 행은 건너뛴다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = '83LU000UKR'
  AND o.name = '쿠팡'
  AND o.external_item_id = '92932462159'
  AND o.purchase_url = 'https://www.coupang.com/vp/products/8888042529?itemId=25949552933&vendorItemId=92932462159'
  AND o.status = 'ON_SALE'
  AND (o.checked_at IS NULL OR o.checked_at < '2026-11-10');

-- 품절 확인으로 마지막 가격과 가격 확인일을 변경하지 않는다.
UPDATE product_offer o
JOIN coupang_offer_correction_targets t ON t.offer_id = o.id
SET o.status = 'SOLD_OUT',
    o.updated_at = CURRENT_TIMESTAMP
WHERE t.product_code IN ('MDE14KH/A', 'Z1KN0001E', 'Z1MM001CH', 'MGEC4KH/A', 'NT960UJH-X72A', '83LU000UKR');

-- 제품 14: 사용자 요청에 따라 첨부된 미개봉 당근 매물의 판매 희망가를 참고가로 등록한다.
-- 기존 당근 오퍼가 있으면 수동 반영 여부와 확인일에 관계없이 그대로 보존한다.
-- 기존 당근 등록 방식처럼 개별 매물 대신 검색 결과로 연결하고, 조사일을 저장한다.
INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at, created_at, updated_at)
SELECT p.id, '당근', 4100000, NULL,
       'https://www.daangn.com/kr/search/buy-sell/?q=NT960UJH-X72A%20%EB%AF%B8%EA%B0%9C%EB%B4%89',
       'ON_SALE', '2026-11-10', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM product p
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = '당근'
WHERE p.code = 'NT960UJH-X72A'
  AND existing.id IS NULL;

-- 당근은 품절 UPDATE 이후 대상에 넣고, 이미 등록된 경우도 현재 값으로 최저가를 다시 계산한다.
INSERT INTO coupang_offer_correction_targets (offer_id, product_id, product_code)
SELECT o.id, p.id, p.code
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'NT960UJH-X72A'
  AND o.name = '당근';

-- 보정 대상 제품은 다른 판매처까지 포함해 최저가를 다시 계산한다.
DELETE m FROM product_min_price m
JOIN (SELECT DISTINCT product_id FROM coupang_offer_correction_targets) t ON t.product_id = m.product_id;

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN (SELECT DISTINCT product_id FROM coupang_offer_correction_targets) t ON t.product_id = o.product_id
WHERE o.status = 'ON_SALE'
GROUP BY o.product_id;

-- 제품 13: 공식 사양을 확정하지 못해 사용자 요청으로 제품과 종속 데이터를 삭제한다.
-- 이미 수동 삭제된 경우 각 DELETE는 0건으로 건너뛴다.
DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code = 'NT760VJT-A72A';

DELETE o FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'NT760VJT-A72A';

DELETE l FROM laptop l
JOIN product p ON p.id = l.id
WHERE p.code = 'NT760VJT-A72A';

DELETE FROM product
WHERE code = 'NT760VJT-A72A';

COMMIT;

DROP TEMPORARY TABLE coupang_offer_correction_targets;
DROP TEMPORARY TABLE coupang_offer_corrections;
