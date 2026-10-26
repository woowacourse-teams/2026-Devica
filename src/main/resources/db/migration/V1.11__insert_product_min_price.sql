-- 기존 product_offer로 제품별 최저가를 채운다. 판매 중인 오퍼가 없는 제품은 행을 두지 않는다
INSERT INTO product_min_price (product_id, min_price)
SELECT product_id, MIN(price)
FROM product_offer
WHERE status = 'ON_SALE'
GROUP BY product_id;
