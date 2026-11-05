-- M Max 칩 CPU 등급(MAX, 최소 점수 57500)에 해당하는 CPU. 제품·노트북은 등록하지 않는다
-- CPU 점수: PassMark CPU Mark(멀티스레드), 2026-11-05 기준 표본 188개
--   https://www.cpubenchmark.net/cpu.php?cpu=Apple+M5+Max+18+Core&id=7231

-- CPU: apple-m5-max-18-core
INSERT INTO cpu (manufacturer, name, core_count, score, created_at, updated_at)
VALUES (
    'Apple',
    'Apple M5 Max 18-core',
    18,
    57705,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
