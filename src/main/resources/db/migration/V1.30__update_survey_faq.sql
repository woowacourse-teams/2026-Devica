-- 지원하는 사용 목적과 설문·기본 권장 사양 화면의 동작이 바뀌어 공통 FAQ 답변을 맞춘다 (#442)
UPDATE faq
SET content    = '노트북을 지원합니다. 사용 목적은 백엔드·프론트엔드·모바일 개발과 디자인 작업 중에서 고를 수 있습니다.
다른 제품은 준비 중입니다.',
    updated_at = NOW()
WHERE slug = 'supported-products';

UPDATE faq
SET content    = '제품과 사용 목적을 고르면 기본 권장 사양이 나옵니다. 항목을 누르면 그 값을 권하는 이유를 볼 수 있습니다.
- 바로 제품을 보려면 "기본 권장 사양으로 제품 보기"를 누르세요. 기본 권장 사양에 맞는 제품 목록이 바로 열립니다.
- 내 상황에 맞추려면 "내 작업 환경에 맞게 조정하기"를 누르세요. 답변을 반영해 사양을 올리거나 내립니다.',
    updated_at = NOW()
WHERE slug = 'how-to-get-recommendation';

UPDATE faq
SET content    = '지금 쓰는 노트북 사양은 건너뛸 수 있고, 나머지 질문은 답해야 다음으로 넘어갑니다.
확실하지 않은 질문은 ''잘 모르겠다''나 가장 가까운 답을 고르세요.',
    updated_at = NOW()
WHERE slug = 'skip-questions';
