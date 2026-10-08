# 첨삭 버전 순번 이관

기존 `review_versions.version` 문자열을 `version_number BIGINT NOT NULL`로 옮기는 일회성 수동 절차다.
신규 DB는 변경된 entity로 생성하므로 이 스크립트를 실행하지 않는다.
기존 DB는 변경된 애플리케이션을 처음 시작하기 전에 이관한다. `ddl-auto: update`는 기존 문자열에서 순번을 추출하지 못한다.
런타임 자동 이관이나 Flyway 실행은 추가하지 않는다. 영속 구조의 기준은 [ERD](../../../docs/erd.md#review_versions)를 따른다.

## 보존·거부 기준

- `v0.1`, `v0.3`, `v0.9`, `v0.10`은 각각 1, 3, 9, 10으로 옮긴다. 빈 번호를 채우거나 다시 번호를 매기지 않는다.
- 정상 형식은 `v0.` 뒤에 앞자리 0 없는 양수가 오는 문자열이다. `null`, 빈 문자열, 공백·줄바꿈, `v0.0`, `v0.-1`, `v0.01`, 다른 접두사·소수 형식은 거부한다.
- 순번은 Java `long`과 DB `BIGINT`의 양수 범위인 1~9223372036854775807 안에 있어야 한다. 형식·숫자 변환·범위를 검증할 수 없으면 중단한다.
- 같은 자기소개서 안에서 중복된 숫자 순번은 거부한다. 서로 다른 자기소개서의 같은 순번은 허용한다.
- 버전 ID·자기소개서 ID·Job 연결·요구사항·생성 시각과 모든 결과·면접 참조를 유지한다. `cover_letters.latest_review_version_id`와 Job의 입력·결과 참조 ID도 변경하지 않는다.
- 새 열의 값·`NOT NULL`·양수 CHECK·자기소개서별 UNIQUE 적용 후 기존 문자열 UNIQUE와 `version` 열을 제거한다.

## 실행 전

1. 대상 DB 종류·주소·이름·schema와 기존 `review_versions.version` 존재를 확인한다. `version_number`가 이미 있거나 부분 이관 상태면 이 스크립트를 다시 실행하지 않는다.
2. 앱과 worker 및 DB를 변경하는 도구를 모두 중지한다. 이관·검증이 끝날 때까지 다시 시작하지 않는다.
3. 전체 DB를 백업하고 별도 복제 DB에 복원한다. 복제 DB에 해당 스크립트 전체를 실행해 성공·행 수·아래 참조 보존 항목을 먼저 확인한다.
4. 이관 전 버전별 `(id, cover_letter_id, version, llm_job_id, request_instruction, created_at)`과 최신 성공 포인터, Job 입력·결과 참조, 버전을 참조하는 하위 결과·면접·키워드 분석의 ID를 보관한다. 검증 오류가 나면 원본의 원인부터 확인하며 임의 삭제·재번호 부여로 통과시키지 않는다.

이 저장소 작업·테스트에서는 운영 DB에 접속하거나 SQL을 실행하지 않는다. 운영 반영은 담당자가 대상과 백업·복제 검증 결과를 확인한 별도 배포 절차에서 수행한다.

## PostgreSQL

PostgreSQL 17을 사용하는 기존 `db-postgres` DB에는 [postgresql.sql](postgresql.sql)을 적용한다.
저장소 루트에서 DB 접속 정보가 설정된 libpq service를 사용한 복제 DB 실행 예시는 다음과 같다.
`rewrite-migration-copy`는 실행자가 준비한 복제 DB service 이름으로 바꾼다.

```bash
PGSERVICE=rewrite-migration-copy pg_dump --format=custom --file=/absolute/path/rewrite-before-version-number.dump
PGSERVICE=rewrite-migration-copy psql -X -v ON_ERROR_STOP=1 -f scripts/migrations/review-version-number/postgresql.sql
```

스크립트는 `BEGIN` 후 `review_versions`의 `ACCESS EXCLUSIVE` 잠금을 잡고 검증·DDL·데이터 변환을 한 트랜잭션에서 수행한다.
오류가 나면 `ON_ERROR_STOP`이 실행을 중단하며 연결이 끝날 때 미커밋 변경이 롤백된다. 기존 연결을 직접 사용하는 경우에는 `ROLLBACK` 후 상태를 확인한다.
검증 오류의 원인을 해결한 뒤 원래 schema가 유지됐는지 확인하고 전체 스크립트를 다시 실행한다.
커밋 후 이전 앱으로 되돌려야 하면 이관 전 백업을 별도 DB에 복원·검증하고 해당 DB와 이전 앱을 함께 복구한다.
DDL 잠금과 제약 문법은 [PostgreSQL LOCK](https://www.postgresql.org/docs/17/sql-lock.html)·[ALTER TABLE](https://www.postgresql.org/docs/17/sql-altertable.html)을 따른다.

## 파일 H2

기존 `db-h2` DB에는 [h2.sql](h2.sql)을 적용한다. 프로젝트가 사용하는 H2 2.x JAR와 같은 버전을 사용한다.
앱·H2 Console·다른 JVM의 모든 DB 연결을 종료한 뒤 `data/rewrite.mv.db`를 별도 경로에 복사한다.
복제 파일은 원본과 다른 디렉터리에 둔다. 변경된 앱을 실행해 schema를 먼저 갱신하지 않는다.

저장소 루트에서 복제 파일을 대상으로 실행하는 예시는 다음과 같다.
`H2_JAR`는 프로젝트 실행 의존성의 H2 JAR 절대 경로다. 기본 `sa`/빈 비밀번호가 아닌 DB는 실제 접속 계정을 사용한다.
`IFEXISTS=TRUE`로 경로 오류에 따른 빈 DB 생성을 막는다. 앱을 중지했으므로 `AUTO_SERVER`는 사용하지 않는다.

```bash
H2_JAR=/absolute/path/to/h2.jar
java -cp "$H2_JAR" org.h2.tools.RunScript \
  -url 'jdbc:h2:file:/absolute/path/to/copy/rewrite;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;IFEXISTS=TRUE' \
  -user sa -password '' \
  -script scripts/migrations/review-version-number/h2.sql
```

`-continueOnError`나 GUI의 오류 무시 옵션을 사용하지 않는다. 앞부분의 검증 SELECT는 잘못된 행이 있을 때 `SIGNAL`로 중단하므로 DDL에 도달하지 않는다.
H2의 `ALTER TABLE`은 열린 트랜잭션을 커밋하므로 스크립트 전체를 `ROLLBACK`할 수 없다.
DDL에 도달한 뒤 오류·접속 종료가 발생하면 새 열·제약 일부가 남을 수 있다. 앱을 시작하거나 스크립트를 이어서 실행하지 않는다.
모든 연결을 닫고 이관 전 백업을 새 경로에 복원한 뒤 원래 문자열 열·값·참조를 확인하고 전체 이관을 다시 수행한다.
H2의 트랜잭션 차이와 함수는 [ALTER TABLE](https://h2database.com/html/commands.html#alter_table_add)·[SIGNAL](https://h2database.com/html/functions.html#signal)을 따른다.

## 완료 확인과 앱 시작

1. 이관 전후 버전 수·ID와 기존 참조가 같은지 비교한다. 순번이 기존 `version`의 숫자값과 같고 `v0.`를 붙인 표시가 이전 값과 같은지 확인한다.
2. `version` 열·`uk_review_versions_cover_letter_version` 제약이 제거되고 `version_number`가 `BIGINT NOT NULL`인지 확인한다.
3. `ck_review_versions_version_number_positive`와 `uk_review_versions_cover_letter_version_number`가 존재하는지 확인한다. 잘못된 값·중복 입력의 거부 여부는 복제 DB에서 확인한다.
4. 변경된 앱을 시작해 기존 버전 목록·특정 버전·최신 성공 결과를 조회한다. 정상 데이터의 화면 표시와 최신 성공 포인터가 유지되고, 새 시도는 해당 자기소개서의 최대 순번 + 1인지 확인한다.

## 저장소 검증 범위

`ReviewVersionNumberMigrationTest`는 실제 파일 H2 DB에 `h2.sql`을 실행해 빈 DB·빈 번호·9/10·잘못된 형식·0·음수·중복·overflow의 처리와 ID·참조·제약 보존을 검증한다.
이 테스트는 PostgreSQL의 PL/pgSQL·트랜잭션·잠금 동작을 검증하지 않는다. PostgreSQL 스크립트는 실제 PostgreSQL 복제 DB에서 별도로 실행해야 한다.
Docker·Compose·Testcontainers 또는 운영 DB 접속은 일반 저장소 검증에 사용하지 않는다.
