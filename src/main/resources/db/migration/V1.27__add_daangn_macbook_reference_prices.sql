-- 서울·경기에서 조사한 미개봉 매물의 판매 희망가 11건. 거래 체결가·현재 재고를 보장하지 않는다.
-- ON_SALE은 검색 가능한 참고가를 기존 통합 최저가에 포함하기 위한 기존 상태값이다.
-- 개별 매물 URL 대신 제품별 검색 결과로 연결하고, 게시일이 아닌 조사일을 저장한다.
START TRANSACTION;

INSERT INTO product_offer
    (product_id, name, price, external_item_id, purchase_url, status, checked_at,
     created_at, updated_at)
-- 모든 제품·사양이 일치하고 기존 당근 오퍼가 없어야 등록한다. 실패 시 첫 INSERT 전체를 중단한다.
SELECT CASE WHEN COUNT(g.id) OVER () = 11 AND COUNT(existing.id) OVER () = 0 THEN p.id END,
       '당근', verified.price, NULL, verified.purchase_url, 'ON_SALE', verified.checked_at,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT 'MDH74KH/A' AS code, 'MacBook Air 13 M5' AS name, 'Apple M5' AS cpu_name, 10 AS cpu_core_count, 'Apple M5 8-core GPU' AS gpu_name,
           16 AS memory_gb, 512 AS storage_gb, 13.6 AS screen_size_inch, 1900000 AS price, '2026-11-04' AS checked_at,
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%EC%97%90%EC%96%B4%2013%20M5%2016GB%20512GB%20%EB%AF%B8%EA%B0%9C%EB%B4%89' AS purchase_url
    UNION ALL
    SELECT 'MDHD4KH/A', 'MacBook Air 13 M5', 'Apple M5', 10, 'Apple M5 10-core GPU',
           24, 1024, 13.6, 2250000, '2026-11-05',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%EC%97%90%EC%96%B4%2013%20M5%2024GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'MDVD4KH/A', 'MacBook Air 15 M5', 'Apple M5', 10, 'Apple M5 10-core GPU',
           16, 512, 15.3, 2050000, '2026-11-04',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%EC%97%90%EC%96%B4%2015%20M5%2016GB%20512GB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'MDVC4KH/A', 'MacBook Air 15 M5', 'Apple M5', 10, 'Apple M5 10-core GPU',
           24, 1024, 15.3, 2800000, '2026-11-05',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%EC%97%90%EC%96%B4%2015%20M5%2024GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'MDE14KH/A', 'MacBook Pro 14 M5', 'Apple M5', 10, 'Apple M5 10-core GPU',
           16, 1024, 14.2, 2800000, '2026-11-04',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2014%20M5%2016GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'Z1KN0001B', 'MacBook Pro 14 M5', 'Apple M5', 10, 'Apple M5 10-core GPU',
           24, 1024, 14.2, 2600000, '2026-11-05',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2014%20M5%2024GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'Z1MH001VY', 'MacBook Pro 14 M5 Pro', 'Apple M5 Pro 15-core', 15, 'Apple M5 Pro 16-core GPU',
           24, 1024, 14.2, 3400000, '2026-11-04',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2014%20M5%20Pro%2024GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'Z1ML001Y3', 'MacBook Pro 14 M5 Pro 48GB', 'Apple M5 Pro 15-core', 15, 'Apple M5 Pro 16-core GPU',
           48, 1024, 14.2, 4150000, '2026-11-04',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2014%20M5%20Pro%2048GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'Z1MM001CH', 'MacBook Pro 14 M5 Pro 48GB 2TB', 'Apple M5 Pro 18-core', 18, 'Apple M5 Pro 20-core GPU',
           48, 2048, 14.2, 5500000, '2026-11-05',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2014%20M5%20Pro%2048GB%202TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'MGE44KH/A', 'MacBook Pro 16 M5 Pro', 'Apple M5 Pro 18-core', 18, 'Apple M5 Pro 20-core GPU',
           24, 1024, 16.2, 3850000, '2026-11-05',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2016%20M5%20Pro%2024GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
    UNION ALL
    SELECT 'MGEC4KH/A', 'MacBook Pro 16 M5 Pro', 'Apple M5 Pro 18-core', 18, 'Apple M5 Pro 20-core GPU',
           48, 1024, 16.2, 4950000, '2026-11-04',
           'https://www.daangn.com/kr/search/buy-sell/?q=%EB%A7%A5%EB%B6%81%20%ED%94%84%EB%A1%9C%2016%20M5%20Pro%2048GB%201TB%20%EB%AF%B8%EA%B0%9C%EB%B4%89'
) verified
LEFT JOIN product p ON p.code = verified.code AND p.brand = 'Apple' AND p.name = verified.name
LEFT JOIN laptop l ON l.id = p.id AND l.os = 'MAC'
    AND l.memory_gb = verified.memory_gb AND l.storage_gb = verified.storage_gb
    AND l.screen_size_inch = verified.screen_size_inch
LEFT JOIN cpu c ON c.id = l.cpu_id AND c.manufacturer = 'Apple'
    AND c.name = verified.cpu_name AND c.core_count = verified.cpu_core_count
LEFT JOIN gpu g ON g.id = l.gpu_id AND g.manufacturer = 'Apple' AND g.name = verified.gpu_name
    AND c.id IS NOT NULL
LEFT JOIN product_offer existing ON existing.product_id = p.id AND existing.name = '당근';

DELETE m FROM product_min_price m
JOIN product p ON p.id = m.product_id
WHERE p.code IN ('MDH74KH/A', 'MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A', 'MDE14KH/A', 'Z1KN0001B', 'Z1MH001VY', 'Z1ML001Y3', 'Z1MM001CH', 'MGE44KH/A', 'MGEC4KH/A');

INSERT INTO product_min_price (product_id, min_price)
SELECT o.product_id, MIN(o.price)
FROM product_offer o
JOIN product p ON p.id = o.product_id
WHERE p.code IN ('MDH74KH/A', 'MDHD4KH/A', 'MDVD4KH/A', 'MDVC4KH/A', 'MDE14KH/A', 'Z1KN0001B', 'Z1MH001VY', 'Z1ML001Y3', 'Z1MM001CH', 'MGE44KH/A', 'MGEC4KH/A')
    AND o.status = 'ON_SALE'
GROUP BY o.product_id;

COMMIT;
