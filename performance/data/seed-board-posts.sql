-- Run in a dedicated MySQL 8.4 session connected to the development database.
-- Each batch commits separately. Stop on SQL errors; do not use mysql --force.
SET @perf_run_id = 'baseline-1';
SET @perf_post_count = 1000000;
SET @perf_batch_size = 1000;
SET @perf_content_chars = 500;

DROP PROCEDURE IF EXISTS seed_performance_board_posts;

DELIMITER $$
CREATE PROCEDURE seed_performance_board_posts(
    IN p_run_id VARCHAR(255),
    IN p_count INT,
    IN p_batch_size INT,
    IN p_content_chars INT
)
BEGIN
    DECLARE v_prefix VARCHAR(35);
    DECLARE v_usage_purpose_id BIGINT;
    DECLARE v_start INT DEFAULT 1;
    DECLARE v_batch_count INT;
    DECLARE v_first_created_at DATETIME;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS perf_board_seed_numbers;
        RESIGNAL;
    END;

    IF p_run_id IS NULL OR NOT REGEXP_LIKE(p_run_id, '^[A-Za-z0-9-]{1,24}$', 'c') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'run_id must be 1-24 letters, digits, or hyphens';
    END IF;
    IF p_count IS NULL OR p_count < 1 OR p_count > 9999999 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'post_count must be between 1 and 9999999';
    END IF;
    IF p_batch_size IS NULL OR p_batch_size < 1 OR p_batch_size > 1000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'batch_size must be between 1 and 1000';
    END IF;
    IF p_content_chars IS NULL OR p_content_chars < 1 OR p_content_chars > 10000 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'content_chars must be between 1 and 10000';
    END IF;

    SET v_prefix = CONCAT('PERF-POST-', UPPER(p_run_id), '-');
    SELECT MIN(up.id) INTO v_usage_purpose_id
    FROM usage_purpose up
    JOIN product_category pc ON pc.id = up.product_category_id
    WHERE pc.code = 'LAPTOP' AND up.code = 'BACKEND_DEVELOPMENT';

    IF v_usage_purpose_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'LAPTOP / BACKEND_DEVELOPMENT usage purpose is required';
    END IF;
    IF EXISTS (
        SELECT 1 FROM board_post
        WHERE usage_purpose_id = v_usage_purpose_id
          AND title LIKE CONCAT(v_prefix, '%')
          AND CHAR_LENGTH(title) = CHAR_LENGTH(v_prefix) + 7
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Posts for this run_id already exist; inspect before retrying';
    END IF;

    -- Reuse at most 1000 offsets even when generating millions of posts.
    CREATE TEMPORARY TABLE perf_board_seed_numbers (n INT NOT NULL PRIMARY KEY) ENGINE = InnoDB;
    INSERT INTO perf_board_seed_numbers (n)
    WITH RECURSIVE numbers(n) AS (
        SELECT 0
        UNION ALL
        SELECT n + 1 FROM numbers WHERE n + 1 < p_batch_size
    )
    SELECT n FROM numbers;

    -- Spread posts one second apart, ending at the start time of this run.
    SET v_first_created_at = TIMESTAMPADD(SECOND, 1 - p_count, CURRENT_TIMESTAMP);
    WHILE v_start <= p_count DO
        SET v_batch_count = LEAST(p_batch_size, p_count - v_start + 1);
        START TRANSACTION;

        INSERT INTO board_post (usage_purpose_id, title, content, created_at, updated_at)
        SELECT v_usage_purpose_id,
               CONCAT(v_prefix, LPAD(v_start + n, 7, '0')),
               RPAD(CONCAT('Performance board post ', UPPER(p_run_id), '-',
                           LPAD(v_start + n, 7, '0'), '. '),
                    p_content_chars, 'Synthetic laptop discussion. '),
               TIMESTAMPADD(SECOND, v_start + n - 1, v_first_created_at),
               TIMESTAMPADD(SECOND, v_start + n - 1, v_first_created_at)
        FROM perf_board_seed_numbers
        WHERE n < v_batch_count
        ORDER BY n;

        COMMIT;
        SET v_start = v_start + v_batch_count;
    END WHILE;

    DROP TEMPORARY TABLE perf_board_seed_numbers;

    SELECT COUNT(*) AS posts,
           MIN(CHAR_LENGTH(content)) AS min_content_chars,
           MAX(CHAR_LENGTH(content)) AS max_content_chars,
           MIN(created_at) AS first_created_at,
           MAX(created_at) AS last_created_at
    FROM board_post
    WHERE usage_purpose_id = v_usage_purpose_id
      AND title LIKE CONCAT(v_prefix, '%')
      AND CHAR_LENGTH(title) = CHAR_LENGTH(v_prefix) + 7;
END$$
DELIMITER ;

CALL seed_performance_board_posts(@perf_run_id, @perf_post_count, @perf_batch_size, @perf_content_chars);
DROP PROCEDURE seed_performance_board_posts;
