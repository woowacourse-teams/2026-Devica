SET @snapshot_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'experiment_event'
      AND column_name = 'recommendation_snapshots_json'
);

SET @snapshot_column_ddl = IF(
    @snapshot_column_exists = 0,
    'ALTER TABLE experiment_event ADD COLUMN recommendation_snapshots_json JSON NULL AFTER recommendation_cpu_tier',
    'SELECT 1'
);

PREPARE snapshot_column_statement FROM @snapshot_column_ddl;
EXECUTE snapshot_column_statement;
DEALLOCATE PREPARE snapshot_column_statement;
