# 쿠팡 API 가격 조회 검증

검색 응답에서 기존 판매상품의 옵션 식별자와 가격을 확보할 수 있는지 확인하는 수동 도구다.
이 명령은 Spring 서버와 DB를 기동하지 않는다. 가격 변경, 판매 상태 변경, 일일 스케줄은 실행하지 않는다.
현재 1단계인 기존 20개 상품의 기준 ID 준비는 [판매상품 ID 준비](쿠팡_판매상품_ID_준비.md)를 참고한다.

## 키 설정

### 로컬

저장소 루트에서 `coupang.local.env.example`을 `coupang.local.env`로 복사하고 두 값을 입력한다.
실제 키 파일은 `.gitignore`의 `*.env` 규칙으로 제외된다.

```dotenv
COUPANG_ACCESS_KEY=발급받은_ACCESS_KEY
COUPANG_SECRET_KEY=발급받은_SECRET_KEY
```

`KEY=value` 또는 `KEY="value"` 형식을 사용한다. 줄 끝 주석이나 셸 변수 치환은 지원하지 않는다.
이 도구는 두 키만 읽고 파일의 내용을 셸 명령으로 실행하지 않는다.
같은 이름의 환경변수가 설정되어 있으면 파일보다 우선한다. 키를 명령행 인자로 전달하지 않는다.

### 개발·운영 서버

각 서버의 `/opt/app/.env`에 같은 두 환경변수를 저장한다. 규격은 `.env.server.example`에 있다.
`docker-compose.server.yml`의 `env_file: .env`가 앱 컨테이너로 전달한다.
`.env`를 수정해도 이미 실행 중인 컨테이너의 환경변수는 바뀌지 않으므로 반영 시 컨테이너 재생성이 필요하다.
이번 단계의 수동 명령은 Gradle 실행 환경에서 사용한다. 현재 앱 컨테이너에 자동 수집 기능이 추가되는 것은 아니다.
이후 DB 가격 갱신 기능이 구현되면 매일 가격을 변경하기 위한 재배포는 필요하지 않다.

키는 일반 앱 실행이나 모의 응답 테스트에 필요하지 않다.

## 실행

```bash
./gradlew coupangPriceProbe --args='--env-file=coupang.local.env'
```

환경변수로 이미 설정했다면 `--env-file`을 생략한다.

기본 대상은 `src/main/resources/coupang/price-probe-targets.json`의 4개다.
`V1.6__seed_prototype_products.sql`의 최초 등록값에서 가져왔으며, 현재 운영 DB를 읽은 값이 아니다.

| 제품 코드 | 선정 이유 |
|---|---|
| MDH74KH/A | 같은 페이지의 16GB·512GB 옵션 |
| MDHD4KH/A | 같은 페이지의 24GB·1TB 옵션 |
| Z1MM001CH | CPU·RAM·SSD 선택이 여러 개인 MacBook Pro |
| NT760VJT-A72A | 별도 페이지의 Windows 노트북 |

명령은 대상마다 키워드 검색을 1회 실행한다. 검색 결과는 최대 10개이며 전체 카탈로그가 아니다.
연속 요청 사이에 2.1초를 두고, 연결 5초·응답 10초의 타임아웃을 적용한다.
인증 실패 또는 HTTP 429가 발생하면 남은 대상은 `NOT_ATTEMPTED`로 기록하고 호출을 중단한다.
검증 단계에서는 자동 재시도하지 않는다. 같은 계정의 다른 실행과 겹치지 않게 사용한다.

## 보고서 확인

기본 출력은 Git에서 제외되는 `build/reports/coupang-price-probe-<UTC 실행시각>.json`이다.
검색 응답 중 상품 ID·상품명·가격·상품 URL만 기록하며 인증 헤더·키·응답 원문 전체는 기록하지 않는다.

| 필드 | 의미 |
|---|---|
| `target` | 검색어, 기대 사양과 기존 구매 URL |
| `observedAt` | 해당 요청을 처리한 UTC 시각. 가격 검증 완료 시각이 아님 |
| `outcome` | `RETRIEVED`, `NO_SEARCH_RESULTS`, `API_FAILURE`, `NOT_ATTEMPTED` |
| `failureReason` | 인증·호출 제한·연결·HTTP·응답 형식 오류 구분 |
| `candidates[].response` | API가 반환한 상품 ID, 상품명, 가격, URL |
| `candidates[].urlIdentifiers` | URL에서 추출한 `pageId`, `itemId`, `vendorItemId` |
| `candidates[].optionMatch` | 기존 URL과의 옵션 ID 비교 결과 |
| `candidates[].priceCheck` | 가격 누락·0 이하 여부, 또는 실제 가격 검증이 필요함을 표시 |

