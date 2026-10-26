-- 설문은 recommendation/purposes/*.yml 에서 읽는다 (ADR 0046)
-- question_option 이 question 을 참조하므로 먼저 지운다
DROP TABLE question_option;
DROP TABLE question;
