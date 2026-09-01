INSERT INTO product_category (code)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE code = new.code;
