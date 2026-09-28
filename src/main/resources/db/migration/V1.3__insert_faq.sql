INSERT INTO faq (usage_purpose_id, slug, title, content, published, display_order, created_at, updated_at)
SELECT NULL,
       f.slug,
       f.title,
       f.content,
       TRUE,
       f.display_order,
       NOW(),
       NOW()
FROM (SELECT 1                          AS display_order,
             'what-is-devica'           AS slug,
             'Devica는 어떤 서비스인가요?'       AS title,
             '제품 유형과 사용 목적을 고르면 필요한 사양을 알려 드리고, 그 사양을 충족하는 제품을 찾아 드리는 서비스입니다.
Devica는 제품을 직접 판매하지 않습니다.' AS content
      UNION ALL
      SELECT 2,
             'supported-products',
             '어떤 제품과 사용 목적을 지원하나요?',
             '지금은 노트북과 백엔드 개발용만 지원합니다.
다른 제품과 사용 목적은 준비 중입니다.'
      UNION ALL
      SELECT 3,
             'how-to-get-recommendation',
             '권장 사양은 어떻게 받나요?',
             '제품과 사용 목적을 고르면 기본 권장 사양이 나옵니다.
- 바로 제품을 보려면 "기본 권장 사양으로 제품 보기"를 누르세요.
- 내 상황에 맞추려면 "질문에 답하기"를 누르세요. 답변을 반영해 사양을 올리거나 내립니다.'
      UNION ALL
      SELECT 4,
             'skip-questions',
             '질문에 모두 답해야 하나요?',
             '아니요. 모든 질문은 선택 사항이라 건너뛸 수 있습니다.
답하지 않은 질문은 사양 계산에 반영하지 않으므로, 모두 건너뛰면 기본 권장 사양이 그대로 나옵니다.'
      UNION ALL
      SELECT 5,
             'mac-and-windows-results',
             '결과에 Mac과 Windows가 함께 나오는 이유는 무엇인가요?',
             '생각하는 OS를 고르지 않으면 두 OS의 권장 사양을 모두 보여 드립니다.
질문에서 Mac이나 Windows를 고르면 그 OS의 권장 사양만 나옵니다.'
      UNION ALL
      SELECT 6,
             'how-search-works',
             '권장 사양으로 검색하면 어떤 제품이 나오나요?',
             'OS가 같고 CPU·메모리·저장 공간이 권장 사양 이상인 제품만 나옵니다.
권장 사양보다 높은 제품도 함께 나옵니다. 예를 들어 권장 메모리가 24GB라면 32GB 제품도 포함됩니다.'
      UNION ALL
      SELECT 7,
             'no-matching-products',
             '조건에 맞는 제품이 없다고 나와요.',
             '검색어나 가격·브랜드 같은 검색 조건을 줄여 보세요. 검색 조건은 함께 적용되고, 직접 풀기 전까지 자동으로 해제되지 않습니다.
그래도 없다면 질문으로 돌아가 답변을 다시 확인해 보세요.'
      UNION ALL
      SELECT 8,
             'recommended-sort',
             '추천순은 어떤 기준으로 정렬하나요?',
             '사양이 높은 순입니다. CPU 점수를 먼저 보고, 같으면 메모리, 그다음 저장 공간 순으로 비교합니다.
가격은 보지 않습니다.
Mac과 Windows 권장 사양을 함께 볼 때는 두 목록의 기준을 맞출 수 없어 가격순으로만 정렬합니다.'
      UNION ALL
      SELECT 9,
             'price-basis',
             '목록에 보이는 가격은 어떤 기준인가요?',
             '판매 중인 구매처 가격 중 가장 낮은 값입니다. 가격순 정렬도 이 값을 기준으로 합니다.
구매처의 실제 가격과 다를 수 있으니 결제 전에 구매처에서 확인해 주세요.'
      UNION ALL
      SELECT 10,
             'no-price',
             '"가격 정보 없음"은 무엇인가요?',
             '지금 판매 중인 구매처가 없는 제품입니다.
사양을 비교할 수 있도록 목록과 상세에는 보여 드립니다. 다만 가격 조건을 걸면 결과에서 빠지고, 가격순에서는 맨 뒤에 놓입니다.'
      UNION ALL
      SELECT 11,
             'how-to-purchase',
             '제품은 어디서 구매하나요?',
             '제품 상세의 구매처를 누르면 외부 구매처로 이동합니다.
결제·배송·교환·환불은 해당 구매처의 정책을 따릅니다.'
      UNION ALL
      SELECT 12,
             'progress-kept',
             'FAQ나 제품 상세를 보고 돌아오면 입력한 내용이 사라지나요?',
             '아니요. 돌아오면 질문 답변과 검색 조건이 그대로 남아 있습니다.') f;

INSERT INTO faq (usage_purpose_id, slug, title, content, published, display_order, created_at, updated_at)
SELECT p.id,
       f.slug,
       f.title,
       f.content,
       TRUE,
       f.display_order,
       NOW(),
       NOW()
