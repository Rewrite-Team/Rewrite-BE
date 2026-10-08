-- 앱과 모든 DB 접속을 종료하고 파일을 백업한 뒤 H2 2.x RunScript로 한 번만 실행한다.
-- 오류 후 계속 실행하는 옵션을 사용하지 않는다. H2 DDL은 자동 커밋되므로 README.md의 복구 절차를 따른다.

-- DDL 전에 모든 기존 값을 검증한다. SIGNAL은 해당 행이 있을 때만 오류를 발생시킨다.
-- Java 정규식의 절대 시작/끝을 사용해 공백·줄바꿈·앞자리 0도 거부한다.
SELECT SIGNAL('45000', 'review_versions.version must use canonical positive v0.N format: ' || id)
FROM review_versions
WHERE version IS NULL OR NOT REGEXP_LIKE(version, '\Av0[.][1-9][0-9]*\z');

SELECT SIGNAL('45000', 'review_versions.version exceeds the positive bigint range: ' || id)
FROM review_versions
WHERE CAST(SUBSTRING(version FROM 4) AS DECIMAL(38, 0)) NOT BETWEEN 1 AND 9223372036854775807;

SELECT CASE WHEN EXISTS (
    SELECT 1 FROM review_versions
    GROUP BY cover_letter_id, CAST(SUBSTRING(version FROM 4) AS BIGINT)
    HAVING COUNT(*) > 1
) THEN SIGNAL('45000', 'review_versions contains duplicate version numbers for a cover letter')
ELSE NULL END;

ALTER TABLE review_versions ADD COLUMN version_number BIGINT;
UPDATE review_versions SET version_number = CAST(SUBSTRING(version FROM 4) AS BIGINT);
ALTER TABLE review_versions ALTER COLUMN version_number SET NOT NULL;
ALTER TABLE review_versions ADD CONSTRAINT ck_review_versions_version_number_positive
    CHECK (version_number > 0);
ALTER TABLE review_versions ADD CONSTRAINT uk_review_versions_cover_letter_version_number
    UNIQUE (cover_letter_id, version_number);

-- 새 값과 제약이 모두 적용된 뒤 기존 문자열 열을 제거한다. 참조 무결성은 계속 활성화한다.
ALTER TABLE review_versions DROP CONSTRAINT IF EXISTS uk_review_versions_cover_letter_version;
ALTER TABLE review_versions DROP COLUMN version;
