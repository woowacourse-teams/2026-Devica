-- 확인한 동일 사양의 Apple 구성 1건. 원 제품 코드와 쿠팡 옵션은 유지한다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     price_condition, price_basis, link_type, created_at, updated_at)
-- 대상 사양이 없거나 Apple 공홈 오퍼가 이미 있으면 NOT NULL 제약으로 중단한다.
-- product.code는 UNIQUE이며 집계는 대상이 없어도 한 행을 반환한다.
SELECT CASE WHEN COUNT(*) = 1 AND COUNT(existing.id) = 0 THEN MIN(p.id) END,
       'Apple 공홈',
       2190000,
       NULL,
       'https://www.apple.com/kr/shop/buy-mac/macbook-air/13-모델-스카이-블루-m5-칩-10코어-cpu-8코어-gpu-16gb-메모리-512gb-저장-장치',
       'ON_SALE',
       '2026-11-02',
       '일반 판매가 · 스카이 블루 · 한국어 키보드',
       'SELLER_PRICE',
       'PURCHASE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM product p
JOIN laptop l ON l.id = p.id
JOIN cpu c ON c.id = l.cpu_id
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = 'Apple 공홈'
WHERE p.code = 'MDH74KH/A' AND p.brand = 'Apple' AND p.name = 'MacBook Air 13 M5'
  AND l.os = 'MAC' AND l.memory_gb = 16 AND l.storage_gb = 512 AND l.screen_size_inch = 13.6
  AND c.manufacturer = 'Apple' AND c.name = 'Apple M5' AND c.core_count = 10;

DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code = 'MDH74KH/A';

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code = 'MDH74KH/A' AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
