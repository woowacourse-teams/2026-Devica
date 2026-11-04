-- 검수한 M5 CPU 10코어·GPU 10코어 Air 구성 3건. 기존 코드·오퍼는 보존한다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     created_at, updated_at)
-- 세 사양이 모두 일치하고 기존 Apple 오퍼가 없어야 한다.
-- 하나라도 실패하면 모든 행의 product_id가 NULL이 되어 첫 INSERT 전체가 중단된다.
SELECT CASE WHEN COUNT(c.id) OVER () = 3 AND COUNT(existing.id) OVER () = 0 THEN p.id END,
       'Apple 공홈',
       verified.price,
       NULL,
       verified.purchase_url,
       'ON_SALE',
       '2026-11-02',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT 'MDHD4KH/A' AS code, 'MacBook Air 13 M5' AS name,
           24 AS memory_gb, 1024 AS storage_gb, 13.6 AS screen_size_inch, 3040000 AS price,
           'https://www.apple.com/kr/shop/buy-mac/macbook-air/13-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-24gb-메모리-1tb-저장-장치' AS purchase_url
    UNION ALL
    SELECT 'MDVD4KH/A', 'MacBook Air 15 M5', 16, 512, 15.3, 2490000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-air/15-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-16gb-메모리-512gb-저장-장치'
    UNION ALL
    SELECT 'MDVC4KH/A', 'MacBook Air 15 M5', 24, 1024, 15.3, 3340000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-air/15-모델-스카이-블루-m5-칩-10코어-cpu-10코어-gpu-24gb-메모리-1tb-저장-장치'
) verified
LEFT JOIN product p ON p.code = verified.code AND p.brand = 'Apple' AND p.name = verified.name
LEFT JOIN laptop l ON l.id = p.id AND l.os = 'MAC'
    AND l.memory_gb = verified.memory_gb AND l.storage_gb = verified.storage_gb
    AND l.screen_size_inch = verified.screen_size_inch
LEFT JOIN cpu c ON c.id = l.cpu_id
    AND c.manufacturer = 'Apple' AND c.name = 'Apple M5' AND c.core_count = 10
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = 'Apple 공홈';

DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code IN ('MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A');

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code IN ('MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A') AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
