INSERT INTO product_category (name)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE name = new.name;
