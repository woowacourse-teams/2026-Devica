-- 목록 최저가에 품절 판매처의 마지막 확인 가격을 포함하도록 모든 제품의 최저가를 다시 계산한다.
-- 애플리케이션의 ProductMinPriceRepository와 같은 기준이다. 숨김·단종 판매처는 계속 제외한다.
-- 판매 중이거나 품절인 판매처가 없는 제품은 최저가 행을 두지 않는다.
START TRANSACTION;

DELETE FROM product_min_price;

INSERT INTO product_min_price (product_id, min_price)
SELECT product_id, MIN(price)
FROM product_offer
WHERE status IN ('ON_SALE', 'SOLD_OUT')
GROUP BY product_id;

COMMIT;
