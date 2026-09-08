# Testing

## Test Strategy

변경 범위에 맞는 가장 좁은 테스트부터 작성하고, 완료 전에는 관련 전체 테스트를 실행한다.

## Test Levels

### Unit Tests

- enum, DTO, 순수 모델, service 로직을 검증한다.
- validation metadata나 상태 전이처럼 빠르게 검증 가능한 규칙을 포함한다.

### Web / Controller Tests

- HTTP method, path, request validation, response body, status code를 검증한다.
- service에서 발생한 `BusinessException`이 global handler를 통해 올바르게 응답되는지 확인한다.

### Integration Tests

- DB/JPA 도입 이후 repository와 transaction 동작을 검증한다.
- DB/JPA 전환 전 in-memory repository 단계에서는 DB integration test를 만들지 않는다.
- 실제 인증 컴포넌트의 통합 테스트는 `auth-test` profile에서 인메모리 H2와 테스트용 인증 설정을 사용한다.
- Flyway migration 검증은 Flyway 도입 이슈 이후에 추가한다.

## Required Verification

일반 변경:

```bash
./gradlew test
```

큰 변경 또는 완료 전 검증:

```bash
./gradlew check
```

특정 테스트만 확인할 때:

```bash
./gradlew test --tests '*CoverLetterServiceTest'
```

## Continuous Integration

GitHub Actions는 `dev` 대상 pull request에서 다음 검증을 실행한다.

```bash
./gradlew check
```

- Java 21 환경에서 전체 테스트와 검증 작업을 실행한다.
- 테스트 HTML과 JaCoCo HTML/XML 결과를 workflow artifact로 14일 동안 보관한다.
- Workflow Summary에서 Gradle 검사, 테스트, JaCoCo line coverage와 workflow 링크를 제공한다.
- `PR Report` job은 같은 결과를 `github-actions[bot]`의 고정 PR 댓글로 생성하거나 갱신한다.
- 댓글 쓰기 권한은 `PR Report`에만 부여하며, 이 job은 PR 코드를 checkout하거나 실행하지 않고 외부 fork와 Dependabot PR에서는 건너뛴다.
- 같은 pull request의 새 실행이 시작되면 진행 중인 이전 실행을 취소한다.
- `Backend Check`를 `dev` 브랜치 병합 필수 상태 검사로 사용한다.
- Codecov 연동은 CI 범위에 포함하지 않는다.

## Expectations

- 새 기능에는 테스트를 추가한다.
- 기존 동작 변경에는 관련 테스트를 갱신한다.
- 테스트가 실패한 상태로 완료했다고 말하지 않는다.
- 검증 명령을 실행하지 못한 경우 최종 응답에 이유와 남은 위험을 적는다.

## Coverage

Jacoco가 설정되어 있으며, 테스트 후 `jacocoTestReport`가 실행된다.

주요 산출물:

```text
build/reports/jacoco/test/html/index.html
build/reports/jacoco/test/jacocoTestReport.xml
```

## 코드 흐름 도구 검증

`code-flow` 스킬과 뷰어 변경 시 Python 구조·소스 검증과 실제 브라우저 검증을 실행한다. 명령과 시각 확인 항목은 [스킬 검증 가이드](../.agents/skills/code-flow/references/validation.md)를 따른다. 이 검증은 Java 동작 테스트를 대체하지 않는다.
