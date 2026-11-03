-- 제품 추천이 화면 색 영역과 GPU 를 권하고 맞춤 제품 목록이 그것과 비교하도록 노트북에 색 영역과 GPU 를 더한다 (#322)
-- 값은 제조사 공식 사양 페이지 기준이다. 공식 수치가 없는 값은 판매처 사양표·리뷰 사양 자료로 채우고 출처를 적는다.
-- 색 영역: DCI-P3 를 대부분(95% 이상) 채우면 WIDE, 아니면 STANDARD
-- GPU: CPU 처럼 gpu 테이블에 두고 점수로 등급을 가린다. vram_gb 는 외장 GPU 만 적는다
-- GPU 점수: 3DMark Steel Nomad Light, Notebookcheck GPU 페이지의 리뷰 평균값이다. 초기값이고 이후 자동 갱신 파이프라인으로 갱신한다
--   RTX 4050(M5·RTX 5060·RTX 5070 Ti 를 함께 실은 비교 차트) https://www.notebookcheck.net/NVIDIA-GeForce-RTX-4050-Laptop-GPU-Benchmarks-and-Specs.675695.0.html
--   M5 Pro https://www.notebookcheck.com/Apple-M5-Pro-20-Core-GPU-Grafikkarte-Benchmarks-und-Spezifikationen.1244912.0.html
--   Arc 140V https://www.notebookcheck.net/Intel-Arc-Graphics-140V-Benchmarks-and-Specs.854991.0.html
--   Graphics 4 Xe3 https://www.notebookcheck.net/Intel-Graphics-4-Xe3-Panther-Lake-Benchmarks-and-Specs.1193658.0.html
--   M5 Pro 20코어는 결과가 1건이다. Radeon 840M 은 결과가 없어 같은 급 내장 GPU 인 Graphics 4 Xe3 보다 조금 낮게 둔 추정값이다
CREATE TABLE gpu
(
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    manufacturer VARCHAR(32)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    vram_gb      INT          NULL,
    score        INT          NOT NULL,
    created_at   DATETIME     NOT NULL,
    updated_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

INSERT INTO gpu (manufacturer, name, vram_gb, score, created_at, updated_at)
VALUES ('Apple', 'Apple M5 8-core GPU', NULL, 4405, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('Apple', 'Apple M5 10-core GPU', NULL, 4864, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('Apple', 'Apple M5 Pro 16-core GPU', NULL, 8600, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('Apple', 'Apple M5 Pro 20-core GPU', NULL, 10018, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('NVIDIA', 'NVIDIA GeForce RTX 5070 Ti Laptop GPU', 12, 17324, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('NVIDIA', 'NVIDIA GeForce RTX 5060 Laptop GPU', 8, 11792, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('NVIDIA', 'NVIDIA GeForce RTX 4050 Laptop GPU', 6, 6206, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('Intel', 'Intel Arc 140V', NULL, 3214, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('Intel', 'Intel Graphics 4 Xe3', NULL, 2285, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
       ('AMD', 'AMD Radeon 840M', NULL, 2000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

ALTER TABLE laptop
    ADD COLUMN color_gamut VARCHAR(16) NULL,
    ADD COLUMN gpu_id      BIGINT      NULL;

-- Mac: 모든 모델이 P3 화면이다. GPU 는 같은 칩이라도 코어 수가 달라 제품 코드로 매핑한다
-- MacBook Air 13 M5 기본형: 8코어 GPU
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Apple M5 8-core GPU')
WHERE id = (SELECT id FROM product WHERE code = 'MDH74KH/A');

-- 나머지 M5 모델: 10코어 GPU. MDHD4KH/A 는 GPU 코어 수가 확인되지 않아 10코어로 추정했다
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Apple M5 10-core GPU')
WHERE id IN (SELECT id FROM product
             WHERE code IN ('MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A', 'MDE14KH/A', 'Z1KN0001B', 'Z1KN0001E'));

-- M5 Pro 15코어 CPU: 16코어 GPU
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Apple M5 Pro 16-core GPU')
WHERE id IN (SELECT id FROM product WHERE code IN ('Z1MH001VY', 'Z1ML001Y3'));

-- M5 Pro 18코어 CPU: 20코어 GPU
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Apple M5 Pro 20-core GPU')
WHERE id IN (SELECT id FROM product WHERE code IN ('Z1MM001CH', 'MGE44KH/A', 'MGEC4KH/A'));

-- Galaxy Book6: Intel Graphics 4 Xe3 내장. 색 영역은 공식 표기가 없어 판매처 사양표(Coolblue, 같은 시리즈 NP760XJG)의 sRGB 100% 기준
UPDATE laptop
SET color_gamut = 'STANDARD',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Intel Graphics 4 Xe3')
WHERE id = (SELECT id FROM product WHERE code = 'NT760VJT-A72A');

-- Galaxy Book6 Ultra: 판매처 상품명 기준 RTX 5060 8GB. 색 영역은 공식 표기가 "DCI-P3 120% 컬러 볼륨"뿐이라 리뷰 사양 자료(NanoReview, HK01)의 DCI-P3 100% 기준
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'NVIDIA GeForce RTX 5060 Laptop GPU')
WHERE id = (SELECT id FROM product WHERE code = 'NT960UJH-X72A');

-- gram Pro AI 16: DCI-P3 99%(Typ.), Intel Arc 140V 내장
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Intel Arc 140V')
WHERE id = (SELECT id FROM product WHERE code = '16Z90TS-GU7WK');

-- gram Pro 16: DCI-P3 99%(Typ.), RTX 4050 6GB
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'NVIDIA GeForce RTX 4050 Laptop GPU')
WHERE id = (SELECT id FROM product WHERE code = '16Z90TR-SD7WK');

-- IdeaPad Pro 5i Gen 11: OLED DCI-P3 100%, Intel Graphics 4 Xe3 내장
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'Intel Graphics 4 Xe3')
WHERE id = (SELECT id FROM product WHERE code = '83SK0009KR');

-- Legion Pro 5i Gen 10: OLED DCI-P3 100%, RTX 5070 Ti 12GB
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'NVIDIA GeForce RTX 5070 Ti Laptop GPU')
WHERE id = (SELECT id FROM product WHERE code = '83LU000UKR');

-- Zenbook 14: OLED DCI-P3 100%, AMD Radeon 840M 내장
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'AMD Radeon 840M')
WHERE id = (SELECT id FROM product WHERE code = 'UM3406GA-QL151W');

-- ROG Strix G16: DCI-P3 100%, RTX 5060 8GB
UPDATE laptop
SET color_gamut = 'WIDE',
    gpu_id      = (SELECT id FROM gpu WHERE name = 'NVIDIA GeForce RTX 5060 Laptop GPU')
WHERE id = (SELECT id FROM product WHERE code = 'G614PM-TS171W');

ALTER TABLE laptop
    MODIFY COLUMN color_gamut VARCHAR(16) NOT NULL,
    MODIFY COLUMN gpu_id BIGINT NOT NULL,
    ADD CONSTRAINT fk_laptop_gpu
        FOREIGN KEY (gpu_id) REFERENCES gpu (id);