`MATCHED_OPTION_IDS`는 `itemId`와 `vendorItemId`가 모두 같다는 뜻이다.
API 응답의 `productId`와 URL의 `pageKey`는 별도로 기록하고 서로 같다고 가정하지 않는다.
페이지 ID만 같거나 상품명에 기대 사양이 있어도 옵션이 같다고 판단하지 않는다.
ID가 없으면 `INSUFFICIENT_IDENTIFIERS`, 다르면 `DIFFERENT_OPTION`, 해석할 수 없는 URL은 `INVALID_PRODUCT_URL`이다.
상품 URL을 추가 요청하거나 리다이렉트로 따라가지 않는다.

**옵션 ID 일치는 동일 사양·정확한 옵션 가격·일반 판매가 검증 완료를 뜻하지 않는다.**
보고서의 `verificationStatus`는 항상 `MANUAL_VERIFICATION_REQUIRED`이고, `databaseModified`는 `false`다.
가격이 양수여도 `REQUIRES_PRICE_VERIFICATION`으로 남긴다. 상품명에서 사양을 자동 확정하지 않는다.
검색 결과 누락을 품절로 취급하지 않는다.

정상 조회가 끝나면 종료 코드 0, 입력·설정·파일 오류는 1, API 실패가 있으면 보고서를 남기고 2로 종료한다.
종료 코드 0도 가격 자동 갱신이 가능하다는 판정은 아니다.

## 실제 응답 검증 절차

1. 후보의 `itemId`와 `vendorItemId`가 기존 구매 URL과 같은지 확인한다.
2. 실제 선택된 옵션이 기대 모델·CPU·RAM·SSD·OS와 맞는지 확인한다.
3. API의 `productPrice`가 그 옵션의 가격인지 확인한다.
4. [가격 기준](제품-가격-수집-대상.md)에 따라 카드·회원·별도 쿠폰 조건이 없는 일반 판매가인지 확인한다.
5. 확정 근거가 부족하면 갱신 가능으로 승인하지 않고, 확인 불가 이유를 기록한다.

실제 키로 실행한 보고서와 페이지 대조가 있어야 위 절차의 완료를 말할 수 있다.
모의 응답 테스트 통과는 실제 API의 옵션별 가격 재조회 지원을 증명하지 않는다.

검색어를 바꾸거나 대상을 최대 20개로 늘릴 때는 기본 JSON을 복사해 수정하고 별도 경로로 전달한다.
모델 코드를 포함한 검색이 결과를 반환하지 않을 경우 모델명·사양 중심의 검색어도 별도 실행으로 비교한다.

```bash
./gradlew coupangPriceProbe --args='--env-file=coupang.local.env --targets=build/coupang-targets.json'
```

필요하면 `--output=build/reports/coupang-probe.json`으로 출력 위치를 지정한다. 같은 경로는 덮어쓴다.

## 모의 응답 테스트

```bash
./gradlew test --tests 'com.wrb.devica.product.infra.coupang.*'
```

외부 API와 DB 없이 서명, 한글 검색어 인코딩, 응답 역직렬화, 오류 분류, 옵션 ID 충돌·누락 및 검증 보류를 확인한다.
공식 계약 확인 출처는 [파트너스 API 문서](https://partners.coupang.com/#help/open-api)의 검색 응답과 HMAC 가이드다.

## 다음 단계: 기존 판매상품의 가격 갱신

기존 `ProductOffer.externalItemId`에 저장된 `vendorItemId`를 기준으로 응답의 판매 옵션을 찾고,
일치하는 옵션의 가격과 확인 날짜를 갱신하는 처리는 다음 단계에서 구현한다.
현재 보고서의 `compareOption`은 수동 조회 검증을 위해 `itemId`와 `vendorItemId`를 모두 비교한다.
이 결과를 바로 서비스의 가격 갱신·숨김·삭제 판단으로 사용하지 않는다.
검색 결과 누락이나 다른 후보의 반환만으로 기존 상품이 품절되거나 다른 상품으로 바뀌었다고 확정할 수 없다.
사양 기반 신규 제품 검색·등록과 최초 승인 저장 기능은 현재 구현 범위에서 제외한다.
