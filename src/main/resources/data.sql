-- 앱이 뜰 때마다 실행되므로 재실행에 안전해야 한다

INSERT INTO product_category (name)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE name = new.name;
