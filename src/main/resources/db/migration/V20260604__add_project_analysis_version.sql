-- 완료 콜백 멱등성 보호용 낙관적 락 컬럼.
-- 동시 중복 웹훅이 같은 analysis를 두 번 종료 전이 → 배치 카운터 이중 감소 → 조기/불완전 핸드오프를 막는다.
-- 기존 행은 0으로 초기화(NOT NULL DEFAULT 0 → 무중단 적용).
ALTER TABLE project_analysis ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
