-- Run in a dedicated MySQL 8.4 session connected to the development database.
-- Each batch commits separately. Stop on SQL errors; do not use mysql --force.
SET @perf_run_id = 'baseline-1';
SET @perf_product_count = 20000;
SET @perf_batch_size = 1000;

DROP PROCEDURE IF EXISTS seed_performance_products;

DELIMITER $$
CREATE PROCEDURE seed_performance_products(
    IN p_run_id VARCHAR(255),
    IN p_count INT,
    IN p_batch_size INT
)
BEGIN
    DECLARE v_prefix VARCHAR(30);
    DECLARE v_category_id BIGINT;
    DECLARE v_apple_cpu_id BIGINT;
    DECLARE v_intel_cpu_id BIGINT;
    DECLARE v_start INT DEFAULT 1;
    DECLARE v_batch_count INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS perf_seed_numbers;
        RESIGNAL;
    END;

    IF p_run_id IS NULL OR NOT REGEXP_LIKE(p_run_id, '^[A-Za-z0-9-]{1,24}$', 'c') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'run_id must be 1-24 letters, digits, or hyphens';
    END IF;
    IF p_count IS NULL OR p_count < 1 OR p_count > 999999 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'product_count must be between 1 and 999999';
    END IF;
    IF p_batch_size IS NULL OR p_batch_size < 1 OR p_batch_size > 1000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'batch_size must be between 1 and 1000';
    END IF;

    SET v_prefix = CONCAT('PERF-', UPPER(p_run_id), '-');
    SELECT MIN(id) INTO v_category_id FROM product_category WHERE code = 'LAPTOP';
    SELECT MIN(id) INTO v_apple_cpu_id FROM cpu WHERE manufacturer = 'Apple';
    SELECT MIN(id) INTO v_intel_cpu_id FROM cpu WHERE manufacturer = 'Intel';

    IF v_category_id IS NULL OR v_apple_cpu_id IS NULL OR v_intel_cpu_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'LAPTOP category and Apple/Intel CPUs are required';
    END IF;
    IF EXISTS (
        SELECT 1 FROM product
        WHERE code LIKE CONCAT(v_prefix, '%') AND CHAR_LENGTH(code) = CHAR_LENGTH(v_prefix) + 6
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Products for this run_id already exist; inspect before retrying';
    END IF;

    -- Reuse at most 1000 offsets, keeping recursion within the default limit.
    CREATE TEMPORARY TABLE perf_seed_numbers (n INT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
    INSERT INTO perf_seed_numbers (n)
    WITH RECURSIVE numbers(n) AS (
        SELECT 0
        UNION ALL
        SELECT n + 1 FROM numbers WHERE n + 1 < p_batch_size
    )
    SELECT n FROM numbers;

    WHILE v_start <= p_count DO
        SET v_batch_count = LEAST(p_batch_size, p_count - v_start + 1);
        START TRANSACTION;

        INSERT INTO product (product_category_id, brand, name, code, created_at, updated_at)
        SELECT v_category_id,
               ELT(MOD(v_start + n - 1, 5) + 1, 'Apple', 'Dell', 'Lenovo', 'LG', 'Samsung'),
               CONCAT('Performance Laptop ', LPAD(v_start + n, 6, '0')),
               CONCAT(v_prefix, LPAD(v_start + n, 6, '0')),
               CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM perf_seed_numbers
        WHERE n < v_batch_count
        ORDER BY n;

        INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch)
        SELECT p.id,
               IF(MOD(v_start + s.n - 1, 5) = 0, v_apple_cpu_id, v_intel_cpu_id),
               IF(MOD(v_start + s.n - 1, 5) = 0, 'MAC', 'WINDOWS'),
               ELT(MOD(MOD(v_start + s.n - 1, 5) + FLOOR((v_start + s.n - 1) / 5), 5) + 1,
                   8, 16, 24, 32, 64),
               ELT(MOD(v_start + s.n - 1, 4) + 1, 256, 512, 1024, 2048),
               ELT(MOD(v_start + s.n - 1, 4) + 1, 1150, 1350, 1550, 1750),
               ELT(MOD(v_start + s.n - 1, 5) + 1, 13.6, 15.6, 14.0, 16.0, 15.6)
        FROM perf_seed_numbers s
        JOIN product p ON p.code = CONCAT(v_prefix, LPAD(v_start + s.n, 6, '0'))
        WHERE s.n < v_batch_count;

        INSERT INTO product_offer (product_id, name, price, purchase_url, status, created_at, updated_at)
        SELECT p.id,
               CONCAT('PERF_STORE_', stores.store_no),
               800000 + MOD(v_start + s.n, 500) * 3000 + stores.store_no * 10000,
               CONCAT('https://example.invalid/performance/', UPPER(p_run_id), '/',
                      LPAD(v_start + s.n, 6, '0'), '/', stores.store_no),
               'ON_SALE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        FROM perf_seed_numbers s
        JOIN product p ON p.code = CONCAT(v_prefix, LPAD(v_start + s.n, 6, '0'))
        CROSS JOIN (SELECT 1 AS store_no UNION ALL SELECT 2 UNION ALL SELECT 3) stores
        WHERE s.n < v_batch_count;

        COMMIT;
        SET v_start = v_start + v_batch_count;
    END WHILE;

    DROP TEMPORARY TABLE perf_seed_numbers;

    SELECT COUNT(DISTINCT p.id) AS products, COUNT(DISTINCT l.id) AS laptops,
           COUNT(o.id) AS on_sale_offers
    FROM product p
    LEFT JOIN laptop l ON l.id = p.id
    LEFT JOIN product_offer o ON o.product_id = p.id AND o.status = 'ON_SALE'
    WHERE p.code LIKE CONCAT(v_prefix, '%') AND CHAR_LENGTH(p.code) = CHAR_LENGTH(v_prefix) + 6;

    SELECT COUNT(*) AS products_without_three_on_sale_offers
    FROM (
        SELECT p.id
        FROM product p
        LEFT JOIN product_offer o ON o.product_id = p.id AND o.status = 'ON_SALE'
        WHERE p.code LIKE CONCAT(v_prefix, '%') AND CHAR_LENGTH(p.code) = CHAR_LENGTH(v_prefix) + 6
        GROUP BY p.id
        HAVING COUNT(o.id) <> 3
    ) invalid_products;
END$$
DELIMITER ;

CALL seed_performance_products(@perf_run_id, @perf_product_count, @perf_batch_size);
DROP PROCEDURE seed_performance_products;
