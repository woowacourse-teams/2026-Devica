-- 1TB 를 쓰는데도 용량이 부족한 사용자에게 2TB 까지 권하게 되어 저장 공간 FAQ 를 맞춘다 (#293)
UPDATE faq
SET content    = REPLACE(content,
                         '이런 조건이 겹칠 때 1TB로 올립니다.',
                         '이런 조건이 겹칠 때 1TB로 올립니다. 지금 1TB를 쓰는데도 용량이 자주 부족했고 이런 조건까지 겹치면 2TB를 권합니다.'),
    updated_at = NOW()
WHERE slug = 'backend-laptop-storage';
