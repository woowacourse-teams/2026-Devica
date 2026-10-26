# 기존 쿠팡 판매상품의 기준 ID 준비

가격 최신화의 첫 단계는 기존 `ProductOffer.externalItemId`에 쿠팡 판매 옵션의 `vendorItemId`를 보관하는 것이다.
새 클래스·테이블·컬럼은 추가하지 않는다. 이번 단계에는 쿠팡 API 호출이 필요 없다.

## 이미 저장된 데이터

`V1.6__seed_prototype_products.sql`은 기존 노트북 20개의 쿠팡 URL과 `external_item_id`를 함께 등록한다.
20개 모두 `external_item_id`가 URL의 `vendorItemId`와 같고, 서로 중복되지 않는다.
따라서 시드 그대로 적용된 DB에는 이미 기준 ID가 채워져 있다. 기존 시드나 가격을 다시 쓰는 마이그레이션은 필요 없다.
이는 저장된 데이터의 일치 여부이며, 현재 쿠팡의 가격·판매 상태·실제 선택 옵션을 확인한 결과는 아니다.

```text
purchaseUrl
  https://www.coupang.com/vp/products/9410641464?itemId=27961674147&vendorItemId=94919713475
                                                                      └─ 기준 ID
ProductOffer.externalItemId = "94919713475"
```

- `ProductOffer.id`: 우리 DB의 판매상품 ID
- `ProductOffer.productId`: 우리 DB의 제품 ID
- `ProductOffer.externalItemId`: 판매처의 판매 옵션 ID. 쿠팡에서는 `vendorItemId`
- 쿠팡 응답의 `productId` 및 URL의 `itemId`: 기준 ID와 다른 값

20개 상품별 ID와 URL은 [초기 제품 가격 수집 대상](제품-가격-수집-대상.md)에 정리되어 있다.

## 개발·운영 DB 확인

이번 작업에서는 dev/prod에 접속하거나 값을 변경하지 않았다. 아래 조회로 실제 저장값을 확인한다.

```sql
SELECT p.code, o.id AS offer_id, o.external_item_id AS vendor_item_id, o.purchase_url
FROM product_offer o
JOIN product p ON p.id = o.product_id
JOIN laptop l ON l.id = p.id
WHERE o.name = '쿠팡'
ORDER BY p.code;
```

각 행의 `vendor_item_id`와 URL의 `vendorItemId`가 일치하면 1단계 준비가 된 것이다.
실제 DB에 빈 값이 있으면 저장된 유효한 URL에서 추출해 보충한다. 비어 있지 않은 값이 URL과 다르면
자동으로 덮어쓰지 않고 기존 연결부터 확인한다. 단축 URL이나 ID가 없는 URL에서는 다른 ID로 추측하지 않는다.
실제 DB에 누락이 확인되기 전에는 보충용 마이그레이션이나 실행 서비스를 추가하지 않는다.

## 검증과 다음 단계

`FlywayMigrationTest`는 테스트 전용 DB에 마이그레이션을 적용하고 다음을 검증한다.

- 기존 노트북의 쿠팡 판매상품이 20개인지
- 제품 코드와 판매 옵션 ID가 중복되지 않는지
- 모든 URL을 파싱할 수 있고, 저장된 ID가 비어 있지 않으며 URL의 `vendorItemId`와 같은지

```bash
./gradlew test --tests 'com.wrb.devica.common.FlywayMigrationTest' \
  --tests 'com.wrb.devica.product.infra.coupang.CoupangProductUrlTest'
```

기존 `CoupangProductUrl`은 URL에서 ID를 추출하고, `coupangPriceProbe`는 API 응답을 수동으로 확인하는 데 사용한다.
다음 단계에서 저장된 기준 ID와 응답의 ID를 대조한 뒤 가격·확인 날짜를 갱신한다.
일치하지 않는 후보나 검색 결과 누락만으로 품절·상품 변경을 확정하지 않는다. 숨김·삭제 정책은 다음 단계에서 결정한다.
기존 승인 필드·클래스·승인 전용 테스트·미적용 V1.14는 제거했으며, 신규 제품 수집과 등록은 미룬다.
