-- 앱과 worker를 중지하고 백업한 기존 DB에 한 번만 실행한다.
-- psql -v ON_ERROR_STOP=1 -f postgresql.sql을 사용한다. 자세한 절차는 README.md를 따른다.
BEGIN;
LOCK TABLE review_versions IN ACCESS EXCLUSIVE MODE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM review_versions
        WHERE version IS NULL OR version !~ '^v0[.][1-9][0-9]*$'
    ) THEN
        RAISE EXCEPTION 'review_versions.version must use canonical positive v0.N format';
    END IF;

    IF EXISTS (
        SELECT 1 FROM review_versions
        WHERE CAST(SUBSTRING(version FROM 4) AS NUMERIC) NOT BETWEEN 1 AND 9223372036854775807
    ) THEN
        RAISE EXCEPTION 'review_versions.version exceeds the positive bigint range';
    END IF;

    IF EXISTS (
        SELECT 1 FROM review_versions
        GROUP BY cover_letter_id, CAST(SUBSTRING(version FROM 4) AS BIGINT)
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'review_versions contains duplicate version numbers for a cover letter';
    END IF;
END;
$$;

ALTER TABLE review_versions ADD COLUMN version_number BIGINT;
UPDATE review_versions SET version_number = CAST(SUBSTRING(version FROM 4) AS BIGINT);
ALTER TABLE review_versions ALTER COLUMN version_number SET NOT NULL;
ALTER TABLE review_versions ADD CONSTRAINT ck_review_versions_version_number_positive
    CHECK (version_number > 0);
ALTER TABLE review_versions ADD CONSTRAINT uk_review_versions_cover_letter_version_number
    UNIQUE (cover_letter_id, version_number);

-- 새 값과 제약이 모두 적용된 뒤 기존 문자열 열을 제거한다. CASCADE로 참조를 제거하지 않는다.
ALTER TABLE review_versions DROP CONSTRAINT IF EXISTS uk_review_versions_cover_letter_version;
ALTER TABLE review_versions DROP COLUMN version;
COMMIT;
