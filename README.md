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
- `docs/erd.md`: MVP persistence ERD와 테이블 관계
- `docs/decisions/README.md`: API와 persistence 설계 결정 인덱스 및 도메인별 결정 문서 라우팅
- `docs/architecture.md`: 패키지 구조와 계층 책임
- `docs/conventions.md`: Java/Spring/API/예외 처리 규칙
- `docs/testing.md`: 테스트 전략과 검증 명령
- `docs/codex-workflow.md`: Codex 품질 원칙 적용, 위험도 기반 리뷰, issue/commit/PR 절차와 Notion API 문서 동기화 절차

## Development

작업 전 `docs/README.md`에서 작업 유형별로 필요한 문서를 확인합니다.
Codex를 통한 작업 범위 판단, 변경 영향 확인, 위험도 기반 리뷰와 issue, PR, commit 절차는 `docs/codex-workflow.md`를 따릅니다.
API 계약 또는 구현 상태를 수정할 때는 같은 문서에 정의된 Notion 프론트엔드용 API 문서 동기화 절차를 따릅니다. 저장소의 Markdown 문서와 실제 코드가 기준 원본입니다.
GitHub issue 또는 PR 생성 시에는 `.github` 하위 템플릿을 사용합니다.
