-- 추천순 정렬을 없애 추천순 기준을 설명하는 FAQ 를 지운다 (#326)
DELETE
FROM faq
WHERE slug = 'recommended-sort';
