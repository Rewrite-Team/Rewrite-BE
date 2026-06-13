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

## Documentation

- `AGENTS.md`: Codex가 반드시 따라야 하는 저장소 작업 규칙
- `docs/README.md`: 문서 라우팅 가이드
- `docs/status.md`: 기능 진행 상태, 남은 작업, 다음 권장 작업
- `docs/requirements.md`: 제품 요구사항과 화면별 기능
- `docs/api/README.md`: API 상태표와 도메인별 API 문서 라우팅
- `docs/decisions/README.md`: API 설계 결정 인덱스와 도메인별 결정 문서 라우팅
- `docs/architecture.md`: 패키지 구조와 계층 책임
- `docs/conventions.md`: Java/Spring/API/예외 처리 규칙
- `docs/testing.md`: 테스트 전략과 검증 명령
- `docs/codex-workflow.md`: Codex로 이슈, PR, 커밋, 진행 현황을 다루는 절차

## Development

작업 전 `docs/README.md`에서 작업 유형별로 필요한 문서를 확인합니다.
Codex를 통한 issue, PR, commit 절차는 `docs/codex-workflow.md`를 따릅니다.
GitHub issue 또는 PR 생성 시에는 `.github` 하위 템플릿을 사용합니다.
