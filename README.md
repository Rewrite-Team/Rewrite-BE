# Rewrite-BE

Rewrite 서비스의 Java Spring Boot 백엔드 프로젝트입니다.

## Tech Stack

- Java 21
- Spring Boot 4
- Gradle
- Spring WebMVC
- Jakarta Validation
- PostgreSQL
- H2 (local/test)
- Spring AI
- JUnit 5

## Quick Start

Run tests:

```bash
./gradlew test
```

Run the application:

```bash
SPRING_PROFILES_ACTIVE=local OPENAI_API_KEY=your-api-key ./gradlew bootRun
```

Run locally with Docker PostgreSQL and development authentication:

```bash
docker compose up -d --wait postgres
SPRING_PROFILES_ACTIVE=local-postgres \
DB_URL=jdbc:postgresql://127.0.0.1:5432/rewrite \
DB_USERNAME=qwer \
DB_PASSWORD=qwer \
OPENAI_API_KEY=your-api-key \
./gradlew bootRun
```

Run the production profile with PostgreSQL and real authentication:

```bash
SPRING_PROFILES_ACTIVE=prod \
DB_URL=jdbc:postgresql://localhost:5432/rewrite \
DB_USERNAME=rewrite \
DB_PASSWORD=your-password \
OPENAI_API_KEY=your-api-key \
./gradlew bootRun
```

## 실행 프로필

| 지정한 실행 profile | 함께 활성화되는 세부 profile | DB | 인증 | Swagger | H2 Console |
|---|---|---|---|---|---|
| `local` | `db-h2`, `auth-dev` | 파일형 H2 | 개발용 고정 사용자 | 인증 없음 | 활성화, Basic Auth |
| `local-postgres` | `db-postgres`, `auth-dev` | Docker PostgreSQL | 개발용 고정 사용자 | 인증 없음 | 비활성화 |
| `prod` | `db-postgres`, `auth-real` | PostgreSQL | 실제 카카오/JWT 인증 | 인증 없음 | 비활성화 |
| `test` | `auth-dev` | 인메모리 H2 | 개발용 고정 사용자 | 인증 없음 | 비활성화 |
| `auth-test` | `auth-real` | 인메모리 H2 | 실제 인증 통합 테스트 설정 | 인증 없음 | 비활성화 |

- 사용자는 실행 목적을 나타내는 profile 하나만 `SPRING_PROFILES_ACTIVE`로 지정한다.
- `application.yaml`의 profile group은 DB와 인증 세부 profile을 조합한다.
- `test`와 `auth-test`의 인메모리 H2 접속 정보는 각각의 테스트 전용 설정에서 제공한다.
- 현재 사용자 조회는 `auth-dev`의 `DevCurrentUserProvider`, `auth-real`의 `AuthenticatedCurrentUserProvider`를 사용한다.
- `local-postgres`와 `prod`는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 필수로 받는다.

### 기존 첨삭 버전 데이터 전환

기존 `review_versions.version` 문자열을 사용하는 PostgreSQL·파일 H2 DB는 새 코드를 실행하기 전에 [versionNumber 전환 절차](scripts/migrations/review-version-number/README.md)를 적용한다.
`ddl-auto: update`는 기존 데이터의 번호 변환을 수행하지 않는다.

### 로컬 PostgreSQL 데이터

- 기존 H2 데이터는 PostgreSQL로 자동 이관되지 않는다.
- 로컬에서는 `compose.yaml`에 선언된 접속 정보를 위 예시처럼 명시한다.
- PostgreSQL 환경 변수는 데이터 디렉터리를 처음 초기화할 때만 계정을 생성한다.
- 기존 `rewrite-postgres-data` volume이 과거 계정으로 초기화되어 있으면 실행 환경변수만 바꿔도 계정이 변경되지 않는다.

```bash
docker compose down       # 컨테이너만 내리고 데이터 volume은 보존
docker compose down -v    # 로컬 PostgreSQL 데이터를 삭제하고 완전히 초기화
```

