INSERT INTO product_category (code)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE code = new.code;

INSERT INTO usage_purpose (product_category_id, code)
SELECT id, 'BACKEND_DEVELOPMENT'
FROM product_category
WHERE code = 'LAPTOP'
ON DUPLICATE KEY UPDATE usage_purpose.code = usage_purpose.code;

-- 아래는 목록 조회를 눈으로 확인하기 위한 예시 제품이다.
-- 여러 번 실행해도 같은 상태가 되도록 이미 있으면 넣지 않는다.
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
SELECT 'Intel', 'Intel Core Ultra 7 255H', 16, 24000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cpu WHERE name = 'Intel Core Ultra 7 255H');

INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
SELECT 'Apple', 'Apple M4', 10, 21000, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM cpu WHERE name = 'Apple M4');

INSERT INTO product (product_category_id, brand, name, code, released_at, created_at, updated_at)
SELECT category.id, 'LG', 'gram Pro 16', 'LG-GRAM-PRO-16', '2026-01-10', NOW(), NOW()
FROM product_category category
WHERE category.code = 'LAPTOP'
ON DUPLICATE KEY UPDATE product.code = product.code;

INSERT INTO product (product_category_id, brand, name, code, released_at, created_at, updated_at)
SELECT category.id, 'Apple', 'MacBook Air 13', 'APPLE-MBA-13-M4', '2026-03-05', NOW(), NOW()
FROM product_category category
WHERE category.code = 'LAPTOP'
ON DUPLICATE KEY UPDATE product.code = product.code;

INSERT INTO product (product_category_id, brand, name, code, released_at, created_at, updated_at)
SELECT category.id, 'Samsung', 'Galaxy Book5', 'SS-GB5-16', '2026-02-01', NOW(), NOW()
FROM product_category category
WHERE category.code = 'LAPTOP'
ON DUPLICATE KEY UPDATE product.code = product.code;

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
SELECT product.id, cpu.id, 'WINDOWS', 32, 1024, 1199, 16.0
FROM product, cpu
WHERE product.code = 'LG-GRAM-PRO-16' AND cpu.name = 'Intel Core Ultra 7 255H'
  AND NOT EXISTS (SELECT 1 FROM laptop WHERE laptop.id = product.id);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
SELECT product.id, cpu.id, 'MAC', 16, 512, 1240, 13.6
FROM product, cpu
WHERE product.code = 'APPLE-MBA-13-M4' AND cpu.name = 'Apple M4'
  AND NOT EXISTS (SELECT 1 FROM laptop WHERE laptop.id = product.id);

INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
SELECT product.id, cpu.id, 'WINDOWS', 16, 512, 1560, 16.0
FROM product, cpu
WHERE product.code = 'SS-GB5-16' AND cpu.name = 'Intel Core Ultra 7 255H'
  AND NOT EXISTS (SELECT 1 FROM laptop WHERE laptop.id = product.id);

-- gram: 판매 중 둘. 더 싼 오퍼가 품절이라 최저가에 섞이면 안 된다.
INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
SELECT product.id, offer.name, offer.price, offer.url, offer.status, NOW(), NOW()
FROM product,
     (SELECT 'LG-GRAM-PRO-16' AS product_code, '쿠팡' AS name, 2990000 AS price,
             'https://example.com/gram/coupang' AS url, 'ON_SALE' AS status
      UNION ALL SELECT 'LG-GRAM-PRO-16', '네이버', 2850000, 'https://example.com/gram/naver', 'ON_SALE'
      UNION ALL SELECT 'LG-GRAM-PRO-16', '11번가', 2500000, 'https://example.com/gram/11st', 'SOLD_OUT'
      UNION ALL SELECT 'APPLE-MBA-13-M4', '애플', 1890000, 'https://example.com/mba/apple', 'ON_SALE'
      -- Galaxy Book5 는 판매 중 오퍼가 없어 목록에서 빠진다
      UNION ALL SELECT 'SS-GB5-16', '쿠팡', 1290000, 'https://example.com/book/coupang', 'DISCONTINUED'
     ) offer
WHERE product.code = offer.product_code
  AND NOT EXISTS (SELECT 1 FROM product_offer existing
                  WHERE existing.product_id = product.id AND existing.name = offer.name);
