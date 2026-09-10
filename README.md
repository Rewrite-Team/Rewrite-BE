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

`local`은 파일형 H2를, `local-postgres`는 노트북의 Docker PostgreSQL을 사용하며 둘 다 개발용 고정 사용자로 인증을 대체한다. `prod`는 PostgreSQL과 실제 카카오/JWT 인증을 사용한다. 기존 H2 데이터는 PostgreSQL로 자동 이관되지 않는다. Flyway 도입은 후속 이슈에서 진행한다.

### 실행 프로필

| 지정한 실행 profile | 함께 활성화되는 세부 profile | DB | 인증 | Swagger | H2 Console |
|---|---|---|---|---|---|
| `local` | `db-h2`, `auth-dev` | 파일형 H2 | 개발용 고정 사용자 | 인증 없음 | 활성화, Basic Auth |
| `local-postgres` | `db-postgres`, `auth-dev` | Docker PostgreSQL | 개발용 고정 사용자 | 인증 없음 | 비활성화 |
| `prod` | `db-postgres`, `auth-real`, `internal-tools-secured` | PostgreSQL | 실제 카카오/JWT 인증 | Basic Auth | 비활성화 |
| `test` | `auth-dev` | 인메모리 H2 | 개발용 고정 사용자 | 인증 없음 | 비활성화 |
| `auth-test` | `auth-real`, `internal-tools-secured` | 인메모리 H2 | 실제 인증 통합 테스트 설정 | Basic Auth | 비활성화 |

사용자는 실행 목적을 나타내는 profile 하나만 `SPRING_PROFILES_ACTIVE`로 지정한다. `application.yaml`의 profile group이 DB, 인증과 내부 도구 보호 세부 profile을 조합한다. `test`와 `auth-test`의 인메모리 H2 접속 정보는 각각의 테스트 전용 설정에서 제공한다.

```bash
SPRING_PROFILES_ACTIVE=local             # 파일형 H2 로컬 개발
SPRING_PROFILES_ACTIVE=local-postgres    # Docker PostgreSQL 로컬 개발
SPRING_PROFILES_ACTIVE=prod              # 운영 PostgreSQL과 실제 인증
```

`local-postgres`와 `prod`는 모두 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 필수로 받는다. 로컬에서는 `compose.yaml`에 선언된 접속 정보를 위 예시처럼 명시한다. PostgreSQL 환경 변수는 데이터 디렉터리를 처음 초기화할 때만 계정을 생성하므로, 기존 `rewrite-postgres-data` volume이 과거 계정으로 초기화되어 있다면 실행 환경변수만 바꿔도 계정이 변경되지 않는다.

```bash
docker compose down       # 컨테이너만 내리고 데이터 volume은 보존
docker compose down -v    # 로컬 PostgreSQL 데이터를 삭제하고 완전히 초기화
```

`down -v`는 기존 로컬 데이터를 제거하려는 경우에만 사용한다.

## API 확인

애플리케이션 실행 후 다음 경로에서 구현된 API를 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

`local`, `local-postgres`, `test` profile에서는 별도 인증 없이 접근할 수 있다. 이 profile에서는 실제 인증 controller가 비활성화되므로 API-001~004, API-006을 제외한 24개 API를 확인한다. 인증 API를 포함한 활성 API 29개 전체 문서는 `prod` profile에서 내부 도구용 Basic Auth로 접근하며 다음 환경변수가 필요하다.

```bash
INTERNAL_TOOLS_USERNAME= {username}
INTERNAL_TOOLS_PASSWORD= {password}
```

Basic Auth는 Swagger와 H2 Console 접근만 보호한다. Swagger에서 실제 Rewrite API를 호출할 때는 기존 카카오 로그인으로 발급한 인증 Cookie와, 상태 변경 요청인 경우 CSRF 토큰이 별도로 필요하다.

H2 Console은 `db-h2` profile에서 활성화되며, 내부 도구용 Basic Auth 계정을 사용한다. `local` profile은 profile group을 통해 `db-h2`를 함께 활성화한다. 기본 프로필, `local-postgres`와 운영 환경에서는 비활성화되어 있다.

```bash
SPRING_PROFILES_ACTIVE=local
```

위 설정으로 실행한 뒤 `http://localhost:8080/h2-console`로 접근한다. 운영 환경에서는 `prod` profile을 활성화한다. Swagger UI는 API 번호, 사용 목적, 사용 화면, 호출 시점, 주요 동작과 오류 조건을 확인하는 핵심 API 문서다. 생성된 OpenAPI는 controller, DTO와 `@RewriteApi`를 기준으로 하며 Notion은 보조 동기화 문서로 사용한다.

## 코드 흐름 탐색

저장소 스킬 [code-flow](.agents/skills/code-flow/SKILL.md)로 Java 호출 흐름을 생성할 수 있습니다.

```text
$code-flow KeywordAnalysisController.startKeywordAnalysis 흐름 보여줘
```

AI가 소스를 읽어 호출 데이터를 작성하고, 공통 뷰어가 부모 호출 박스·트랜잭션·분기와 같은 화면에서 펼치는 비동기 흐름을 표시합니다. 결과는 `build/code-flow/`의 독립 HTML, Markdown, 소스 해시 스냅샷입니다. HTML은 서버 없이 열 수 있고 근거 코드 발췌가 포함됩니다. 사용·갱신 절차는 [Codex Workflow](docs/codex-workflow.md#코드-호출-흐름-시각화)를 참고하세요.

## Documentation

- `AGENTS.md`: Codex가 반드시 따라야 하는 저장소 작업 규칙
- `docs/README.md`: 문서 라우팅 가이드
- `docs/status.md`: 기능 진행 상태, 남은 작업, 다음 권장 작업
- `docs/requirements.md`: 제품 요구사항과 화면별 기능
- `docs/api/README.md`: API 상태표와 도메인별 API 문서 라우팅
- `docs/erd.md`: MVP persistence ERD와 테이블 관계
- `docs/decisions/README.md`: API와 persistence 설계 결정 인덱스 및 도메인별 결정 문서 라우팅
- `docs/architecture.md`: 패키지 구조와 계층 책임
- `docs/conventions.md`: Java/Spring/API/예외 처리 규칙
- `docs/testing.md`: 테스트 전략과 검증 명령
- `docs/deployment.md`: OCI 서버·네트워크 구성과 수동 배포·복구 절차
- `docs/codex-workflow.md`: Codex 품질 원칙 적용, 위험도 기반 리뷰, issue/commit/PR 절차와 OpenAPI·Notion 문서 동기화 절차

## Development

작업 전 `docs/README.md`에서 작업 유형별로 필요한 문서를 확인합니다.
Codex를 통한 작업 범위 판단, 변경 영향 확인, 위험도 기반 리뷰와 issue, PR, commit 절차는 `docs/codex-workflow.md`를 따릅니다.
API 계약 또는 구현 상태를 수정할 때는 controller·DTO·`@RewriteApi`, OpenAPI 통합 테스트와 관련 저장소 문서를 함께 갱신합니다. Notion은 생성된 OpenAPI와 저장소 문서를 기준으로 동기화합니다.
GitHub issue 또는 PR 생성 시에는 `.github` 하위 템플릿을 사용합니다.
