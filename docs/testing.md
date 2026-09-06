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
