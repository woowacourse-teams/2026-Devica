INSERT INTO usage_purpose (product_category_id, code)
SELECT id, 'DESIGN'
FROM product_category
WHERE code = 'LAPTOP';
