# Persistence Decisions

## Decision 072: 남은 자기소개서 CRUD 확장 전 DB/JPA 전환을 선행한다

### 결정

API-008 자기소개서 초안 생성으로 in-memory repository 기반의 기본 service/controller/API 응답 흐름을 확인했다. 이후 DB/JPA 전환 이슈에서 API-008의 공개 계약은 유지하되 내부 persistence 구현을 DB/JPA로 교체하고, 그 다음 남은 자기소개서 CRUD를 확장한다.

DB/JPA 전환 및 후속 구현 대상:

```text
API-008 POST   /cover-letters
API-007 GET    /cover-letters
API-009 PUT    /cover-letters/{coverLetterId}/basic-info
API-010 PUT    /cover-letters/{coverLetterId}/preferences
API-011 PUT    /cover-letters/{coverLetterId}/questions
API-012 GET    /cover-letters/{coverLetterId}
API-013 DELETE /cover-letters/{coverLetterId}
```

초기 DB/JPA 전환 범위는 기존 API-008 공개 계약 유지, API-008 내부 persistence 구현 교체, JPA entity/repository, DB driver, 테스트 가능한 DB 설정, service transaction boundary, repository/transaction 테스트까지로 제한한다.

Flyway는 초기 DB/JPA 전환 범위에 포함하지 않는다. migration versioning은 스키마 변경 이력 관리가 실제로 필요한 시점에 별도 이슈로 검토한다.

DB/JPA 구현 전 MVP persistence 구조는 `docs/erd.md`를 기준으로 확인한다. `docs/erd.md`는 전체 MVP 테이블 관계를 정리하되, 초기 DB/JPA 전환 이슈가 모든 테이블을 한 번에 구현한다는 의미는 아니다.

### 근거

REQ-003의 남은 CRUD는 저장소 구현 세부사항의 영향을 직접 받는다.

- 목록 조회는 pagination, status filter, owner filter, createdAt 정렬, deletedAt 제외가 필요하다.
- 상세 조회는 owner 검증, soft delete 제외, 질문과 최신 Job 요약 조회 정책이 필요하다.
- 삭제는 hard delete가 아니라 soft delete로 처리한다.
- 등록 step 저장은 전체 replace, WRITING 상태 검증, transaction boundary가 필요하다.

API-008의 공개 계약은 이미 확인됐지만 저장소 구현은 아직 in-memory다. API-008 내부 persistence를 DB/JPA로 먼저 교체하지 않은 채 남은 동작들을 in-memory repository 기준으로 확장하면 DB/JPA 전환 시 repository API, 테스트 fixture, pagination/sort/filter 검증을 다시 작성할 가능성이 높다.

### 고려한 대안

1. 남은 CRUD를 in-memory로 모두 구현한 뒤 DB/JPA로 전환
   - API 흐름을 빠르게 붙일 수 있다.
   - 하지만 pagination, filtering, soft delete, transaction 테스트가 실제 저장소 전환 시 중복 작업이 된다.

2. DB/JPA/Flyway를 한 번에 도입
   - migration versioning까지 한 번에 기준을 세울 수 있다.
   - 하지만 아직 스키마 변화 폭이 크고, Flyway 운영 기준이 확정되지 않은 상태에서 migration 파일 관리 비용이 먼저 생긴다.

3. DB/JPA를 먼저 도입하고 Flyway는 후순위로 분리
   - 남은 CRUD를 실제 persistence 모델 위에서 구현할 수 있다.
   - migration versioning은 필요가 명확해지는 시점에 별도 이슈로 결정할 수 있다.

### 선택 이유

DB/JPA 선행 전환은 앞으로 구현할 자기소개서 CRUD의 테스트와 repository 설계를 실제 운영 경계에 더 가깝게 만든다. 특히 owner filter, deletedAt 제외, pagination, transaction boundary는 in-memory collection으로 검증할 때와 JPA query로 검증할 때의 실패 지점이 다르다.

반면 Flyway는 지금 당장 API 동작을 검증하는 데 필수는 아니다. 초기에는 JPA 기반 persistence와 테스트 가능한 DB 구성을 먼저 안정화하고, 스키마 변경 이력이 누적되기 시작하면 Flyway 도입 여부를 별도 이슈에서 결정한다.

