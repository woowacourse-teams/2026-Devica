INSERT INTO product_category (code)
VALUES ('LAPTOP') AS new
ON DUPLICATE KEY UPDATE code = new.code;

INSERT INTO usage_purpose (product_category_id, code)
SELECT id, 'BACKEND_DEVELOPMENT'
FROM product_category
WHERE code = 'LAPTOP'
ON DUPLICATE KEY UPDATE usage_purpose.code = usage_purpose.code;

-- 질문·선택지는 문구만 담는다. 순서·입력 유형·배타 여부·의존 관계는 애플리케이션의 QuestionCode 가 갖는다.
INSERT INTO question (usage_purpose_id, code, title, description)
SELECT p.id, q.code, q.title, q.description
FROM usage_purpose p
         JOIN (SELECT 'CURRENT_OS'                                  AS code,
                      '지금 쓰는 노트북의 OS는 무엇인가요?'                      AS title,
                      '모든 항목은 선택 사항입니다. 입력하지 않아도 다음으로 넘어갈 수 있어요.' AS description
               UNION ALL
               SELECT 'CURRENT_MAC_CPU', '지금 쓰는 Mac 의 프로세서는 무엇인가요?', NULL
               UNION ALL
               SELECT 'CURRENT_WINDOWS_CPU', '지금 쓰는 노트북의 프로세서는 무엇인가요?', NULL
               UNION ALL
               SELECT 'CURRENT_MEMORY', '지금 쓰는 노트북의 메모리는 얼마인가요?', NULL
               UNION ALL
               SELECT 'CURRENT_STORAGE', '지금 쓰는 노트북의 저장 공간은 얼마인가요?', NULL
               UNION ALL
               SELECT 'PREFERRED_OS', '어떤 노트북을 생각하고 계신가요?', NULL
               UNION ALL
               SELECT 'PROGRAMMING_LANGUAGE', '주로 다루는 언어는 무엇인가요?', '여러 개를 선택할 수 있어요.'
               UNION ALL
               SELECT 'IDE', '어떤 IDE 를 쓰나요?', NULL
               UNION ALL
               SELECT 'AI_CODING_TOOL', 'AI 코딩 도구를 쓰나요?', NULL
               UNION ALL
               SELECT 'DEV_ENVIRONMENT_SETUP', '개발 환경을 어떻게 띄우나요?', NULL
               UNION ALL
               SELECT 'SLOWDOWN', '프로그램을 여러 개 켜두면 멈추거나 껐다 켜야 했나요?', NULL
               UNION ALL
               SELECT 'STORAGE_SHORTAGE', '용량이 부족해서 뭔가를 지우거나 옮긴 적이 있나요?', NULL
               UNION ALL
               SELECT 'BUILD_WAIT', '빌드나 테스트가 끝나기를 기다리는 게 답답했나요?', NULL
               UNION ALL
               SELECT 'OVERHEATING', '개발할 때 노트북이 뜨거워지거나 팬이 크게 도나요?', NULL
               UNION ALL
               SELECT 'USAGE_PERIOD', '이 노트북을 얼마나 쓸 생각인가요?', NULL) q
WHERE p.code = 'BACKEND_DEVELOPMENT'
ON DUPLICATE KEY UPDATE question.title = q.title, question.description = q.description;

