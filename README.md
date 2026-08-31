# Rewrite-BE

Rewrite 서비스의 Java Spring Boot 백엔드 프로젝트입니다.

## Tech Stack

- Java 21
- Spring Boot 4
- Gradle
- Spring WebMVC
- Jakarta Validation
- Spring AI
- JUnit 5

## Quick Start

Run tests:

```bash
./gradlew test
```

Run the application:

```bash
OPENAI_API_KEY=your-api-key ./gradlew bootRun
```

## API 확인

애플리케이션 실행 후 다음 경로에서 구현된 API를 확인할 수 있습니다.

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

`local`, `test` profile에서는 별도 인증 없이 접근할 수 있다. 이 profile에서는 실제 인증 controller가 비활성화되므로 API-001~004, API-006을 제외한 24개 API를 확인한다. 인증 API를 포함한 활성 API 29개 전체 문서는 그 외 profile에서 내부 도구용 Basic Auth로 접근하며 다음 환경변수가 필요하다.

```bash
INTERNAL_TOOLS_USERNAME= {username}
INTERNAL_TOOLS_PASSWORD= {password}
```

Basic Auth는 Swagger와 H2 Console 접근만 보호한다. Swagger에서 실제 Rewrite API를 호출할 때는 기존 카카오 로그인으로 발급한 인증 Cookie와, 상태 변경 요청인 경우 CSRF 토큰이 별도로 필요하다.

H2 Console은 기본 프로필과 운영 환경에서 비활성화되어 있다. 실제 카카오/JWT 인증을 유지하는 `devtools` profile에서만 활성화할 수 있으며, Swagger와 동일한 내부 도구용 Basic Auth 계정을 사용한다.

```bash
SPRING_PROFILES_ACTIVE=devtools
```

위 설정으로 실행한 뒤 `http://localhost:8080/h2-console`로 접근한다. 운영 환경에서는 `devtools` profile을 활성화하지 않는다. Swagger UI는 API 번호, 사용 목적, 사용 화면, 호출 시점, 주요 동작과 오류 조건을 확인하는 핵심 API 문서다. 생성된 OpenAPI는 controller, DTO와 `@RewriteApi`를 기준으로 하며 Notion은 보조 동기화 문서로 사용한다.

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
- `docs/codex-workflow.md`: Codex 품질 원칙 적용, 위험도 기반 리뷰, issue/commit/PR 절차와 OpenAPI·Notion 문서 동기화 절차

## Development

작업 전 `docs/README.md`에서 작업 유형별로 필요한 문서를 확인합니다.
Codex를 통한 작업 범위 판단, 변경 영향 확인, 위험도 기반 리뷰와 issue, PR, commit 절차는 `docs/codex-workflow.md`를 따릅니다.
API 계약 또는 구현 상태를 수정할 때는 controller·DTO·`@RewriteApi`, OpenAPI 통합 테스트와 관련 저장소 문서를 함께 갱신합니다. Notion은 생성된 OpenAPI와 저장소 문서를 기준으로 동기화합니다.
GitHub issue 또는 PR 생성 시에는 `.github` 하위 템플릿을 사용합니다.