`down -v`는 기존 로컬 데이터를 제거하려는 경우에만 사용한다.

### H2 Console

- `db-h2` profile에서 H2 Console을 활성화한다.
- `local` profile은 profile group을 통해 `db-h2`를 함께 활성화한다.
- 기본 프로필·`local-postgres`·운영 환경에서는 H2 Console을 비활성화한다.
- Basic Auth 계정은 `INTERNAL_TOOLS_USERNAME`, `INTERNAL_TOOLS_PASSWORD` 환경변수로 설정한다.
- 값을 지정하지 않으면 로컬 기본값 `0000`을 사용한다.

`local`로 애플리케이션을 실행한 뒤 `http://localhost:8080/h2-console`로 접근한다.

## API 확인

애플리케이션 실행 후 다음 경로에서 구현된 API를 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

모든 profile에서 Swagger UI와 OpenAPI JSON은 별도 인증 없이 조회할 수 있다.

- Swagger UI는 API 번호·사용 목적·화면·호출 시점·주요 동작·오류 조건을 확인하는 핵심 API 문서다.
- 생성 OpenAPI는 controller·DTO·`@RewriteApi`를 기준으로 한다.

| profile | 조회할 수 있는 API 문서 |
|---|---|
| `local`, `local-postgres`, `test` | 실제 인증 controller가 비활성화되어 API-001~004, API-006을 제외한 24개 API |
| `prod`, `auth-test` | 인증 API를 포함한 활성 API 29개 전체 |

- `auth-real`에서 인증이 필요한 Rewrite API를 호출할 때는 카카오 로그인으로 발급한 인증 Cookie가 필요하다.
- `auth-real`의 상태 변경 요청에는 CSRF 토큰도 필요하다.

## 코드 흐름 탐색

저장소 스킬 [code-flow](.agents/skills/code-flow/SKILL.md)로 Java 호출 흐름을 생성할 수 있습니다.

```text
$code-flow KeywordAnalysisController.startKeywordAnalysis 흐름 보여줘
```

- AI가 소스를 읽어 호출 데이터를 작성합니다.
- 공통 뷰어는 부모 호출 박스·트랜잭션·분기와 비동기 흐름을 같은 화면에 표시합니다.
- 결과는 `build/code-flow/`의 독립 HTML·Markdown·소스 해시 스냅샷입니다.
- HTML은 서버 없이 열 수 있고 근거 코드 발췌를 포함합니다.
- 사용·갱신 절차는 [code-flow 스킬](.agents/skills/code-flow/SKILL.md)를 따릅니다.

## Documentation

- [AGENTS.md](AGENTS.md): 문서 위치·핵심 작업 규칙
- [문서 안내](docs/README.md): 기준 문서·API·문서·GitHub 작성 기준

## 테스트와 CI

일반 변경은 `./gradlew test`, 완료 전은 `./gradlew check`를 실행한다.
Docker 사용 제한은 [AGENTS 기본 규칙](AGENTS.md#기본-규칙)을 따른다.

- [Backend CI](.github/workflows/backend-ci.yml)는 `dev` 대상 PR에서 Java 21로 `./gradlew check`를 실행한다.
- 테스트 HTML·JaCoCo HTML/XML은 workflow artifact로 14일 보관하고 Summary에 검사·테스트·line coverage·workflow 링크를 표시한다.
- `PR Report`는 같은 결과를 `github-actions[bot]`의 고정 PR 댓글로 생성·갱신한다.
- `PR Report`만 댓글 쓰기 권한을 가지며 PR 코드를 checkout·실행하지 않고 fork·Dependabot PR은 건너뛴다.
- 같은 PR의 새 실행은 이전 실행을 취소하며 `Backend Check`를 병합 필수 검사로 설정하지 않는다.
- Codecov는 CI 범위에서 제외한다.
- 테스트 후 `jacocoTestReport`를 실행하며 결과는 `build/reports/jacoco/test/html/index.html`과 `build/reports/jacoco/test/jacocoTestReport.xml`에 생성한다.