INSERT INTO question_option (question_id, code, content, description)
SELECT q.id, o.code, o.content, o.description
FROM question q
         JOIN usage_purpose p ON p.id = q.usage_purpose_id
         JOIN (SELECT 'CURRENT_OS'            AS question_code,
                      'MACOS'                 AS code,
                      'Mac'                   AS content,
                      CAST(NULL AS CHAR(256)) AS description
               UNION ALL SELECT 'CURRENT_OS', 'WINDOWS', 'Windows', NULL
               UNION ALL SELECT 'CURRENT_MAC_CPU', 'BASIC', 'M 칩', NULL
               UNION ALL SELECT 'CURRENT_MAC_CPU', 'PRO', 'M Pro 칩', NULL
               UNION ALL SELECT 'CURRENT_MAC_CPU', 'MAX', 'M Max 칩', NULL
               UNION ALL SELECT 'CURRENT_WINDOWS_CPU', 'U', 'Core Ultra 5 235U / Ryzen AI 5 340급', NULL
               UNION ALL SELECT 'CURRENT_WINDOWS_CPU', 'P_HS', 'Core Ultra 7 258V / Ryzen AI 7 445급', NULL
               UNION ALL SELECT 'CURRENT_WINDOWS_CPU', 'H', 'Core Ultra 7 255H / Ryzen 7 H 260급', NULL
               UNION ALL SELECT 'CURRENT_WINDOWS_CPU', 'HX', 'Core Ultra 9 275HX / Ryzen 9 8940HX급', NULL
               UNION ALL SELECT 'CURRENT_MEMORY', 'GB_8_OR_LESS', '8GB 이하', NULL
               UNION ALL SELECT 'CURRENT_MEMORY', 'GB_16', '16GB', NULL
               UNION ALL SELECT 'CURRENT_MEMORY', 'GB_24', '24GB', NULL
               UNION ALL SELECT 'CURRENT_MEMORY', 'GB_32', '32GB', NULL
               UNION ALL SELECT 'CURRENT_MEMORY', 'GB_64_OR_MORE', '64GB 이상', NULL
               UNION ALL SELECT 'CURRENT_STORAGE', 'GB_256_OR_LESS', '256GB 이하', NULL
               UNION ALL SELECT 'CURRENT_STORAGE', 'UNDER_1TB', '256GB 초과 1TB 미만', NULL
               UNION ALL SELECT 'CURRENT_STORAGE', 'TB_1_OR_MORE', '1TB 이상', NULL
               UNION ALL SELECT 'PREFERRED_OS', 'MACOS', '맥', NULL
               UNION ALL SELECT 'PREFERRED_OS', 'WINDOWS', '윈도우', NULL
               UNION ALL SELECT 'PREFERRED_OS', 'UNDECIDED', '아직 안 정했다',
                                '결과에서 Mac 과 Windows 권장안을 함께 보여드려요.'
               UNION ALL SELECT 'PROGRAMMING_LANGUAGE', 'JAVA_FAMILY', 'Java, Kotlin, C# 등', NULL
               UNION ALL SELECT 'PROGRAMMING_LANGUAGE', 'NODE_TYPESCRIPT', 'Node.js, TypeScript', NULL
               UNION ALL SELECT 'PROGRAMMING_LANGUAGE', 'OTHERS', 'Python, Go, PHP 등', NULL
               UNION ALL SELECT 'PROGRAMMING_LANGUAGE', 'UNDECIDED', '아직 정하지 않았다', NULL
               UNION ALL SELECT 'IDE', 'JETBRAINS', 'IntelliJ, PyCharm 등 JetBrains 제품', NULL
               UNION ALL SELECT 'IDE', 'LIGHT_EDITOR', 'VS Code, Vim, Neovim 등', NULL
               UNION ALL SELECT 'IDE', 'MULTIPLE', '여러 개를 동시에 함께 쓴다', NULL
               UNION ALL SELECT 'IDE', 'UNKNOWN', '아직 모르겠다', NULL
               UNION ALL SELECT 'AI_CODING_TOOL', 'AI_EDITOR', 'Cursor 같은 AI 전용 에디터를 쓴다', NULL
               UNION ALL SELECT 'AI_CODING_TOOL', 'IDE_EXTENSION', '기존 IDE 에 확장으로 붙여 쓴다', NULL
               UNION ALL SELECT 'AI_CODING_TOOL', 'NONE', '안 쓴다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'LOCAL_MANY', '로컬에 직접 설치해서 여러 개 띄운다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'LOCAL_FEW', '로컬에 직접 설치해서 한두 개 띄운다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'DOCKER_MANY', '도커로 여러 개를 한 번에 띄운다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'DOCKER_FEW', '도커로 한두 개 띄운다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'REMOTE', '원격 서버에 접속해서 쓴다', NULL
               UNION ALL SELECT 'DEV_ENVIRONMENT_SETUP', 'NO_EXPERIENCE', '아직 다뤄본 적이 없다', NULL
               UNION ALL SELECT 'SLOWDOWN', 'OFTEN', '자주 그렇다', NULL
               UNION ALL SELECT 'SLOWDOWN', 'SOMETIMES', '가끔 느려진다', NULL
               UNION ALL SELECT 'SLOWDOWN', 'FINE', '괜찮다', NULL
               UNION ALL SELECT 'SLOWDOWN', 'UNKNOWN', '잘 모르겠다', NULL
               UNION ALL SELECT 'STORAGE_SHORTAGE', 'OFTEN', '자주 있다', NULL
               UNION ALL SELECT 'STORAGE_SHORTAGE', 'ONCE_OR_TWICE', '한두 번 있다', NULL
               UNION ALL SELECT 'STORAGE_SHORTAGE', 'NEVER', '없다', NULL
               UNION ALL SELECT 'BUILD_WAIT', 'OFTEN', '자주 답답했다', NULL
               UNION ALL SELECT 'BUILD_WAIT', 'FINE', '참을 만했다 / 신경 안 쓰였다', NULL
               UNION ALL SELECT 'BUILD_WAIT', 'UNKNOWN', '잘 모르겠다', NULL
               UNION ALL SELECT 'OVERHEATING', 'OFTEN', '자주 그렇다', NULL
               UNION ALL SELECT 'OVERHEATING', 'FINE', '가끔 / 별로 없다', NULL
               UNION ALL SELECT 'OVERHEATING', 'UNKNOWN', '잘 모르겠다', NULL
               UNION ALL SELECT 'USAGE_PERIOD', 'TWO_YEARS', '2년 정도 (곧 바꿀 수 있다)', NULL
               UNION ALL SELECT 'USAGE_PERIOD', 'THREE_TO_FOUR_YEARS', '3~4년', NULL
               UNION ALL SELECT 'USAGE_PERIOD', 'FIVE_PLUS_YEARS', '5년 이상 / 오래 쓰고 싶다', NULL
               UNION ALL SELECT 'USAGE_PERIOD', 'UNDECIDED', '아직 안 정했다', NULL) o
              ON o.question_code = q.code
WHERE p.code = 'BACKEND_DEVELOPMENT'
ON DUPLICATE KEY UPDATE question_option.content = o.content, question_option.description = o.description;
