-- GitHub App 설치가 삭제된 시각 (S07 후속). security.md 9.8 보관 "설치 삭제 뒤 30일(그 뒤 계정 값을 지움)"(docs PR #15로 반영,
-- 정리 작업은 S15)의 기준이다.
-- 사람이 정함 (2026-11-24, 세션 선택지): '삭제된 지 30일'의 기준 시각은 "deleted_at 컬럼 (Recommended)".
-- V5는 master에 들어가서 고치지 않고 새 버전으로 더한다 ("새 후속 PR로 V6 (Recommended)").
-- updated_at은 삭제 뒤에 늦게 온 installation_repositories removed 웹훅이 바꿀 수 있어 30일 기준으로 쓰지 않는다.

ALTER TABLE studybot.github_installations ADD COLUMN deleted_at TIMESTAMPTZ;

-- V5 시절에 DELETED가 된 행은 삭제 시각이 없다. updated_at으로 채운다. 삭제 뒤에 온 removed 웹훅이 updated_at을 옮겼을 수 있어
-- 근사값이다(실제보다 늦을 수 있어, 보관이 짧아지지 않고 길어지는 쪽으로 틀린다). 다시 돌려도 결과가 같다.
UPDATE studybot.github_installations
SET deleted_at = updated_at
WHERE status = 'DELETED' AND deleted_at IS NULL;

-- 삭제는 되돌리지 않는다. 삭제 시각은 DELETED일 때만, 그리고 늘 있다.
ALTER TABLE studybot.github_installations
	ADD CONSTRAINT github_installations_deleted_at_ck CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL));
