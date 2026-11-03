-- 검수한 M5 Pro 구성 5건. 기본 M5 모델의 미확인 가격은 대체하지 않는다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     price_condition, price_basis, link_type, created_at, updated_at)
-- 다섯 사양이 모두 일치하고 기존 Apple 오퍼가 없어야 한다.
-- 하나라도 실패하면 모든 행의 product_id가 NULL이 되어 첫 INSERT 전체가 중단된다.
SELECT CASE WHEN COUNT(c.id) OVER () = 5 AND COUNT(existing.id) OVER () = 0 THEN p.id END,
       'Apple 공홈',
       verified.price,
       NULL,
       verified.purchase_url,
       'ON_SALE',
       '2026-11-02',
       '일반 판매가 · 스페이스 블랙 · 스탠다드 디스플레이 · 한국어 키보드',
       'SELLER_PRICE',
       'PURCHASE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT 'Z1MH001VY' AS code, 'MacBook Pro 14 M5 Pro' AS name, 15 AS cpu_core_count,
           24 AS memory_gb, 1024 AS storage_gb, 14.2 AS screen_size_inch, 4190000 AS price,
           'https://www.apple.com/kr/shop/buy-mac/macbook-pro/14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-15코어-cpu-16코어-gpu-24gb-메모리-1tb-저장-장치' AS purchase_url
    UNION ALL
    SELECT 'Z1ML001Y3', 'MacBook Pro 14 M5 Pro 48GB', 15, 48, 1024, 14.2, 5210000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-pro/14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-15코어-cpu-16코어-gpu-48gb-메모리-1tb-저장-장치'
    UNION ALL
    SELECT 'Z1MM001CH', 'MacBook Pro 14 M5 Pro 48GB 2TB', 18, 48, 2048, 14.2, 6510000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-pro/14-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-48gb-메모리-2tb-저장-장치'
    UNION ALL
    SELECT 'MGE44KH/A', 'MacBook Pro 16 M5 Pro', 18, 24, 1024, 16.2, 4990000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-pro/16-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-24gb-메모리-1tb-저장-장치'
    UNION ALL
    SELECT 'MGEC4KH/A', 'MacBook Pro 16 M5 Pro', 18, 48, 1024, 16.2, 6010000,
           'https://www.apple.com/kr/shop/buy-mac/macbook-pro/16-모델-space-black-스탠다드-디스플레이-apple-m5-pro-칩-18코어-cpu-20코어-gpu-48gb-메모리-1tb-저장-장치'
) verified
LEFT JOIN product p ON p.code = verified.code AND p.brand = 'Apple' AND p.name = verified.name
LEFT JOIN laptop l ON l.id = p.id AND l.os = 'MAC'
    AND l.memory_gb = verified.memory_gb AND l.storage_gb = verified.storage_gb
    AND l.screen_size_inch = verified.screen_size_inch
LEFT JOIN cpu c ON c.id = l.cpu_id AND c.manufacturer = 'Apple'
    AND c.name = CONCAT('Apple M5 Pro ', verified.cpu_core_count, '-core')
    AND c.core_count = verified.cpu_core_count
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = 'Apple 공홈';

DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code IN ('Z1MH001VY', 'Z1ML001Y3', 'Z1MM001CH', 'MGE44KH/A', 'MGEC4KH/A');

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code IN ('Z1MH001VY', 'Z1ML001Y3', 'Z1MM001CH', 'MGE44KH/A', 'MGEC4KH/A')
    AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
