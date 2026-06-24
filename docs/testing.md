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

## Pull Request CI Report

- `test` job 실패는 기존과 동일하게 PR check를 실패 처리한다.
- Codecov project/patch status는 informational로 사용하며 커버리지 수치만 제공한다.
- 커버리지 감소나 Codecov 업로드 실패만으로 PR 병합을 차단하지 않는다.
- `coverage` job은 JaCoCo XML의 전체 line coverage와 Codecov 업로드 결과를 output으로 제공한다.
- `pr-report` job은 테스트 결과, line coverage, Codecov 업로드 상태와 Codecov PR 상세 링크를 고정 댓글로 표시한다.
- 고정 댓글은 새 workflow 실행마다 기존 `github-actions[bot]` 댓글을 갱신한다.
- PR 코드 실행 job에는 쓰기 권한을 주지 않고, checkout을 수행하지 않는 `pr-report` job에만 `pull-requests: write`를 부여한다.
- 외부 fork PR에서는 쓰기 토큰 제한을 고려해 고정 댓글 작성을 건너뛴다.
