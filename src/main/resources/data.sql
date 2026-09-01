INSERT INTO product_category (code)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE code = new.code;

INSERT INTO usage_purpose (product_category_id, code)
SELECT id, 'BACKEND_DEVELOPMENT'
FROM product_category
WHERE code = 'LAPTOP'
ON DUPLICATE KEY UPDATE usage_purpose.code = usage_purpose.code;
