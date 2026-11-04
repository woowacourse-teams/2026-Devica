INSERT INTO usage_purpose (product_category_id, code)
SELECT id, 'MOBILE_DEVELOPMENT'
FROM product_category
WHERE code = 'LAPTOP';
