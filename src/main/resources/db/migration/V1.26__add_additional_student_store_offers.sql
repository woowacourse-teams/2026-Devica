-- 2026-11-02 조사한 학생몰 가격 2건. 재고 상태를 별도로 판정하지 않고 기존 판매처와 동일하게 표시한다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     created_at, updated_at)
-- 누락·사양 불일치·기존 학생몰 오퍼가 하나라도 있으면 NOT NULL 제약으로 전체 등록을 중단한다.
SELECT CASE WHEN COUNT(c.id) OVER () = 2 AND COUNT(existing.id) OVER () = 0 THEN p.id END,
       '학생복지스토어',
       verified.price,
       NULL,
       verified.purchase_url,
       'ON_SALE',
       '2026-11-02',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT 'Z1ML001Y3' AS code, 'MacBook Pro 14 M5 Pro 48GB' AS name,
           'Apple M5 Pro 15-core' AS cpu_name, 15 AS cpu_core_count,
           48 AS memory_gb, 1024 AS storage_gb, 14.2 AS screen_size_inch, 4855400 AS price,
           'https://www.univstore.com/item/139570' AS purchase_url
    UNION ALL
    SELECT 'Z1MM001CH', 'MacBook Pro 14 M5 Pro 48GB 2TB', 'Apple M5 Pro 18-core', 18, 48, 2048, 14.2, 6066400,
           'https://www.univstore.com/item/139498'
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
WHERE p.code IN ('Z1ML001Y3', 'Z1MM001CH');

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code IN ('Z1ML001Y3', 'Z1MM001CH') AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
