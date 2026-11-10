-- 개발 DB에 연결한 MySQL 8.4 전용 세션에서 실행한다.
-- 배치마다 따로 커밋한다. SQL 오류가 나면 멈추고, mysql --force는 쓰지 않는다.
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
    DECLARE v_mac_cpu_count INT;
    DECLARE v_windows_cpu_count INT;
    DECLARE v_missing_gpu_tiers TEXT;
    DECLARE v_missing_tiers TEXT;
    DECLARE v_start INT DEFAULT 1;
    DECLARE v_batch_count INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS perf_seed_numbers;
        DROP TEMPORARY TABLE IF EXISTS perf_seed_cpus;
        DROP TEMPORARY TABLE IF EXISTS perf_seed_gpus;
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

    IF v_category_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'LAPTOP category is required';
    END IF;
    IF EXISTS (
        SELECT 1 FROM product
        WHERE code LIKE CONCAT(v_prefix, '%') AND CHAR_LENGTH(code) = CHAR_LENGTH(v_prefix) + 6
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Products for this run_id already exist; inspect before retrying';
    END IF;

    -- OS별 CPU를 id 순으로 번호를 매겨 둔다. 노트북은 이 번호로 CPU를 돌아가며 받는다.
    CREATE TEMPORARY TABLE perf_seed_cpus (
        os VARCHAR(16) NOT NULL,
        cpu_no INT NOT NULL,
        cpu_id BIGINT NOT NULL,
        score INT NOT NULL,
        PRIMARY KEY (os, cpu_no)
    ) ENGINE = InnoDB;
    INSERT INTO perf_seed_cpus (os, cpu_no, cpu_id, score)
    SELECT os, ROW_NUMBER() OVER (PARTITION BY os ORDER BY id) - 1, id, score
    FROM (
        SELECT IF(manufacturer = 'Apple', 'MAC', 'WINDOWS') AS os, id, score
        FROM cpu
        WHERE manufacturer IN ('Apple', 'Intel', 'AMD')
    ) os_cpus;

    SELECT COUNT(IF(os = 'MAC', 1, NULL)), COUNT(IF(os = 'WINDOWS', 1, NULL))
    INTO v_mac_cpu_count, v_windows_cpu_count
    FROM perf_seed_cpus;

    -- 등급 점수 기준은 CpuTier.java의 minScore와 같아야 한다. 한쪽을 고치면 함께 고친다.
    -- 등급마다 그 점수 이상인 CPU가 있어야 목록 API의 CPU 필터가 0건을 내지 않는다.
    SELECT GROUP_CONCAT(tiers.tier ORDER BY tiers.os, tiers.min_score)
    INTO v_missing_tiers
    FROM (
        SELECT 'MAC' AS os, 'BASIC' AS tier, 20000 AS min_score
        UNION ALL SELECT 'MAC', 'PRO', 30000
        UNION ALL SELECT 'MAC', 'MAX', 57500
        UNION ALL SELECT 'WINDOWS', 'U', 15000
        UNION ALL SELECT 'WINDOWS', 'P_HS', 20000
        UNION ALL SELECT 'WINDOWS', 'H', 24000
        UNION ALL SELECT 'WINDOWS', 'HX', 35000
    ) tiers
    WHERE NOT EXISTS (
        SELECT 1 FROM perf_seed_cpus c WHERE c.os = tiers.os AND c.score >= tiers.min_score
    );

    IF v_missing_tiers IS NOT NULL THEN
        SET v_missing_tiers = LEFT(CONCAT('No CPU reaches CPU tiers: ', v_missing_tiers), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_missing_tiers;
    END IF;

    -- GPU 등급마다 대표 GPU를 하나씩 고른다. 등급 점수 기준은 GpuTier.java의 minScore와 같아야 한다.
    -- Mac은 칩과 GPU가 한 몸이라 CPU 등급으로 GPU가 정해지고(slot 0: M 칩, 1: M Pro 칩 이상),
    -- Windows는 CPU와 상관없이 slot 0: 내장, 1: RTX xx50급, 2: RTX xx60급을 고르게 나눈다.
    CREATE TEMPORARY TABLE perf_seed_gpus (
        os VARCHAR(16) NOT NULL,
        slot INT NOT NULL,
        gpu_id BIGINT,
        PRIMARY KEY (os, slot)
    ) ENGINE = InnoDB;
    INSERT INTO perf_seed_gpus (os, slot, gpu_id)
    SELECT slots.os, slots.slot,
           (SELECT MIN(g.id) FROM gpu g
            WHERE (g.manufacturer = 'Apple') = (slots.os = 'MAC')
              AND g.score >= slots.min_score AND g.score < slots.max_score)
    FROM (
        SELECT 'MAC' AS os, 0 AS slot, 0 AS min_score, 8000 AS max_score
        UNION ALL SELECT 'MAC', 1, 8000, 2147483647
        UNION ALL SELECT 'WINDOWS', 0, 0, 6000
        UNION ALL SELECT 'WINDOWS', 1, 6000, 9500
        UNION ALL SELECT 'WINDOWS', 2, 9500, 2147483647
    ) slots;

    SELECT GROUP_CONCAT(CONCAT(os, ' slot ', slot) ORDER BY os, slot)
    INTO v_missing_gpu_tiers
    FROM perf_seed_gpus
    WHERE gpu_id IS NULL;

    IF v_missing_gpu_tiers IS NOT NULL THEN
        SET v_missing_gpu_tiers = LEFT(CONCAT('No GPU for GPU tiers: ', v_missing_gpu_tiers), 128);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_missing_gpu_tiers;
    END IF;

    -- 번호는 최대 1000개만 만들어 재사용해 재귀 깊이를 기본 제한 안에 둔다.
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

        -- 메모리(5개)와 스토리지(4개)는 제품 5개 묶음 순번 k = FLOOR(i / 5)마다 한 칸씩 돈다.
        -- 연속한 k 20개(lcm(5, 4))에서 OS·브랜드별로 메모리×스토리지 20조합이 한 번씩 나오므로,
        -- CPU를 k 20개(제품 100개) 단위로 바꾸면 CPU마다 같은 조합 분포를 받는다.
        -- Windows의 GPU 등급(3개)과 색 영역(2개)은 위 순번과 겹치지 않도록 제품 코드의 CRC32로 나눈다.
        -- Mac은 모두 넓은 색 영역이다.
        INSERT INTO laptop (id, cpu_id, os, memory_gb, storage_gb, weight_g, screen_size_inch, color_gamut, gpu_id)
        SELECT p.id,
               c.cpu_id,
               c.os,
               ELT(MOD(MOD(v_start + s.n - 1, 5) + FLOOR((v_start + s.n - 1) / 5), 5) + 1,
                   8, 16, 24, 32, 64),
               ELT(MOD(v_start + s.n - 1, 4) + 1, 256, 512, 1024, 2048),
               ELT(MOD(v_start + s.n - 1, 4) + 1, 1150, 1350, 1550, 1750),
               ELT(MOD(v_start + s.n - 1, 5) + 1, 13.6, 15.6, 14.0, 16.0, 15.6),
               IF(c.os = 'MAC' OR MOD(FLOOR(CRC32(p.code) / 3), 2) = 1, 'WIDE', 'STANDARD'),
               g.gpu_id
        FROM perf_seed_numbers s
        JOIN product p ON p.code = CONCAT(v_prefix, LPAD(v_start + s.n, 6, '0'))
        JOIN perf_seed_cpus c
          ON c.os = IF(MOD(v_start + s.n - 1, 5) = 0, 'MAC', 'WINDOWS')
         AND c.cpu_no = MOD(FLOOR((v_start + s.n - 1) / 100),
                            IF(MOD(v_start + s.n - 1, 5) = 0, v_mac_cpu_count, v_windows_cpu_count))
        JOIN perf_seed_gpus g
          ON g.os = c.os
         AND g.slot = IF(c.os = 'MAC', c.score >= 30000, MOD(CRC32(p.code), 3))
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

        -- 제품 목록은 product_offer를 직접 읽지 않고, product_offer로 계산해 둔 product_min_price를 읽으므로 함께 채운다
        INSERT INTO product_min_price (product_id, min_price)
        SELECT p.id, MIN(o.price)
        FROM perf_seed_numbers s
        JOIN product p ON p.code = CONCAT(v_prefix, LPAD(v_start + s.n, 6, '0'))
        JOIN product_offer o ON o.product_id = p.id AND o.status = 'ON_SALE'
        WHERE s.n < v_batch_count
        GROUP BY p.id;

        COMMIT;
        SET v_start = v_start + v_batch_count;
    END WHILE;

    DROP TEMPORARY TABLE perf_seed_numbers;
    DROP TEMPORARY TABLE perf_seed_cpus;
    DROP TEMPORARY TABLE perf_seed_gpus;

    SELECT COUNT(DISTINCT p.id) AS products, COUNT(DISTINCT l.id) AS laptops,
           COUNT(o.id) AS on_sale_offers, COUNT(DISTINCT m.product_id) AS min_prices
    FROM product p
    LEFT JOIN laptop l ON l.id = p.id
    LEFT JOIN product_offer o ON o.product_id = p.id AND o.status = 'ON_SALE'
    LEFT JOIN product_min_price m ON m.product_id = p.id
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