FROM usage_purpose p
         JOIN (SELECT 1                                   AS display_order,
                      'backend-laptop-what-to-check'      AS slug,
                      '백엔드 개발용 노트북은 어떤 사양을 봐야 하나요?'       AS title,
                      '메모리, 저장 공간, CPU 세 가지를 보세요.
백엔드 작업에서 실제로 병목이 되고, 구매 후 바꾸기 어려운 항목입니다.
- 메모리: IDE·Docker·데이터베이스·서비스를 동시에 띄워 가장 먼저 모자랍니다. 모자라면 전체가 함께 느려집니다.
- 저장 공간: 컨테이너 이미지와 빌드 캐시가 쓸수록 쌓입니다.
- CPU: 빌드와 테스트를 돌릴 때 대기 시간을 좌우합니다.' AS content
               UNION ALL
               SELECT 2,
                      'backend-laptop-memory',
                      '메모리는 16GB면 충분한가요?',
                      'IDE 하나에 데이터베이스 한두 개를 띄우는 정도라면 16GB로 충분합니다. 그래서 기본 권장 사양이 16GB입니다.
- 운영체제와 브라우저 5~9GB, IDE 1~5GB, Docker 5~6GB를 쓰므로 가벼운 구성도 시작부터 11GB 안팎입니다.
- 가벼운 구성(VS Code + 데이터베이스 두 개)은 12~19GB를 씁니다.

아래 조건이 겹칠수록 24GB, 32GB로 올려야 합니다.
- IntelliJ 같은 JetBrains IDE(인덱싱 중 3~5GB)
- Java·Kotlin·C# 계열
- Docker로 여러 서비스를 한 번에 띄움
- 지금 노트북이 자주 느려지거나 멈춤
- 5년 이상 쓸 계획

서비스 여러 개를 상시 띄운다면 48GB까지 봅니다.
지금 쓰는 양은 docker stats와 작업 관리자(Mac은 활성 상태 보기)에서 확인할 수 있습니다.'
               UNION ALL
               SELECT 3,
                      'backend-laptop-storage',
                      '저장 공간은 256GB로 부족한가요?',
                      '대체로 부족해서 512GB를 기본으로 권합니다.
- 운영체제·Docker·IDE·런타임 설치만으로 60~90GB를 씁니다.
- 프로젝트 서너 개와 Docker 이미지·볼륨, 라이브러리 캐시까지 더하면 100~150GB가 됩니다.
- SSD는 20% 정도 비워 둬야 속도와 수명이 유지되므로, 256GB로는 여유가 50GB 안팎입니다.

Docker로 여러 서비스를 한 번에 띄운다면 1TB를 권합니다. 용량 부족을 자주 겪었거나 5년 이상 쓸 계획이라면, 이런 조건이 겹칠 때 1TB로 올립니다.
원격 서버에서 개발하는 등 로컬에 쌓이는 것이 적다면 256GB도 가능합니다.'
               UNION ALL
               SELECT 4,
                      'backend-laptop-mac-chip',
                      'Mac은 어떤 칩을 골라야 하나요?',
                      '대부분 기본 M 칩으로 충분합니다. 컨테이너 몇 개와 IDE를 함께 쓰는 정도로는 부족함이 드러나지 않습니다.
빌드와 테스트를 자주, 큰 프로젝트로 돌린다면 코어가 두 배 안팎인 Pro 칩이 유리합니다.

Mac은 칩이 메모리 상한을 정합니다.
- 기본 칩: 세대에 따라 최대 16~32GB
- 더 필요하면 Pro 칩 이상
필요한 메모리를 먼저 정하고, 그 용량을 고를 수 있는 가장 저렴한 칩을 고르세요. 등급(기본·Pro·Max)보다 세대(M2→M3→M4→M5)를 먼저 보는 편이 좋습니다.'
               UNION ALL
               SELECT 5,
                      'backend-laptop-windows-cpu',
                      'Windows 노트북은 어떤 CPU를 골라야 하나요?',
                      '기본 권장은 Intel Core Ultra 7 또는 AMD Ryzen 7 이상입니다. 빌드 시간은 성능 코어 수를 따라가는데, 이 등급부터 성능 코어를 넉넉히 기대할 수 있습니다.

모델명 끝의 알파벳도 확인하세요.
- U: 저전력입니다. 가볍지만 긴 빌드에서 성능이 덜 유지됩니다.
- H·HX: 고성능입니다. 대신 발열이 크고 배터리 시간이 짧으며 무겁습니다.
매일 들고 다닌다면 무조건 H 계열이 정답은 아닙니다. 같은 등급이라도 모델마다 코어 구성이 다르니 사양표를 확인하세요.'
               UNION ALL
               SELECT 6,
                      'backend-laptop-mac-or-windows',
                      'Mac과 Windows 중 무엇이 나은가요?',
                      '어느 쪽으로도 백엔드 개발에는 문제가 없습니다. 프레임워크는 OS를 가리지 않고, Docker도 양쪽 모두 리눅스 VM 위에서 돌아갑니다.

Mac이 조금 편한 점
- 백엔드 개발자, 특히 Docker 사용자는 Mac을 더 많이 씁니다. 설치 문서와 문제 해결 자료도 macOS·리눅스 기준이 많습니다.
- 다만 Apple Silicon은 ARM 기반이라, 회사에 x86 전용 이미지가 있다면 먼저 확인하세요.

Windows가 나은 경우
- 같은 예산으로 메모리와 저장 공간을 더 받을 수 있습니다.
- 회사 환경이 Windows이거나, C#/.NET을 다룰 계획이거나, 게임을 합니다.

이미 Windows에 익숙하다면 굳이 바꿀 필요는 없습니다.'
               UNION ALL
               SELECT 7,
                      'backend-laptop-windows-24gb',
                      'Windows 권장 메모리가 24GB인데 24GB 모델이 잘 안 보여요.',
                      'Windows 노트북은 24GB 모델이 드물어 보통 16GB와 32GB 중에 고릅니다.
검색은 권장 사양 이상인 제품을 보여 주므로 32GB 제품이 함께 나옵니다. 24GB가 권장되었다면 16GB 모델로 낮추기보다 32GB 모델을 고르세요.'
               UNION ALL
               SELECT 8,
                      'backend-laptop-budget',
                      '예산이 부족하면 무엇부터 낮춰야 하나요?',
                      '메모리는 마지막까지 지키세요.
- 메모리: 모자라면 작업이 막히고, 대부분 나중에 늘릴 수 없습니다.
- CPU: 낮추면 빌드를 조금 더 기다릴 뿐 하던 작업은 끝납니다.
- 저장 공간: 모자라면 외장 SSD로 보완할 수 있습니다.
아낀 예산은 메모리에 쓰는 편이 낫습니다.'
               UNION ALL
               SELECT 9,
                      'backend-laptop-upgrade-later',
                      '메모리나 저장 공간을 나중에 늘릴 수 있나요?',
                      '- Mac: 둘 다 늘릴 수 없습니다. 구매할 때 정한 용량이 끝입니다.
- Windows: M.2 슬롯이 있는 모델은 SSD를 교체하거나 추가할 수 있습니다. 메모리는 기판에 붙은 모델이 많고, 슬롯이 있어도 증설 비용이 적지 않습니다.
그래서 메모리는 OS와 관계없이 구매 전에 정하는 편이 안전합니다.'
               UNION ALL
               SELECT 10,
                      'backend-laptop-beginner',
                      '개발을 막 시작했는데 어느 정도 사양이 적당한가요?',
                      '기본 권장 사양(메모리 16GB, 저장 공간 512GB, Mac은 기본 M 칩, Windows는 Core Ultra 7·Ryzen 7급)이면 학습에는 충분합니다.
다만 Docker와 여러 서비스를 다루기 시작하면 16GB가 빠듯해집니다. 5년 이상 쓸 계획이라면 24GB 이상을 고려하세요.'
               UNION ALL
               SELECT 11,
                      'backend-laptop-language-ide',
                      '언어나 IDE, AI 도구에 따라 사양이 달라지나요?',
                      '달라집니다.
- Java·Kotlin·C# 계열: 빌드와 실행에 메모리와 CPU를 더 씁니다.
- JetBrains IDE: 인덱싱과 코드 분석에 메모리를 많이 씁니다.
- Cursor 같은 AI 전용 에디터: 메모리를 더 씁니다.
- Node.js·TypeScript: 의존성이 많아 저장 공간을 더 씁니다.
다만 조건 하나만으로 사양을 올리지는 않습니다. 여러 조건이 겹칠 때 올립니다.'
               UNION ALL
               SELECT 12,
                      'backend-laptop-current-laptop',
                      '지금 쓰는 노트북 사양은 왜 묻나요?',
                      '지금 겪는 불편이 어디서 오는지 판단하기 위해서입니다.
- 빌드 대기나 발열이 잦았다면 지금 쓰는 CPU보다 한 단계 위를 권합니다.
- 저장 공간이 256GB 이하인데 용량이 부족했다면, 디스크가 작아서 생긴 문제로 보고 권장 용량을 올리지 않습니다.'
               UNION ALL
               SELECT 13,
                      'backend-laptop-excluded-specs',
                      '그래픽카드, 화면, 배터리는 왜 권장 사양에 없나요?',
                      '백엔드 개발 기준으로 판단하기 어렵거나 사람마다 답이 달라서 제외했습니다.
- 그래픽카드: 백엔드 작업에서 거의 쓰지 않습니다. 로컬에서 LLM을 돌릴 계획이라면 예외입니다.
- 고주사율 화면: 코딩할 때 체감 차이가 크지 않고 취향이 갈립니다.
- 배터리: 제조사 수치는 웹 브라우징 기준이라 빌드할 때는 맞지 않습니다.
- 화면 크기·무게: 외장 모니터를 쓰는지, 들고 다니는지에 따라 답이 정반대입니다.') f
WHERE p.code = 'BACKEND_DEVELOPMENT';
