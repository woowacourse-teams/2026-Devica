-- 2026-11-02 구매 가능 표시를 확인한 학생 인증 판매가 6건. 품절·KB Pay 예상가는 제외한다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     price_condition, price_basis, link_type, created_at, updated_at)
-- 누락·사양 불일치·기존 학생몰 오퍼가 하나라도 있으면 NOT NULL 제약으로 전체 등록을 중단한다.
SELECT CASE WHEN COUNT(c.id) OVER () = 6 AND COUNT(existing.id) OVER () = 0 THEN p.id END,
       '학생복지스토어',
       verified.price,
       NULL,
       verified.purchase_url,
       'ON_SALE',
       '2026-11-02',
       verified.price_condition,
       'SELLER_PRICE',
       'PURCHASE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT 'MDHD4KH/A' AS code, 'MacBook Air 13 M5' AS name, 'Apple M5' AS cpu_name, 10 AS cpu_core_count,
           24 AS memory_gb, 1024 AS storage_gb, 13.6 AS screen_size_inch, 2813400 AS price,
           '학생 인증 필요 · 스타라이트 · 한국어 키보드 · MDHD4KH/A' AS price_condition,
           'https://www.univstore.com/item/138930' AS purchase_url
    UNION ALL
    SELECT 'MDVD4KH/A', 'MacBook Air 15 M5', 'Apple M5', 10, 16, 512, 15.3, 2310600,
           '학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVQ4KH/A', 'https://www.univstore.com/item/138924'
    UNION ALL
    SELECT 'MDVC4KH/A', 'MacBook Air 15 M5', 'Apple M5', 10, 24, 1024, 15.3, 3095200,
           '학생 인증 필요 · 스카이 블루 · 한국어 키보드 · MDVU4KH/A', 'https://www.univstore.com/item/138923'
    UNION ALL
    SELECT 'Z1MH001VY', 'MacBook Pro 14 M5 Pro', 'Apple M5 Pro 15-core', 15, 24, 1024, 14.2, 3906700,
           '학생 인증 필요 · 실버 · 스탠다드 디스플레이 · 한국어 키보드 · MGDN4KH/A', 'https://www.univstore.com/item/138852'
    UNION ALL
    SELECT 'MGE44KH/A', 'MacBook Pro 16 M5 Pro', 'Apple M5 Pro 18-core', 18, 24, 1024, 16.2, 4522600,
           '학생 인증 필요 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드 · MGEA4KH/A', 'https://www.univstore.com/item/138848'
    UNION ALL
    SELECT 'MGEC4KH/A', 'MacBook Pro 16 M5 Pro', 'Apple M5 Pro 18-core', 18, 48, 1024, 16.2, 5607000,
           '학생 인증 필요 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드 · MGEC4KH/A', 'https://www.univstore.com/item/138847'
) verified
LEFT JOIN product p ON p.code = verified.code AND p.brand = 'Apple' AND p.name = verified.name
LEFT JOIN laptop l ON l.id = p.id AND l.os = 'MAC'
    AND l.memory_gb = verified.memory_gb AND l.storage_gb = verified.storage_gb
    AND l.screen_size_inch = verified.screen_size_inch
LEFT JOIN cpu c ON c.id = l.cpu_id AND c.manufacturer = 'Apple'
    AND c.name = verified.cpu_name AND c.core_count = verified.cpu_core_count
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = '학생복지스토어';

DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code IN ('MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A', 'Z1MH001VY', 'MGE44KH/A', 'MGEC4KH/A');

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code IN ('MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A', 'Z1MH001VY', 'MGE44KH/A', 'MGEC4KH/A')
    AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