### 트레이드오프

- 장점
  - 남은 CRUD 구현이 실제 DB query와 transaction 기준으로 진행된다.
  - in-memory repository 확장 후 JPA로 재작성하는 비용을 줄인다.
  - Flyway 도입을 서두르지 않아 초기 persistence 전환 PR 범위를 작게 유지할 수 있다.

- 단점
  - API feature 개발 전에 persistence 기반 작업이 선행되어야 한다.
  - Flyway가 없으므로 초기 단계의 스키마 변경 이력 관리는 별도 migration 파일로 추적하지 않는다.
  - schema migration 전략은 이후 DB 운영 기준이 구체화될 때 다시 결정해야 한다.

### 후속 작업 순서

1. DB/JPA 전환 문서 계획 PR을 병합한다.
2. DB/JPA 전환 이슈에서 기존 API-008 공개 계약을 유지한 채 내부 persistence 구현을 교체한다.
3. API-007 목록 조회를 DB/JPA repository 기반으로 구현한다.
4. API-012 상세 조회와 API-013 soft delete를 DB/JPA repository 기반으로 구현한다.
5. 등록 step 저장 API는 DB/JPA transaction boundary를 기준으로 구현한다.
6. Flyway는 migration versioning 필요성이 확인되면 별도 이슈로 검토한다.

### 비범위

이번 결정은 API path, request, response, error contract를 변경하지 않는다.

초기 DB/JPA 전환 이슈에서 제외하는 항목:

```text
Flyway 의존성
migration 파일
migration 검증
미래 도메인 전체 스키마 선점
API 계약 변경
```

## Decision 101: 실행 프로필은 DB와 인증 세부 프로필을 조합한다

### 결정

- 사용자가 지정하는 실행 profile과 DB·인증·내부 도구 보안 책임을 가진 세부 profile을 분리한다.
- 실행 profile은 profile group으로 다음 세부 profile을 활성화한다.
  - `local`: `db-h2`, `auth-dev`
  - `local-postgres`: `db-postgres`, `auth-dev`
  - `prod`: `db-postgres`, `auth-real`, `internal-tools-secured`
  - `test`: `auth-dev`; 인메모리 H2 접속 정보는 테스트 전용 설정을 사용한다.
  - `auth-test`: `auth-real`, `internal-tools-secured`; 인메모리 H2 접속 정보는 테스트 전용 설정을 사용한다.
- `db-h2`는 파일형 H2와 H2 Console 설정을 제공하고 `db-postgres`는 PostgreSQL driver, JPA schema 갱신 설정과 환경변수 기반 접속 정보를 제공한다.
- `local-postgres`와 `prod`는 모두 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 필수로 받으며 코드에 접속 정보 기본값을 두지 않는다.
- `local-postgres`는 실제 API 인증을 생략하고 개발용 고정 사용자를 사용하며, `prod`는 실제 카카오/JWT 인증과 Swagger 내부 도구 보호를 사용한다.
- 실제 PostgreSQL의 schema 생성, JSON 저장과 재시작 후 데이터 보존은 `local-postgres` 수동 검증으로 확인하고 일반 Gradle 테스트는 Docker에 의존하지 않는다.
- Flyway와 기존 H2 데이터 이관은 후속 이슈로 분리한다.

### 근거

운영 데이터를 애플리케이션 인스턴스의 로컬 파일과 분리하고, 현재 JPA 관계·transaction·JSON 매핑을 실제 운영 DB와 같은 환경에서 검증하기 위해 PostgreSQL을 선택한다. 로컬에서도 PostgreSQL 동작을 반복해서 확인할 수 있어야 하지만, 그 과정에서 카카오 로그인, JWT Cookie와 CSRF 토큰을 매번 준비하면 개발 피드백이 느려진다.

실행 목적과 구성 책임을 분리하면 사용자는 하나의 실행 profile만 선택하면서도 DB와 인증 조합을 명시적으로 확인할 수 있다. 일반 테스트는 Docker에 의존하지 않고 인메모리 H2를 유지하며, 운영 인증과 내부 도구 보호 동작도 기존과 동일하게 유지한다. 공개 API 계약은 변경하지 않는다.
